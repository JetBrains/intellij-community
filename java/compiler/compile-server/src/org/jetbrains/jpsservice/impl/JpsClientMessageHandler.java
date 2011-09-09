package org.jetbrains.jpsservice.impl;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jpsservice.JpsRemoteProto;
import org.jetbrains.jpsservice.JpsServerResponseHandler;

import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/11/11
 */
public abstract class JpsClientMessageHandler extends SimpleChannelHandler{

  public final void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    final JpsRemoteProto.Message message = (JpsRemoteProto.Message)e.getMessage();
    final UUID sessionId = ProtoUtil.fromProtoUUID(message.getSessionId());
    final JpsRemoteProto.Message.Type messageType = message.getMessageType();

    final JpsServerResponseHandler handler = getHandler(sessionId);
    if (handler == null) {
      return; // discard message
    }

    boolean terminateSession = false;

    try {
      if (messageType == JpsRemoteProto.Message.Type.FAILURE) {
        terminateSession = true;
        handler.handleFailure(message.getFailure());
      }
      else if (messageType == JpsRemoteProto.Message.Type.RESPONSE) {
        final JpsRemoteProto.Message.Response response = message.getResponse();
        final JpsRemoteProto.Message.Response.Type responseType = response.getResponseType();
        if (responseType == JpsRemoteProto.Message.Response.Type.COMMAND_RESPONSE) {
          final JpsRemoteProto.Message.Response.CommandResponse commandResponse = response.getCommandResponse();
          terminateSession = commandResponse.getCommandType() != JpsRemoteProto.Message.Response.CommandResponse.Type.COMMAND_ACCEPTED;
          handler.handleCommandResponse(commandResponse);
        }
        else if (responseType == JpsRemoteProto.Message.Response.Type.COMPILE_MESSAGE) {
          handler.handleCompileMessage(response.getCompileMessage());
        }
        else {
          throw new Exception("Unknown response: " + response);
        }
      }
      else {
        throw new Exception("Unknown message received: " + message);
      }
    }
    finally {
      if (terminateSession) {
        terminateSession(sessionId);
      }
    }
  }

  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    super.exceptionCaught(ctx, e);
  }

  @Nullable
  protected abstract JpsServerResponseHandler getHandler(UUID sessionId);

  protected abstract void terminateSession(UUID sessionId);
}
