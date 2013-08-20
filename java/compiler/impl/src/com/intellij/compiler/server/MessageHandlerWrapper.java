package com.intellij.compiler.server;

import io.netty.channel.Channel;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.UUID;

/**
* @author Eugene Zhuravlev
*         Date: 7/3/13
*/
class MessageHandlerWrapper implements BuilderMessageHandler {
  private final BuilderMessageHandler myHandler;

  public MessageHandlerWrapper(BuilderMessageHandler handler) {
    myHandler = handler;
  }

  @Override
  public void buildStarted(UUID sessionId) {
    myHandler.buildStarted(sessionId);
  }

  @Override
  public void handleBuildMessage(Channel channel, UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage msg) {
    myHandler.handleBuildMessage(channel, sessionId, msg);
  }

  @Override
  public void handleFailure(UUID sessionId, CmdlineRemoteProto.Message.Failure failure) {
    myHandler.handleFailure(sessionId, failure);
  }

  @Override
  public void sessionTerminated(UUID sessionId) {
    myHandler.sessionTerminated(sessionId);
  }
}
