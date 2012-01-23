package org.jetbrains.jps.javac;

import org.jboss.netty.channel.MessageEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.RequestFuture;
import org.jetbrains.jps.client.SimpleProtobufClient;
import org.jetbrains.jps.client.UUIDGetter;

import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12
 */
public class JavacServerClient extends SimpleProtobufClient<JavacServerResponseHandler>{

  public JavacServerClient() {
    super(JavacRemoteProto.Message.getDefaultInstance(), new UUIDGetter() {
      @NotNull
      public UUID getSessionUUID(@NotNull MessageEvent e) {
        final JavacRemoteProto.Message message = (JavacRemoteProto.Message)e.getMessage();
        final JavacRemoteProto.Message.UUID uuid = message.getSessionId();
        return new UUID(uuid.getMostSigBits(), uuid.getLeastSigBits());
      }
    });
  }

  public interface RequestCallback extends OutputFileConsumer, DiagnosticOutputConsumer {

  }

  public RequestFuture<JavacServerResponseHandler> sendCompileRequest(RequestCallback callback, final JavacServerResponseHandler responseHandler) {
    // todo: populate with data
    return sendRequest(UUID.randomUUID(), JavacRemoteProto.Message.Request.Type.COMPILE, responseHandler, new RequestFuture.CancelAction<JavacServerResponseHandler>() {
      public void cancel(RequestFuture<JavacServerResponseHandler> javacServerResponseHandlerRequestFuture) throws Exception {
        sendCancelRequest();
      }
    });
  }

  public void sendCancelRequest() {
    sendRequest(UUID.randomUUID(), JavacRemoteProto.Message.Request.Type.CANCEL, null, null);
  }

  public void sendShutdownRequest() {
    sendRequest(UUID.randomUUID(), JavacRemoteProto.Message.Request.Type.SHUTDOWN, null, null);
  }

  private RequestFuture<JavacServerResponseHandler> sendRequest(UUID requestId, final JavacRemoteProto.Message.Request.Type type, final JavacServerResponseHandler responseHandler, final RequestFuture.CancelAction<JavacServerResponseHandler> cancelAction) {
    final JavacRemoteProto.Message.Request.Builder requestBuilder = JavacRemoteProto.Message.Request.newBuilder();
    requestBuilder.setRequestType(type);
    final JavacRemoteProto.Message.Request request = requestBuilder.build();
    final JavacRemoteProto.Message msg = JavacProtoUtil.toMessage(requestId, request);
    return sendMessage(requestId, msg, responseHandler, cancelAction);
  }

}
