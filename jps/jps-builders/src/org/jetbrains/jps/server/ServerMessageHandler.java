package org.jetbrains.jps.server;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import org.jboss.netty.channel.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.messages.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/11/11
 */
class ServerMessageHandler extends SimpleChannelHandler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.server.ServerMessageHandler");

  private final Map<String, SequentialTaskExecutor> myTaskExecutors = new HashMap<String, SequentialTaskExecutor>();
  private final List<Pair<RunnableFuture, CompilationTask>> myBuildsInProgress = Collections.synchronizedList(new LinkedList<Pair<RunnableFuture, CompilationTask>>());
  private final Server myServer;
  private final AsyncTaskExecutor myAsyncExecutor;

  public ServerMessageHandler(Server server, final AsyncTaskExecutor asyncExecutor) {
    myServer = server;
    myAsyncExecutor = asyncExecutor;
  }

  public void messageReceived(final ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    myServer.pingReceived();
    final JpsRemoteProto.Message message = (JpsRemoteProto.Message)e.getMessage();
    final UUID sessionId = ProtoUtil.fromProtoUUID(message.getSessionId());

    JpsRemoteProto.Message reply = null;

    if (message.getMessageType() != JpsRemoteProto.Message.Type.REQUEST) {
      reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("Cannot handle message " + message.toString()));
    }
    else if (!message.hasRequest()) {
      reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("No request in message: " + message.toString()));
    }
    else {
      final JpsRemoteProto.Message.Request request = message.getRequest();
      final JpsRemoteProto.Message.Request.Type requestType = request.getRequestType();
      final ServerState facade = ServerState.getInstance();
      switch (requestType) {
        case COMPILE_REQUEST :
          reply = startBuild(sessionId, ctx, request.getCompileRequest());
          break;
        case RELOAD_PROJECT_COMMAND:
          final JpsRemoteProto.Message.Request.ReloadProjectCommand reloadProjectCommand = request.getReloadProjectCommand();
          facade.clearProjectCache(reloadProjectCommand.getProjectIdList());
          reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createCommandCompletedEvent(null));
          break;
        case CANCEL_BUILD_COMMAND:
          final JpsRemoteProto.Message.Request.CancelBuildCommand cancelCommand = request.getCancelBuildCommand();
          final UUID targetSessionId = ProtoUtil.fromProtoUUID(cancelCommand.getTargetSessionId());
          cancelSession(targetSessionId);
          reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createCommandCompletedEvent(null));
          break;
        case SETUP_COMMAND:
          final Map<String, String> pathVars = new HashMap<String, String>();
          final JpsRemoteProto.Message.Request.SetupCommand setupCommand = request.getSetupCommand();
          for (JpsRemoteProto.Message.KeyValuePair variable : setupCommand.getPathVariableList()) {
            pathVars.put(variable.getKey(), variable.getValue());
          }
          final List<GlobalLibrary> libs = new ArrayList<GlobalLibrary>();
          for (JpsRemoteProto.Message.Request.SetupCommand.GlobalLibrary library : setupCommand.getGlobalLibraryList()) {
            libs.add(
              library.hasHomePath()?
              new SdkLibrary(library.getName(), library.getTypeName(), library.hasVersion() ? library.getVersion() : null, library.getHomePath(), library.getPathList(), library.hasAdditionalDataXml()? library.getAdditionalDataXml() : null) :
              new GlobalLibrary(library.getName(), library.getPathList())
            );
          }
          final String globalEncoding = setupCommand.isInitialized()? setupCommand.getGlobalEncoding() : null;
          facade.setGlobals(libs, pathVars, globalEncoding, setupCommand.getIgnoredFilesPatterns());
          reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createCommandCompletedEvent(null));
          break;

        case SHUTDOWN_COMMAND :
          myAsyncExecutor.submit(new Runnable() {
            public void run() {
              try {
                cancelAllBuildsAndClearState();
              }
              finally {
                myServer.stop();
              }
            }
          });
          break;
        case FS_EVENT:
          final JpsRemoteProto.Message.Request.FSEvent fsEvent = request.getFsEvent();
          final String projectId = fsEvent.getProjectId();
          final ProjectDescriptor pd = facade.getProjectDescriptor(projectId);
          if (pd != null) {
            final boolean wasInterrupted = Thread.interrupted();
            try {
              try {
                for (String path : fsEvent.getChangedPathsList()) {
                  facade.notifyFileChanged(pd, new File(path));
                }
                for (String path : fsEvent.getDeletedPathsList()) {
                  facade.notifyFileDeleted(pd, new File(path));
                }
              }
              finally {
                pd.release();
              }
            }
            finally {
              if (wasInterrupted) {
                Thread.currentThread().interrupt();
              }
            }
          }
          reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createCommandCompletedEvent(null));
          break;
        case PING:
          reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createCommandCompletedEvent(null));
        default:
          reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("Unknown request: " + message));
      }
    }
    if (reply != null) {
      Channels.write(ctx.getChannel(), reply);
    }
  }

  public void cancelAllBuildsAndClearState() {
    final List<RunnableFuture> futures = new ArrayList<RunnableFuture>();

    synchronized (myBuildsInProgress) {
      for (Iterator<Pair<RunnableFuture, CompilationTask>> it = myBuildsInProgress.iterator(); it.hasNext(); ) {
        final Pair<RunnableFuture, CompilationTask> pair = it.next();
        it.remove();
        pair.second.cancel();
        final RunnableFuture future = pair.first;
        futures.add(future);
        future.cancel(false);
      }
    }

    ServerState.getInstance().clearCahedState();

    // wait until really stopped
    for (RunnableFuture future : futures) {
      try {
        future.get();
      }
      catch (InterruptedException ignored) {
      }
      catch (ExecutionException ignored) {
      }
    }
  }

  private void cancelSession(UUID targetSessionId) {
    synchronized (myBuildsInProgress) {
      for (Iterator<Pair<RunnableFuture, CompilationTask>> it = myBuildsInProgress.iterator(); it.hasNext(); ) {
        final Pair<RunnableFuture, CompilationTask> pair = it.next();
        final CompilationTask task = pair.second;
        if (task.getSessionId().equals(targetSessionId)) {
          it.remove();
          task.cancel();
          pair.first.cancel(false);
          break;
        }
      }
    }
  }

  @Override
  public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    final Object attachment = ctx.getAttachment();
    if (attachment instanceof UUID) {
      cancelSession((UUID)attachment);
    }
    super.channelDisconnected(ctx, e);
  }

  @Nullable
  private JpsRemoteProto.Message startBuild(UUID sessionId, final ChannelHandlerContext channelContext, JpsRemoteProto.Message.Request.CompilationRequest compileRequest) {
    if (!compileRequest.hasProjectId()) {
      return ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("No project specified"));
    }

    final String projectId = compileRequest.getProjectId();
    final JpsRemoteProto.Message.Request.CompilationRequest.Type compileType = compileRequest.getCommandType();

    switch (compileType) {
      // todo
      case MAKE:
      case FORCED_COMPILATION:
      case REBUILD: {
        channelContext.setAttachment(sessionId);
        final BuildType buildType = convertCompileType(compileType);
        final List<String> modules = compileRequest.getModuleNameList();
        final List<String> artifacts = compileRequest.getArtifactNameList();
        final List<String> paths = compileRequest.getFilePathList();
        final Map<String, String> builderParams = new HashMap<String, String>();
        for (JpsRemoteProto.Message.KeyValuePair pair : compileRequest.getBuilderParameterList()) {
          builderParams.put(pair.getKey(), pair.getValue());
        }
        final CompilationTask task = new CompilationTask(sessionId, channelContext, projectId, buildType, modules, artifacts, builderParams, paths);
        final RunnableFuture future = getCompileTaskExecutor(projectId).submit(task);
        myBuildsInProgress.add(new Pair<RunnableFuture, CompilationTask>(future, task));
        return null;
      }

      default:
        return ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("Unsupported command: '" + compileType + "'"));
    }
  }

  @NotNull
  private SequentialTaskExecutor getCompileTaskExecutor(String projectId) {
    synchronized (myTaskExecutors) {
      SequentialTaskExecutor executor = myTaskExecutors.get(projectId);
      if (executor == null) {
        executor = new SequentialTaskExecutor(myAsyncExecutor);
        myTaskExecutors.put(projectId, executor);
      }
      return executor;
    }
  }

  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    LOG.error(e);
    ctx.sendUpstream(e);
  }

  public boolean hasRunningBuilds() {
    return !myBuildsInProgress.isEmpty();
  }

  private class CompilationTask implements Runnable, CanceledStatus {

    private final UUID mySessionId;
    private final ChannelHandlerContext myChannelContext;
    private final String myProjectPath;
    private final BuildType myBuildType;
    private final Collection<String> myArtifacts;
    private final Map<String, String> myBuilderParams;
    private final Collection<String> myPaths;
    private final Set<String> myModules;
    private volatile boolean myCanceled = false;

    public CompilationTask(UUID sessionId,
                           ChannelHandlerContext channelContext,
                           String projectId,
                           BuildType buildType,
                           Collection<String> modules,
                           Collection<String> artifacts,
                           Map<String, String> builderParams, Collection<String> paths) {
      mySessionId = sessionId;
      myChannelContext = channelContext;
      myProjectPath = projectId;
      myBuildType = buildType;
      myArtifacts = artifacts;
      myBuilderParams = builderParams;
      myPaths = paths;
      myModules = new HashSet<String>(modules);
    }

    public UUID getSessionId() {
      return mySessionId;
    }

    public boolean isCanceled() {
      return myCanceled;
    }

    public void run() {
      Channels.write(myChannelContext.getChannel(), ProtoUtil.toMessage(mySessionId, ProtoUtil.createBuildStartedEvent("build started")));
      Throwable error = null;
      final Ref<Boolean> hasErrors = new Ref<Boolean>(false);
      final Ref<Boolean> markedFilesUptodate = new Ref<Boolean>(false);
      try {
        ServerState.getInstance().startBuild(myProjectPath, myBuildType, myModules, myArtifacts, myBuilderParams, myPaths, new MessageHandler() {
          public void processMessage(BuildMessage buildMessage) {
            final JpsRemoteProto.Message.Response response;
            if (buildMessage instanceof FileGeneratedEvent) {
              final Collection<Pair<String, String>> paths = ((FileGeneratedEvent)buildMessage).getPaths();
              response = !paths.isEmpty()? ProtoUtil.createFileGeneratedEvent(paths) : null;
            }
            else if (buildMessage instanceof UptoDateFilesSavedEvent) {
              markedFilesUptodate.set(true);
              response = null;
            }
            else if (buildMessage instanceof CompilerMessage) {
              markedFilesUptodate.set(true);
              final CompilerMessage compilerMessage = (CompilerMessage)buildMessage;
              final String text = compilerMessage.getCompilerName() + ": " + compilerMessage.getMessageText();
              final BuildMessage.Kind kind = compilerMessage.getKind();
              if (kind == BuildMessage.Kind.ERROR) {
                hasErrors.set(true);
              }
              response = ProtoUtil.createCompileMessageResponse(
                kind, text, compilerMessage.getSourcePath(),
                compilerMessage.getProblemBeginOffset(), compilerMessage.getProblemEndOffset(),
                compilerMessage.getProblemLocationOffset(), compilerMessage.getLine(), compilerMessage.getColumn(),
                -1.0f);
            }
            else {
              float done = -1.0f;
              if (buildMessage instanceof ProgressMessage) {
                done = ((ProgressMessage)buildMessage).getDone();
              }
              response = ProtoUtil.createCompileProgressMessageResponse(buildMessage.getMessageText(), done);
            }
            if (response != null) {
              Channels.write(myChannelContext.getChannel(), ProtoUtil.toMessage(mySessionId, response));
            }
          }
        }, this);
      }
      catch (Throwable e) {
        LOG.info(e);
        error = e;
      }
      finally {
        finishBuild(error, hasErrors.get(), markedFilesUptodate.get());
      }
    }

    private void finishBuild(@Nullable Throwable error, boolean hadBuildErrors, boolean markedUptodateFiles) {
      JpsRemoteProto.Message lastMessage = null;
      try {
        if (error != null) {
          Throwable cause = error.getCause();
          if (cause == null) {
            cause = error;
          }
          final ByteArrayOutputStream out = new ByteArrayOutputStream();
          cause.printStackTrace(new PrintStream(out));

          final StringBuilder messageText = new StringBuilder();
          messageText.append("JPS Internal error: (").append(cause.getClass().getName()).append(") ").append(cause.getMessage());
          final String trace = out.toString();
          if (!trace.isEmpty()) {
            messageText.append("\n").append(trace);
          }
          lastMessage = ProtoUtil.toMessage(mySessionId, ProtoUtil.createFailure(messageText.toString(), cause));
        }
        else {
          JpsRemoteProto.Message.Response.BuildEvent.Status status = JpsRemoteProto.Message.Response.BuildEvent.Status.SUCCESS;
          if (myCanceled) {
            status = JpsRemoteProto.Message.Response.BuildEvent.Status.CANCELED;
          }
          else if (hadBuildErrors) {
            status = JpsRemoteProto.Message.Response.BuildEvent.Status.ERRORS;
          }
          else if (!markedUptodateFiles){
            status = JpsRemoteProto.Message.Response.BuildEvent.Status.UP_TO_DATE;
          }
          lastMessage = ProtoUtil.toMessage(mySessionId, ProtoUtil.createBuildCompletedEvent("build completed", status));
        }
      }
      catch (Throwable e) {
        lastMessage = ProtoUtil.toMessage(mySessionId, ProtoUtil.createFailure(e.getMessage(), e));
      }
      finally {
        Channels.write(myChannelContext.getChannel(), lastMessage).addListener(new ChannelFutureListener() {
          public void operationComplete(ChannelFuture future) throws Exception {
            final UUID sessionId = getSessionId();
            synchronized (myBuildsInProgress) {
              for (Iterator<Pair<RunnableFuture, CompilationTask>> it = myBuildsInProgress.iterator(); it.hasNext(); ) {
                final CompilationTask task = it.next().second;
                if (sessionId.equals(task.getSessionId())) {
                  it.remove();
                  break;
                }
              }
            }
          }
        });
      }
    }

    public void cancel() {
      myCanceled = true;
    }
  }

  private static BuildType convertCompileType(JpsRemoteProto.Message.Request.CompilationRequest.Type compileType) {
    switch (compileType) {
      case CLEAN: return BuildType.CLEAN;
      case MAKE: return BuildType.MAKE;
      case REBUILD: return BuildType.PROJECT_REBUILD;
      case FORCED_COMPILATION: return BuildType.FORCED_COMPILATION;
    }
    return BuildType.MAKE; // use make by default
  }
}
