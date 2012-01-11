package org.jetbrains.jps.server;

import com.intellij.openapi.diagnostic.Logger;
import org.jboss.netty.channel.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/11/11
 */
class ServerMessageHandler extends SimpleChannelHandler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.server.ServerMessageHandler");

  private final ConcurrentHashMap<String, CompilationTask> myBuildsInProgress = new ConcurrentHashMap<String, CompilationTask>();
  private final ExecutorService myBuildsExecutor;
  private final Server myServer;

  public ServerMessageHandler(ExecutorService buildsExecutor, Server server) {
    myBuildsExecutor = buildsExecutor;
    myServer = server;
  }

  public void messageReceived(final ChannelHandlerContext ctx, MessageEvent e) throws Exception {
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
          break;
        case SETUP_COMMAND:
          final Map<String, String> pathVars = new HashMap<String, String>();
          final JpsRemoteProto.Message.Request.SetupCommand setupCommand = request.getSetupCommand();
          for (JpsRemoteProto.Message.Request.SetupCommand.PathVariable variable : setupCommand.getPathVariableList()) {
            pathVars.put(variable.getName(), variable.getValue());
          }
          final List<GlobalLibrary> libs = new ArrayList<GlobalLibrary>();
          for (JpsRemoteProto.Message.Request.SetupCommand.GlobalLibrary library : setupCommand.getGlobalLibraryList()) {
            libs.add(
              library.hasHomePath()?
              new SdkLibrary(library.getName(), library.getHomePath(), library.getPathList()) :
              new GlobalLibrary(library.getName(), library.getPathList())
            );
          }
          facade.setGlobals(libs, pathVars);
          reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createCommandCompletedEvent(null));
          break;

        case SHUTDOWN_COMMAND :
          // todo pay attention to policy
          myBuildsExecutor.submit(new Runnable() {
            public void run() {
              myServer.stop();
            }
          });
          break;
        case FS_EVENT:
          final JpsRemoteProto.Message.Request.FSEvent fsEvent = request.getFsEvent();
          final String projectId = fsEvent.getProjectId();
          final ProjectDescriptor pd = facade.getProjectDescriptor(projectId);
          if (pd != null) {
            for (String path : fsEvent.getChangedPathsList()) {
              facade.notifyFileChanged(pd, new File(path));
            }
            for (String path : fsEvent.getDeletedPathsList()) {
              facade.notifyFileDeleted(pd, new File(path));
            }
          }
          break;
        default:
          reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("Unknown request: " + message));
      }
    }
    if (reply != null) {
      Channels.write(ctx.getChannel(), reply);
    }
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
      case CLEAN:
      case MAKE:
      case FORCED_COMPILATION:
      case REBUILD: {
        final CompilationTask task = new CompilationTask(sessionId, channelContext, projectId, compileRequest.getModuleNameList());
        if (myBuildsInProgress.putIfAbsent(projectId, task) == null) {
          task.getBuildParams().buildType = convertCompileType(compileType);
          task.getBuildParams().useInProcessJavac = true;
          myBuildsExecutor.submit(task);
        }
        else {
          return ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("Project is being compiled already"));
        }
        return null;
      }

      case CANCEL: {
        final CompilationTask task = myBuildsInProgress.get(projectId);
        if (task != null && task.getSessionId() == sessionId) {
          task.cancel();
        }
        return null;
      }

      default:
        return ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("Unsupported command: '" + compileType + "'"));
    }
  }

  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    if (this == ctx.getPipeline().getLast()) {
      LOG.error(e);
    }
    ctx.sendUpstream(e);
  }

  private class CompilationTask implements Runnable {

    private final UUID mySessionId;
    private final ChannelHandlerContext myChannelContext;
    private final String myProjectPath;
    private final Set<String> myModules;
    private final BuildParameters myParams;

    public CompilationTask(UUID sessionId, ChannelHandlerContext channelContext, String projectId, List<String> modules) {
      mySessionId = sessionId;
      myChannelContext = channelContext;
      myProjectPath = projectId;
      myModules = new HashSet<String>(modules);
      myParams = new BuildParameters();
    }

    public BuildParameters getBuildParams() {
      return myParams;
    }

    public UUID getSessionId() {
      return mySessionId;
    }

    public void run() {
      Channels.write(myChannelContext.getChannel(), ProtoUtil.toMessage(mySessionId, ProtoUtil.createBuildStartedEvent("build started")));
      Throwable error = null;
      try {
        ServerState.getInstance().startBuild(myProjectPath, myModules, myParams, new MessageHandler() {
          public void processMessage(BuildMessage buildMessage) {
            final JpsRemoteProto.Message.Response response;
            if (buildMessage instanceof CompilerMessage) {
              final CompilerMessage compilerMessage = (CompilerMessage)buildMessage;
              final String text = compilerMessage.getCompilerName() + ": " + compilerMessage.getMessageText();
              response = ProtoUtil.createCompileMessageResponse(
                compilerMessage.getKind(), text, compilerMessage.getSourcePath(),
                compilerMessage.getProblemBeginOffset(), compilerMessage.getProblemEndOffset(),
                compilerMessage.getProblemLocationOffset(), compilerMessage.getLine(), compilerMessage.getColumn()
              );
            }
            else {
              response = ProtoUtil.createCompileProgressMessageResponse(buildMessage.getMessageText());
            }
            Channels.write(myChannelContext.getChannel(), ProtoUtil.toMessage(mySessionId, response));
          }
        });
      }
      catch (Throwable e) {
        error = e;
      }
      finally {
        finishBuild(error);
      }
    }

    private void finishBuild(@Nullable Throwable error) {
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
          lastMessage = ProtoUtil.toMessage(mySessionId, ProtoUtil.createBuildCompletedEvent("build completed"));
        }
      }
      catch (Throwable e) {
        lastMessage = ProtoUtil.toMessage(mySessionId, ProtoUtil.createFailure(e.getMessage(), e));
      }
      finally {
        Channels.write(myChannelContext.getChannel(), lastMessage).addListener(new ChannelFutureListener() {
          public void operationComplete(ChannelFuture future) throws Exception {
            myBuildsInProgress.remove(myProjectPath);
          }
        });
      }
    }

    public void cancel() {
      // todo
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
