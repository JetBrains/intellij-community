/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ConcurrentHashSet;
import org.jboss.netty.channel.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
* @author Eugene Zhuravlev
*         Date: 4/25/12
*/
class BuildMessageDispatcher extends SimpleChannelHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.BuildMessageDispatcher");
  private final Map<UUID, SessionData> myMessageHandlers = new ConcurrentHashMap<UUID, SessionData>();
  private final Set<UUID> myCanceledSessions = new ConcurrentHashSet<UUID>();

  public void registerBuildMessageHandler(UUID sessionId,
                                          BuilderMessageHandler handler,
                                          CmdlineRemoteProto.Message.ControllerMessage params) {
    myMessageHandlers.put(sessionId, new SessionData(sessionId, handler, params));
  }

  @Nullable
  public BuilderMessageHandler unregisterBuildMessageHandler(UUID sessionId) {
    myCanceledSessions.remove(sessionId);
    final SessionData data = myMessageHandlers.remove(sessionId);
    return data != null? data.handler : null;
  }

  public void cancelSession(UUID sessionId) {
    if (myCanceledSessions.add(sessionId)) {
      final Channel channel = getConnectedChannel(sessionId);
      if (channel != null) {
        Channels.write(channel, CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCancelCommand()));
      }
    }
  }

  @Nullable
  public Channel getConnectedChannel(final UUID sessionId) {
    final Channel channel = getAssociatedChannel(sessionId);
    return channel != null && channel.isConnected()? channel : null;
  }

  @Nullable
  public Channel getAssociatedChannel(final UUID sessionId) {
    final SessionData data = myMessageHandlers.get(sessionId);
    return data != null? data.channel : null;
  }


  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    final CmdlineRemoteProto.Message message = (CmdlineRemoteProto.Message)e.getMessage();

    SessionData sessionData = (SessionData)ctx.getAttachment();

    UUID sessionId;
    if (sessionData == null) {
      // this is the first message for this session, so fill session data with missing info
      final CmdlineRemoteProto.Message.UUID id = message.getSessionId();
      sessionId = new UUID(id.getMostSigBits(), id.getLeastSigBits());

      sessionData = myMessageHandlers.get(sessionId);
      if (sessionData != null) {
        sessionData.channel = ctx.getChannel();
        ctx.setAttachment(sessionData);
      }
      if (myCanceledSessions.contains(sessionId)) {
        Channels.write(ctx.getChannel(), CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCancelCommand()));
      }
    }
    else {
      sessionId = sessionData.sessionId;
    }

    final BuilderMessageHandler handler = sessionData != null? sessionData.handler : null;
    if (handler == null) {
      // todo
      LOG.info("No message handler registered for session " + sessionId);
      return;
    }

    final CmdlineRemoteProto.Message.Type messageType = message.getType();
    switch (messageType) {
      case FAILURE:
        handler.handleFailure(sessionId, message.getFailure());
        break;

      case BUILDER_MESSAGE:
        final CmdlineRemoteProto.Message.BuilderMessage builderMessage = message.getBuilderMessage();
        final CmdlineRemoteProto.Message.BuilderMessage.Type msgType = builderMessage.getType();
        if (msgType == CmdlineRemoteProto.Message.BuilderMessage.Type.PARAM_REQUEST) {
          final CmdlineRemoteProto.Message.ControllerMessage params = sessionData.params;
          if (params != null) {
            handler.buildStarted(sessionId);
            sessionData.params = null;
            Channels.write(ctx.getChannel(), CmdlineProtoUtil.toMessage(sessionId, params));
          }
          else {
            cancelSession(sessionId);
          }
        }
        else {
          handler.handleBuildMessage(ctx.getChannel(), sessionId, builderMessage);
        }
        break;

      default:
        LOG.info("Unsupported message type " + messageType);
        break;
    }
  }

  @Override
  public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    try {
      super.channelClosed(ctx, e);
    }
    finally {
      final SessionData sessionData = (SessionData)ctx.getAttachment();
      if (sessionData != null) {
        final BuilderMessageHandler handler = unregisterBuildMessageHandler(sessionData.sessionId);
        if (handler != null) {
          // notify the handler only if it has not been notified yet
          handler.sessionTerminated(sessionData.sessionId);
        }
      }
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    final Throwable cause = e.getCause();
    if (cause != null) {
      LOG.info(cause);
    }
    ctx.sendUpstream(e);
  }

  @Override
  public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    try {
      super.channelDisconnected(ctx, e);
    }
    finally {
      final SessionData sessionData = (SessionData)ctx.getAttachment();
      if (sessionData != null) { // this is the context corresponding to some session
        final Channel channel = e.getChannel();
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            channel.close();
          }
        });
      }
    }
  }

  private static final class SessionData {
    final UUID sessionId;
    final BuilderMessageHandler handler;
    volatile CmdlineRemoteProto.Message.ControllerMessage params;
    volatile Channel channel;

    private SessionData(UUID sessionId, BuilderMessageHandler handler, CmdlineRemoteProto.Message.ControllerMessage params) {
      this.sessionId = sessionId;
      this.handler = handler;
      this.params = params;
    }
  }
}
