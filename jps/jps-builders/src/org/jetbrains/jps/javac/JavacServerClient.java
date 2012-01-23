package org.jetbrains.jps.javac;

import org.jboss.netty.channel.MessageEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.RequestFuture;
import org.jetbrains.jps.client.SimpleProtobufClient;
import org.jetbrains.jps.client.UUIDGetter;

import java.io.File;
import java.util.*;

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

  public RequestFuture<JavacServerResponseHandler> sendCompileRequest(List<String> options, Collection<File> files, Collection<File> classpath, Collection<File> platformCp, Collection<File> sourcePath, Map<File, Set<File>> outs, DiagnosticOutputConsumer diagnosticSink, OutputFileConsumer outputSink) {
    final JavacServerResponseHandler rh = new JavacServerResponseHandler(diagnosticSink, outputSink);
    final JavacRemoteProto.Message.Request request = JavacProtoUtil.createCompilationRequest(options, files, classpath, platformCp, sourcePath, outs);
    return sendRequest(request, rh, new RequestFuture.CancelAction<JavacServerResponseHandler>() {
      public void cancel(RequestFuture<JavacServerResponseHandler> javacServerResponseHandlerRequestFuture) throws Exception {
        sendRequest(JavacProtoUtil.createCancelRequest(), null, null);
      }
    });
  }

  public void sendShutdownRequest() {
    sendRequest(JavacProtoUtil.createShutdownRequest(), null, null);
  }

  private RequestFuture<JavacServerResponseHandler> sendRequest(final JavacRemoteProto.Message.Request request, final JavacServerResponseHandler responseHandler, final RequestFuture.CancelAction<JavacServerResponseHandler> cancelAction) {
    final UUID requestId = UUID.randomUUID();
    return sendMessage(requestId, JavacProtoUtil.toMessage(requestId, request), responseHandler, cancelAction);
  }

}
