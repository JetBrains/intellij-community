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
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.RequestFuture;
import org.jetbrains.jps.client.SimpleProtobufClient;
import org.jetbrains.jps.client.UUIDGetter;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12
 */
public class JavacServerClient extends SimpleProtobufClient<JavacServerResponseHandler>{

  public JavacServerClient() {
    super(JavacRemoteProto.Message.getDefaultInstance(), SharedThreadPool.getInstance(), new UUIDGetter() {
      @Override
      @NotNull
      public UUID getSessionUUID(@NotNull JavacRemoteProto.Message message) {
        final JavacRemoteProto.Message.UUID uuid = message.getSessionId();
        return new UUID(uuid.getMostSigBits(), uuid.getLeastSigBits());
      }
    });
  }

  public RequestFuture<JavacServerResponseHandler> sendCompileRequest(List<String> options, Collection<File> files, Collection<File> classpath, Collection<File> platformCp, Collection<File> sourcePath, Map<File, Set<File>> outs, DiagnosticOutputConsumer diagnosticSink, OutputFileConsumer outputSink) {
    final JavacServerResponseHandler rh = new JavacServerResponseHandler(diagnosticSink, outputSink);
    final JavacRemoteProto.Message.Request request = JavacProtoUtil.createCompilationRequest(options, files, classpath, platformCp, sourcePath, outs);
    return sendRequest(request, rh, new RequestFuture.CancelAction<JavacServerResponseHandler>() {
      @Override
      public void cancel(RequestFuture<JavacServerResponseHandler> javacServerResponseHandlerRequestFuture) throws Exception {
        sendRequest(JavacProtoUtil.createCancelRequest(), null, null);
      }
    });
  }

  public RequestFuture sendShutdownRequest() {
    return sendRequest(JavacProtoUtil.createShutdownRequest(), null, null);
  }

  private RequestFuture<JavacServerResponseHandler> sendRequest(final JavacRemoteProto.Message.Request request, final JavacServerResponseHandler responseHandler, final RequestFuture.CancelAction<JavacServerResponseHandler> cancelAction) {
    final UUID requestId = UUID.randomUUID();
    return sendMessage(requestId, JavacProtoUtil.toMessage(requestId, request), responseHandler, cancelAction);
  }

}
