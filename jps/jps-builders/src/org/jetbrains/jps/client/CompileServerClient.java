package org.jetbrains.jps.client;

import com.intellij.util.ConcurrencyUtil;
import org.jboss.netty.channel.MessageEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.*;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/11/11
 */
public class CompileServerClient extends SimpleProtobufClient<JpsServerResponseHandler> {
  private static final ScheduledThreadPoolExecutor ourPingService = ConcurrencyUtil.newSingleScheduledThreadExecutor("Compile server ping thread", Thread.MIN_PRIORITY);
  private volatile ScheduledFuture<?> myPingFuture;
  private final long myServerPingInterval;

  public CompileServerClient(long serverPingInterval, final AsyncTaskExecutor asyncExec) {
    super(JpsRemoteProto.Message.getDefaultInstance(), asyncExec, new UUIDGetter() {
      @NotNull
      public UUID getSessionUUID(@NotNull MessageEvent e) {
        final JpsRemoteProto.Message message = (JpsRemoteProto.Message)e.getMessage();
        return ProtoUtil.fromProtoUUID(message.getSessionId());
      }
    });
    myServerPingInterval = serverPingInterval;
  }

  @NotNull
  public RequestFuture sendCompileRequest(boolean isMake, String projectId, Collection<String> modules, final Collection<String> artifacts,
                                          Collection<String> paths,
                                          final Map<String, String> userData,
                                          JpsServerResponseHandler handler) throws Exception{
    checkConnected();
    final JpsRemoteProto.Message.Request request = isMake?
      ProtoUtil.createMakeRequest(projectId, modules, artifacts, userData) :
      ProtoUtil.createForceCompileRequest(projectId, modules, artifacts, paths, userData);
    return sendRequest(request, handler);
  }

  @NotNull
  public RequestFuture sendRebuildRequest(String projectId, JpsServerResponseHandler handler) throws Exception{
    checkConnected();
    return sendRequest(ProtoUtil.createRebuildRequest(projectId, Collections.<String, String>emptyMap()), handler);
  }

  @NotNull
  public RequestFuture sendShutdownRequest() throws Exception {
    checkConnected();
    return sendRequest(ProtoUtil.createShutdownRequest(true), null);
  }

  @NotNull
  public RequestFuture sendSetupRequest(final Map<String, String> pathVariables, final List<GlobalLibrary> sdkAndLibs,
                                        final String globalEncoding, final String ignoredFilesPatterns) throws Exception {
    checkConnected();
    return sendRequest(ProtoUtil.createSetupRequest(pathVariables, sdkAndLibs, globalEncoding, ignoredFilesPatterns), null);
  }

  @NotNull
  public RequestFuture sendProjectReloadRequest(Collection<String> projectPaths) throws Exception {
    checkConnected();
    return sendRequest(ProtoUtil.createReloadProjectRequest(projectPaths), null);
  }

  @NotNull
  public RequestFuture sendCancelBuildRequest(UUID sessionId) throws Exception {
    checkConnected();
    return sendRequest(ProtoUtil.createCancelRequest(sessionId), null);
  }

  @NotNull
  public RequestFuture sendFSEvent(String projectPath, Collection<String> changedPaths, Collection<String> deletedPaths) throws Exception {
    checkConnected();
    return sendRequest(ProtoUtil.createFSEvent(projectPath, changedPaths, deletedPaths), null);
  }

  private RequestFuture sendRequest(JpsRemoteProto.Message.Request request, @Nullable final JpsServerResponseHandler handler) {
    final RequestFuture.CancelAction<JpsServerResponseHandler> cancelAction = handler == null? null : new RequestFuture.CancelAction<JpsServerResponseHandler>() {
      public void cancel(RequestFuture<JpsServerResponseHandler> future) throws Exception {
        sendCancelBuildRequest(future.getRequestID());
      }
    };
    final UUID sessionUUID = UUID.randomUUID();
    return sendMessage(sessionUUID, ProtoUtil.toMessage(sessionUUID, request), handler, cancelAction);
  }

  @Override
  protected void onConnect() {
    if (myServerPingInterval > 0L) {
      myPingFuture = ourPingService.scheduleAtFixedRate(new Runnable() {
        @Override
        public void run() {
          final JpsRemoteProto.Message.Request ping = ProtoUtil.createPingRequest();
          if (isConnected()) {
            sendRequest(ping, null);
          }
        }
      }, myServerPingInterval, myServerPingInterval, TimeUnit.MILLISECONDS);
    }
  }

  @Override
  protected void beforeDisconnect() {
    final ScheduledFuture<?> future = myPingFuture;
    if (future != null) {
      future.cancel(false);
      myPingFuture = null;
    }
  }
}
