/** 
 * Copyright 2018-2028 Akaxin Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package com.akaxin.site.business.impl.tai;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akaxin.common.command.Command;
import com.akaxin.common.command.CommandResponse;
import com.akaxin.common.constant.CommandConst;
import com.akaxin.common.constant.ErrorCode2;
import com.akaxin.common.logs.LogUtils;
import com.akaxin.proto.core.UserProto;
import com.akaxin.proto.site.ApiFriendApplyCountProto;
import com.akaxin.proto.site.ApiFriendApplyListProto;
import com.akaxin.proto.site.ApiFriendApplyProto;
import com.akaxin.proto.site.ApiFriendApplyResultProto;
import com.akaxin.proto.site.ApiFriendDeleteProto;
import com.akaxin.proto.site.ApiFriendListProto;
import com.akaxin.proto.site.ApiFriendMuteProto;
import com.akaxin.proto.site.ApiFriendProfileProto;
import com.akaxin.proto.site.ApiFriendSettingProto;
import com.akaxin.proto.site.ApiFriendUpdateMuteProto;
import com.akaxin.proto.site.ApiFriendUpdateSettingProto;
import com.akaxin.site.business.dao.UserFriendDao;
import com.akaxin.site.business.dao.UserProfileDao;
import com.akaxin.site.business.impl.AbstractRequest;
import com.akaxin.site.business.impl.notice.User2Notice;
import com.akaxin.site.business.push.PushNotification;
import com.akaxin.site.storage.bean.ApplyFriendBean;
import com.akaxin.site.storage.bean.ApplyUserBean;
import com.akaxin.site.storage.bean.SimpleUserBean;
import com.akaxin.site.storage.bean.UserFriendBean;
import com.akaxin.site.storage.bean.UserProfileBean;

/**
 * <pre>
 * 	用户好友相关功能处理
 * 		1.好友profile资料
 * 		2.好友列表
 * 		3.申请好友
 * 		4.好友申请列表
 * 		5.好友申请总数
 * 		6.好友申请结果	（是否同意）
 * 		7.删除好友
 * 		8.好友静音状态	
 * 		9.更新好友静音
 * </pre>
 * 
 * @author Sam{@link an.guoyue254@gmail.com}
 * @since 2017.11.24 18:36:59
 */
public class ApiFriendService extends AbstractRequest {
	private static final Logger logger = LoggerFactory.getLogger(ApiFriendService.class);

	/**
	 * 查询好友的资料信息，点击好友头像查看使用
	 * 
	 * @param command
	 * @return
	 */
	public CommandResponse profile(Command command) {
		CommandResponse commandResponse = new CommandResponse().setAction(CommandConst.ACTION_RES);
		ErrorCode2 errCode = ErrorCode2.ERROR;
		try {
			ApiFriendProfileProto.ApiFriendProfileRequest request = ApiFriendProfileProto.ApiFriendProfileRequest
					.parseFrom(command.getParams());
			String siteUserId = command.getSiteUserId();
			String globalOrSiteFriendId = request.getSiteUserId();// 等待查询的站点用户ID || globalUserId
			String userIdPubk = request.getUserIdPubk(); // 等待查询的用户公钥，优先级高
			LogUtils.requestDebugLog(logger, command, request.toString());

			if (StringUtils.isAllEmpty(userIdPubk, globalOrSiteFriendId)) {
				errCode = ErrorCode2.ERROR_PARAMETER;
				return commandResponse.setErrCode2(errCode);
			}

			UserProfileBean userBean = UserProfileDao.getInstance().getUserProfileById(globalOrSiteFriendId);
			if (null == userBean || StringUtils.isBlank(userBean.getSiteUserId())) {
				// 直接复用之前的接口了。
				userBean = UserProfileDao.getInstance().getUserProfileByGlobalUserId(globalOrSiteFriendId);
			}

			if (userBean != null && StringUtils.isNotBlank(userBean.getSiteUserId())) {
				UserProto.UserProfile userProfileProto = UserProto.UserProfile.newBuilder()
						.setSiteUserId(String.valueOf(userBean.getSiteUserId()))
						.setUserName(String.valueOf(userBean.getUserName()))
						.setUserPhoto(String.valueOf(userBean.getUserPhoto()))
						.setUserStatusValue(userBean.getUserStatus()).build();
				UserProto.UserRelation userRelation = UserFriendDao.getInstance().getUserRelation(siteUserId,
						userBean.getSiteUserId());
				ApiFriendProfileProto.ApiFriendProfileResponse response = ApiFriendProfileProto.ApiFriendProfileResponse
						.newBuilder().setProfile(userProfileProto).setRelation(userRelation)
						.setUserIdPubk(userBean.getUserIdPubk()).build();
				commandResponse.setParams(response.toByteArray());
				errCode = ErrorCode2.SUCCESS;
			}
		} catch (Exception e) {
			errCode = ErrorCode2.ERROR_SYSTEMERROR;
			LogUtils.requestErrorLog(logger, command, e);
		}
		return commandResponse.setErrCode2(errCode);
	}

	/**
	 * 获取个人的好友列表，通讯录中使用
	 * 
	 * @param command
	 * @return
	 */
	public CommandResponse list(Command command) {
		CommandResponse commandResponse = new CommandResponse().setAction(CommandConst.ACTION_RES);
		ErrorCode2 errCode = ErrorCode2.ERROR;
		try {
			ApiFriendListProto.ApiFriendListRequest request = ApiFriendListProto.ApiFriendListRequest
					.parseFrom(command.getParams());
			String currentUserId = command.getSiteUserId();
			String siteUserId = request.getSiteUserId();
			LogUtils.requestDebugLog(logger, command, request.toString());

			if (StringUtils.isNotBlank(siteUserId) && siteUserId.equals(currentUserId)) {
				List<SimpleUserBean> friendBeanList = UserFriendDao.getInstance().getUserFriends(siteUserId);

				ApiFriendListProto.ApiFriendListResponse.Builder responseBuilder = ApiFriendListProto.ApiFriendListResponse
						.newBuilder();
				for (SimpleUserBean friendBean : friendBeanList) {
					UserProto.SimpleUserProfile.Builder friendBuilder = UserProto.SimpleUserProfile.newBuilder();
					friendBuilder.setSiteUserId(friendBean.getUserId());
					if (StringUtils.isNotEmpty(friendBean.getUserName())) {
						friendBuilder.setUserName(String.valueOf(friendBean.getUserName()));
					}
					if (StringUtils.isNotEmpty(friendBean.getUserPhoto())) {
						friendBuilder.setUserPhoto(String.valueOf(friendBean.getUserPhoto()));
					}
					responseBuilder.addList(friendBuilder.build());
				}
				commandResponse.setParams(responseBuilder.build().toByteArray());
				errCode = ErrorCode2.SUCCESS;
			} else {
				errCode = ErrorCode2.ERROR_PARAMETER;
			}
		} catch (Exception e) {
			errCode = ErrorCode2.ERROR_SYSTEMERROR;
			LogUtils.requestErrorLog(logger, command, e);
		}
		return commandResponse.setErrCode2(errCode);
	}

	/**
	 * 用户好友添加申请
	 * 
	 * @param command
	 * @return
	 */
	public CommandResponse apply(Command command) {
		CommandResponse commandResponse = new CommandResponse().setAction(CommandConst.ACTION_RES);
		ErrorCode2 errCode = ErrorCode2.ERROR;
		int applyTimes = 0;
		try {
			ApiFriendApplyProto.ApiFriendApplyRequest request = ApiFriendApplyProto.ApiFriendApplyRequest
					.parseFrom(command.getParams());
			String siteUserId = command.getSiteUserId();
			String siteFriendId = request.getSiteFriendId();
			String applyReason = request.getApplyReason();
			LogUtils.requestDebugLog(logger, command, request.toString());

			if (StringUtils.isEmpty(siteUserId)) {
				errCode = ErrorCode2.ERROR_PARAMETER;
			} else if (siteUserId.equals(siteFriendId)) {
				errCode = ErrorCode2.ERROR2_FRIEND_APPLYSELF;
			} else if (StringUtils.isNotBlank(siteUserId) && !siteUserId.equals(siteFriendId)) {
				UserProto.UserRelation userRelation = UserFriendDao.getInstance().getUserRelation(siteUserId,
						siteFriendId);
				if (UserProto.UserRelation.RELATION_FRIEND == userRelation) {
					errCode = ErrorCode2.ERROR2_FRIEND_IS;
				} else {
					applyTimes = UserFriendDao.getInstance().getApplyCount(siteFriendId, siteUserId);
					if (applyTimes >= 5) {
						errCode = ErrorCode2.ERROR2_FRIEND_APPLYCOUNT;
					} else {
						if (UserFriendDao.getInstance().saveFriendApply(siteUserId, siteFriendId, applyReason)) {
							errCode = ErrorCode2.SUCCESS;
						}
					}
				}
			}

			if (ErrorCode2.SUCCESS.equals(errCode)) {
				if (applyTimes == 0) {
					new User2Notice().applyFriendNotice(siteUserId, siteFriendId);
				}
				// 同时下发一条PUSH消息
				PushNotification.sendAddFriend(siteUserId, siteFriendId);
			}

		} catch (Exception e) {
			errCode = ErrorCode2.ERROR_SYSTEMERROR;
			LogUtils.requestErrorLog(logger, command, e);
		}
		return commandResponse.setErrCode2(errCode);
	}

	/**
	 * 获取用户的好友申请列表
	 * 
	 * @param command
	 * @return
	 */
	public CommandResponse applyList(Command command) {
		CommandResponse commandResponse = new CommandResponse().setAction(CommandConst.ACTION_RES);
		ErrorCode2 errCode = ErrorCode2.ERROR;
		try {
			String siteUserId = command.getSiteUserId();
			LogUtils.requestDebugLog(logger, command, "");

			if (StringUtils.isNotBlank(siteUserId)) {
				List<ApplyUserBean> applyUserList = UserFriendDao.getInstance().getApplyUserList(siteUserId);
				ApiFriendApplyListProto.ApiFriendApplyListResponse.Builder responseBuilder = ApiFriendApplyListProto.ApiFriendApplyListResponse
						.newBuilder();
				for (ApplyUserBean applyUser : applyUserList) {
					if (StringUtils.isNotEmpty(applyUser.getUserId())) {
						UserProto.UserProfile.Builder userProfileBuilder = UserProto.UserProfile.newBuilder();
						userProfileBuilder.setSiteUserId(applyUser.getUserId());
						userProfileBuilder.setUserName(String.valueOf(applyUser.getUserName()));
						userProfileBuilder.setUserPhoto(String.valueOf(applyUser.getUserPhoto()));
						UserProto.ApplyUserProfile applyUserProfile = UserProto.ApplyUserProfile.newBuilder()
								.setApplyUser(userProfileBuilder.build())
								.setApplyReason(String.valueOf(applyUser.getApplyReason())).build();
						responseBuilder.addList(applyUserProfile);
					}
				}
				commandResponse.setParams(responseBuilder.build().toByteArray());
				errCode = ErrorCode2.SUCCESS;
			}
		} catch (Exception e) {
			errCode = ErrorCode2.ERROR_SYSTEMERROR;
			LogUtils.requestErrorLog(logger, command, e);
		}
		return commandResponse.setErrCode2(errCode);
	}

	/**
	 * 获取申请用户为好友的申请人数
	 * 
	 * @param command
	 * @return
	 */
	public CommandResponse applyCount(Command command) {
		CommandResponse commandResponse = new CommandResponse().setAction(CommandConst.ACTION_RES);
		ErrorCode2 errCode = ErrorCode2.ERROR;
		try {
			String siteUserId = command.getSiteUserId();
			LogUtils.requestDebugLog(logger, command, "");

			if (StringUtils.isNotBlank(siteUserId)) {
				int applyCount = UserFriendDao.getInstance().getApplyCount(siteUserId);
				ApiFriendApplyCountProto.ApiFriendApplyCountResponse response = ApiFriendApplyCountProto.ApiFriendApplyCountResponse
						.newBuilder().setApplyCount(applyCount).build();
				commandResponse.setParams(response.toByteArray());
				errCode = ErrorCode2.SUCCESS;
			}
		} catch (Exception e) {
			errCode = ErrorCode2.ERROR_SYSTEMERROR;
			LogUtils.requestErrorLog(logger, command, e);
		}
		return commandResponse.setErrCode2(errCode);
	}

	/**
	 * 是否同意用户的好友申请处理
	 * 
	 * @param command
	 * @return
	 */
	public CommandResponse applyResult(Command command) {
		CommandResponse commandResponse = new CommandResponse().setAction(CommandConst.ACTION_RES);
		ErrorCode2 errCode = ErrorCode2.ERROR;
		try {
			ApiFriendApplyResultProto.ApiFriendApplyResultRequest request = ApiFriendApplyResultProto.ApiFriendApplyResultRequest
					.parseFrom(command.getParams());
			String siteUserId = command.getSiteUserId();
			String siteFriendId = request.getSiteFriendId();
			boolean result = request.getApplyResult();
			LogUtils.requestDebugLog(logger, command, request.toString());

			if (StringUtils.isNotBlank(siteUserId) && StringUtils.isNotBlank(siteFriendId)
					&& !siteUserId.equals(siteFriendId)) {
				if (UserFriendDao.getInstance().agreeApply(siteUserId, siteFriendId, result)) {
					errCode = ErrorCode2.SUCCESS;
				}

				if (ErrorCode2.SUCCESS.equals(errCode) && result) {
					ApplyFriendBean applyBean = UserFriendDao.getInstance().agreeApplyWithClear(siteUserId,
							siteFriendId);
					// xxx 同意了你的好友申请 ,发送push
					PushNotification.agreeAddFriend(siteUserId, siteFriendId);

					// 发送文本消息
					if (applyBean != null && StringUtils.isNotEmpty(applyBean.getSiteUserId())) {
						new User2Notice().addFriendTextMessage(applyBean);
					}
					logger.debug("client={} siteUserId={} add friend notice to siteUserId={}", command.getClientIp(),
							siteFriendId);
				}
			} else {
				errCode = ErrorCode2.ERROR_PARAMETER;
			}
		} catch (Exception e) {
			errCode = ErrorCode2.ERROR_SYSTEMERROR;
			LogUtils.requestErrorLog(logger, command, e);
		}
		return commandResponse.setErrCode2(errCode);
	}

	/**
	 * 删除好友
	 * 
	 * @param command
	 * @return
	 */
	public CommandResponse delete(Command command) {
		CommandResponse commandResponse = new CommandResponse().setAction(CommandConst.ACTION_RES);
		ErrorCode2 errCode = ErrorCode2.ERROR;
		try {
			ApiFriendDeleteProto.ApiFriendDeleteRequest request = ApiFriendDeleteProto.ApiFriendDeleteRequest
					.parseFrom(command.getParams());
			String siteUserId = command.getSiteUserId();
			String siteFriendId = request.getSiteFriendId();
			LogUtils.requestDebugLog(logger, command, request.toString());

			if (StringUtils.isNotBlank(siteUserId) && StringUtils.isNotBlank(siteFriendId)
					&& !siteUserId.equals(siteFriendId)) {
				if (UserFriendDao.getInstance().deleteFriend(siteUserId, siteFriendId)) {
					errCode = ErrorCode2.SUCCESS;
				}
			} else {
				errCode = ErrorCode2.ERROR_PARAMETER;
			}
		} catch (Exception e) {
			errCode = ErrorCode2.ERROR_SYSTEMERROR;
			LogUtils.requestErrorLog(logger, command, e);
		}
		return commandResponse.setErrCode2(errCode);
	}

	/**
	 * 获取好友的设置信息
	 * 
	 * @param command
	 * @return
	 */
	public CommandResponse setting(Command command) {
		CommandResponse commandResponse = new CommandResponse().setAction(CommandConst.ACTION_RES);
		ErrorCode2 errCode = ErrorCode2.ERROR;
		try {
			ApiFriendSettingProto.ApiFriendSettingRequest request = ApiFriendSettingProto.ApiFriendSettingRequest
					.parseFrom(command.getParams());
			String siteUserId = command.getSiteUserId();
			String siteFriendId = request.getSiteFriendId();
			LogUtils.requestDebugLog(logger, command, request.toString());

			if (StringUtils.isNoneEmpty(siteUserId, siteFriendId)) {
				UserFriendBean bean = UserFriendDao.getInstance().getFriendSetting(siteUserId, siteFriendId);
				if (bean != null) {
					ApiFriendSettingProto.ApiFriendSettingResponse response = ApiFriendSettingProto.ApiFriendSettingResponse
							.newBuilder().setMessageMute(bean.isMute()).build();
					commandResponse.setParams(response.toByteArray());
					errCode = ErrorCode2.SUCCESS;
				} else {
					errCode = ErrorCode2.ERROR_DATABASE_EXECUTE;// 数据库执行错误
				}

			} else {
				errCode = ErrorCode2.ERROR_PARAMETER;
			}

		} catch (Exception e) {
			errCode = ErrorCode2.ERROR_SYSTEMERROR;
			LogUtils.requestErrorLog(logger, command, e);
		}
		return commandResponse.setErrCode2(errCode);
	}

	/**
	 * 对用户设置消息免打扰功能
	 * 
	 * @param command
	 * @return
	 */
	public CommandResponse updateSetting(Command command) {
		CommandResponse commandResponse = new CommandResponse().setAction(CommandConst.ACTION_RES);
		ErrorCode2 errCode = ErrorCode2.ERROR;
		try {
			ApiFriendUpdateSettingProto.ApiFriendUpdateSettingRequest request = ApiFriendUpdateSettingProto.ApiFriendUpdateSettingRequest
					.parseFrom(command.getParams());
			String siteUserId = command.getSiteUserId();
			String siteFriendId = request.getSiteFriendId();
			boolean messageMute = request.getMessageMute();
			LogUtils.requestDebugLog(logger, command, request.toString());

			if (StringUtils.isNoneBlank(siteUserId, siteFriendId)) {
				UserFriendBean friendBean = new UserFriendBean();
				friendBean.setSiteUserId(siteFriendId);
				friendBean.setMute(messageMute);
				if (UserFriendDao.getInstance().updateFriendSetting(siteUserId, friendBean)) {
					errCode = ErrorCode2.SUCCESS;
				} else {
					errCode = ErrorCode2.ERROR_DATABASE_EXECUTE;
				}
			} else {
				errCode = ErrorCode2.ERROR_PARAMETER;
			}

		} catch (Exception e) {
			errCode = ErrorCode2.ERROR_SYSTEMERROR;
			LogUtils.requestErrorLog(logger, command, e);
		}
		return commandResponse.setErrCode2(errCode);
	}

	/**
	 * 获取用户对好友的静音（消息免打扰）状态
	 * 
	 * @param command
	 * @return
	 */
	public CommandResponse mute(Command command) {
		CommandResponse commandResponse = new CommandResponse().setAction(CommandConst.ACTION_RES);
		ErrorCode2 errCode = ErrorCode2.ERROR;
		try {
			ApiFriendMuteProto.ApiFriendMuteRequest request = ApiFriendMuteProto.ApiFriendMuteRequest
					.parseFrom(command.getParams());
			String siteUserId = command.getSiteUserId();
			String siteFriendId = request.getSiteFriendId();
			LogUtils.requestDebugLog(logger, command, request.toString());

			if (StringUtils.isNoneEmpty(siteUserId, siteFriendId)) {
				boolean mute = UserFriendDao.getInstance().getFriendMute(siteUserId, siteFriendId);
				ApiFriendSettingProto.ApiFriendSettingResponse response = ApiFriendSettingProto.ApiFriendSettingResponse
						.newBuilder().setMessageMute(mute).build();
				commandResponse.setParams(response.toByteArray());
				errCode = ErrorCode2.SUCCESS;
			} else {
				errCode = ErrorCode2.ERROR_PARAMETER;
			}
		} catch (Exception e) {
			errCode = ErrorCode2.ERROR_SYSTEMERROR;
			LogUtils.requestErrorLog(logger, command, e);
		}
		return commandResponse.setErrCode2(errCode);
	}

	/**
	 * 对好友设置静音（消息免打扰）状态
	 * 
	 * @param command
	 * @return
	 */
	public CommandResponse updateMute(Command command) {
		CommandResponse commandResponse = new CommandResponse().setAction(CommandConst.ACTION_RES);
		ErrorCode2 errCode = ErrorCode2.ERROR;
		try {
			ApiFriendUpdateMuteProto.ApiFriendUpdateMuteRequest request = ApiFriendUpdateMuteProto.ApiFriendUpdateMuteRequest
					.parseFrom(command.getParams());
			String siteUserId = command.getSiteUserId();
			String siteFriendId = request.getSiteFriendId();
			boolean messageMute = request.getMute();
			LogUtils.requestDebugLog(logger, command, request.toString());

			if (StringUtils.isNoneBlank(siteUserId, siteFriendId)) {
				if (UserFriendDao.getInstance().updateFriendMute(siteUserId, siteFriendId, messageMute)) {
					errCode = ErrorCode2.SUCCESS;
				} else {
					errCode = ErrorCode2.ERROR_DATABASE_EXECUTE;
				}
			} else {
				errCode = ErrorCode2.ERROR_PARAMETER;
			}

		} catch (Exception e) {
			errCode = ErrorCode2.ERROR_SYSTEMERROR;
			LogUtils.requestErrorLog(logger, command, e);
		}
		return commandResponse.setErrCode2(errCode);
	}

}