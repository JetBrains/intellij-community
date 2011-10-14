package org.jetbrains.jps.client;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.JpsRemoteProto;
import org.jetbrains.jps.api.JpsServerResponseHandler;
import org.jetbrains.jps.api.ProtoUtil;

import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/11/11
 */
abstract class ClientMessageHandler extends SimpleChannelHandler{

  public final void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    final JpsRemoteProto.Message message = (JpsRemoteProto.Message)e.getMessage();
    final UUID sessionId = ProtoUtil.fromProtoUUID(message.getSessionId());
    final JpsRemoteProto.Message.Type messageType = message.getMessageType();

    final JpsServerResponseHandler handler = getHandler(sessionId);
    if (handler == null) {
      terminateSession(sessionId);
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
        switch (responseType) {
          case BUILD_EVENT: {
            terminateSession = handler.handleBuildEvent(response.getBuildEvent());
          }
          break;

          case COMPILE_MESSAGE:
            handler.handleCompileMessage(response.getCompileMessage());
            break;

          default:
            terminateSession = true;
            throw new Exception("Unknown response: " + response);
        }
      }
      else {
        terminateSession = true;
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
