package org.jetbrains.jpsservice.impl;

import org.jboss.netty.channel.*;
import org.jetbrains.jpsservice.JpsRemoteProto;

import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/11/11
 */
public class JpsServerMessageHandler extends SimpleChannelHandler {

  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    final JpsRemoteProto.Message message = (JpsRemoteProto.Message)e.getMessage();
    final JpsRemoteProto.Message.Builder responseBuilder = JpsRemoteProto.Message.newBuilder();

    responseBuilder.setMessageUuid(message.getMessageUuid());

    if (message.getKind() != JpsRemoteProto.Message.Kind.REQUEST) {
      responseBuilder.setKind(JpsRemoteProto.Message.Kind.FAILURE).setFailure(JpsProtoUtil.createFailure("Cannot handle message " + message.toString()));
    }
    else if (!message.hasRequest()) {
      responseBuilder.setKind(JpsRemoteProto.Message.Kind.FAILURE).setFailure(JpsProtoUtil.createFailure("No request in message: " + message.toString()));
    }
    else {
      final JpsRemoteProto.Message.Request request = message.getRequest();
      if (request.hasCompileRequest()) {
        responseBuilder.setKind(JpsRemoteProto.Message.Kind.RESPONSE).setResponse(startBuild(request.getCompileRequest()));
      }
      else if (request.hasStatusRequest()){
        responseBuilder.setKind(JpsRemoteProto.Message.Kind.RESPONSE).setResponse(getBuildStatus(request.getStatusRequest()));
      }
      else {
        responseBuilder.setKind(JpsRemoteProto.Message.Kind.FAILURE).setFailure(JpsProtoUtil.createFailure("Unknown request: " + message));
      }
    }
    Channels.write(ctx, e.getFuture(), responseBuilder.build());
  }


  protected JpsRemoteProto.Message.Response startBuild(JpsRemoteProto.Message.Request.CompileRequest compileRequest) {
    //todo: start requested build
    UUID buildUUID = UUID.randomUUID();
    return JpsProtoUtil.createCompileResponse(JpsProtoUtil.createProtoUUID(buildUUID));
  }

  protected JpsRemoteProto.Message.Response getBuildStatus(JpsRemoteProto.Message.Request.StatusRequest statusRequest) {
    // todo: collect status data
    return JpsProtoUtil.createStatusResponse(statusRequest.getSessionId());
  }

  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    super.exceptionCaught(ctx, e);
  }

}
