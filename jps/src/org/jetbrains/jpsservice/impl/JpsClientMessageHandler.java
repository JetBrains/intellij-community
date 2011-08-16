package org.jetbrains.jpsservice.impl;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jpsservice.JpsRemoteProto;
import org.jetbrains.jpsservice.JpsServerResponseHandler;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/11/11
 */
public class JpsClientMessageHandler extends SimpleChannelHandler {

  public final void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    final JpsRemoteProto.Message message = (JpsRemoteProto.Message)e.getMessage();
    final JpsServerResponseHandler handler = getHandler(message);
    if (handler == null) {
      // discard the message
      // todo: log discarded message
      return;
    }
    if (message.getKind() == JpsRemoteProto.Message.Kind.FAILURE) {
      handler.handleFailure(message.getFailure());
    }
    else if (message.getKind() == JpsRemoteProto.Message.Kind.RESPONSE) {
      final JpsRemoteProto.Message.Response response = message.getResponse();
      if (response.hasCompileResponse()) {
        handler.handleCompileResponse(response.getCompileResponse());
      }
      else if (response.hasStatusResponse()) {
        handler.handleStatusResponse(response.getStatusResponse());
      }
      else {
        throw new Exception("Unknown response: " + response);
      }
    }
    else {
      throw new Exception("Unknown message received: " + message);
    }
  }

  @Nullable
  protected JpsServerResponseHandler getHandler(JpsRemoteProto.Message message) {
    return null;
  }

  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    super.exceptionCaught(ctx, e);
  }

}
