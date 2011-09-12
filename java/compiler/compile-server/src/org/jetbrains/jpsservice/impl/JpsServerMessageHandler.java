package org.jetbrains.jpsservice.impl;

import org.jboss.netty.channel.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.server.BuildParameters;
import org.jetbrains.jps.server.BuildType;
import org.jetbrains.jps.server.Facade;
import org.jetbrains.jps.server.MessagesConsumer;
import org.jetbrains.jpsservice.JpsRemoteProto;
import org.jetbrains.jpsservice.Server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/11/11
 */
public class JpsServerMessageHandler extends SimpleChannelHandler {
  private ConcurrentHashMap<String, String> myBuildsInProgress = new ConcurrentHashMap<String, String>();
  private final ExecutorService myBuildsExecutorService;
  private final Server myServer;

  public JpsServerMessageHandler(ExecutorService buildsExecutorService, Server server) {
    myBuildsExecutorService = buildsExecutorService;
    myServer = server;
  }

  public void messageReceived(final ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    final JpsRemoteProto.Message message = (JpsRemoteProto.Message)e.getMessage();
    final UUID sessionId = ProtoUtil.fromProtoUUID(message.getSessionId());

    JpsRemoteProto.Message responseMessage = null;
    boolean shutdown = false;

    if (message.getMessageType() != JpsRemoteProto.Message.Type.REQUEST) {
      responseMessage = ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("Cannot handle message " + message.toString()));
    }
    else if (!message.hasRequest()) {
      responseMessage = ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("No request in message: " + message.toString()));
    }
    else {
      final JpsRemoteProto.Message.Request request = message.getRequest();
      final JpsRemoteProto.Message.Request.Type requestType = request.getRequestType();
      if (requestType == JpsRemoteProto.Message.Request.Type.COMPILE_REQUEST) {
        final JpsRemoteProto.Message.Response response = startBuild(sessionId, ctx, request.getCompileRequest());
        if (response != null) {
          responseMessage = ProtoUtil.toMessage(sessionId, response);
        }
      }
      else if (requestType == JpsRemoteProto.Message.Request.Type.SHUTDOWN_COMMAND){
        shutdown = true;
        responseMessage = ProtoUtil.toMessage(sessionId, ProtoUtil.createCommandAcceptedResponse(null));
      }
      else {
        responseMessage = ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("Unknown request: " + message));
      }
    }
    if (responseMessage != null) {
      final ChannelFuture future = Channels.write(ctx.getChannel(), responseMessage);
      if (shutdown) {
        future.addListener(new ChannelFutureListener() {
          public void operationComplete(ChannelFuture future) throws Exception {
            // todo pay attention to policy
            myBuildsExecutorService.submit(new Runnable() {
              public void run() {
                myServer.stop();
              }
            });
          }
        });
      }
    }
  }

  @Nullable
  private JpsRemoteProto.Message.Response startBuild(UUID sessionId, final ChannelHandlerContext channelContext, JpsRemoteProto.Message.Request.CompilationRequest compileRequest) {
    if (!compileRequest.hasProjectId()) {
      return ProtoUtil.createCommandRejectedResponse("No project specified");
    }

    final String projectId = compileRequest.getProjectId();
    final JpsRemoteProto.Message.Request.CompilationRequest.Type commandType = compileRequest.getCommandType();

    if (commandType == JpsRemoteProto.Message.Request.CompilationRequest.Type.CLEAN ||
        commandType == JpsRemoteProto.Message.Request.CompilationRequest.Type.MAKE ||
        commandType == JpsRemoteProto.Message.Request.CompilationRequest.Type.REBUILD) {
      if (myBuildsInProgress.putIfAbsent(projectId, "") != null) {
        return ProtoUtil.createCommandRejectedResponse("Project is being compiled already");
      }
      myBuildsExecutorService.submit(new CompilationTask(sessionId, channelContext, commandType, projectId, compileRequest.getModuleNameList()));
      return null; // the rest will be handled asynchronously
    }

    if (commandType == JpsRemoteProto.Message.Request.CompilationRequest.Type.CANCEL) {
      final String projectInProgress = myBuildsInProgress.remove(projectId);
      if (projectInProgress == null) {
        return ProtoUtil.createCommandRejectedResponse("Build for requested project is not running");
      }
      // todo: perform cancel
      return ProtoUtil.createBuildCanceledResponse(projectId);
    }

    return ProtoUtil.createCommandRejectedResponse("Unsupported command: '" + commandType + "'");
  }

  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    super.exceptionCaught(ctx, e);
  }

  private class CompilationTask implements Runnable {

    private final UUID mySessionId;
    private final ChannelHandlerContext myChannelContext;
    private final JpsRemoteProto.Message.Request.CompilationRequest.Type myCompileType;
    private final String myProjectPath;
    private final Set<String> myModules;

    public CompilationTask(UUID sessionId, ChannelHandlerContext channelContext, JpsRemoteProto.Message.Request.CompilationRequest.Type compileType, String projectId, List<String> modules) {
      mySessionId = sessionId;
      myChannelContext = channelContext;
      myCompileType = compileType;
      myProjectPath = projectId;
      myModules = new HashSet<String>(modules);
    }

    public void run() {
      Channels.write(myChannelContext.getChannel(), ProtoUtil.toMessage(mySessionId, ProtoUtil.createCommandAcceptedResponse("build started")));
      Throwable error = null;
      try {
        final BuildType buildType = convertCompileType(myCompileType);
        if (buildType == null) {
          throw new Exception("Unsupported build type: " + myCompileType);
        }

        final Map<String,String> pathVars = new HashMap<String, String>(); // todo
        pathVars.put("MAVEN_REPOSITORY", "C:/Users/jeka/.m2/repository");

        final BuildParameters params = new BuildParameters();
        params.buildType = buildType;
        params.pathVariables = pathVars;
        params.useInProcessJavac = true;

        Facade.getInstance().startBuild(myProjectPath, myModules, params, new MessagesConsumer() {
          public void consumeProgressMessage(String message) {
            Channels.write(
              myChannelContext.getChannel(), ProtoUtil.toMessage(mySessionId, ProtoUtil .createCompileProgressMessageResponse( message))
            );
          }

          public void consumeCompilerMessage(String compilerName, String message) {
            Channels.write(
              myChannelContext.getChannel(), ProtoUtil.toMessage(mySessionId, ProtoUtil.createCompileErrorMessageResponse(message, null, -1, -1))
            );
          }
        });
      }
      catch (Throwable e) {
        error = e;
      }
      finally {
        final JpsRemoteProto.Message lastMessage = error != null?
                  ProtoUtil.toMessage(mySessionId, ProtoUtil.createFailure("build failed: ", error)) :
                  ProtoUtil.toMessage(mySessionId, ProtoUtil.createBuildCompletedResponse("build completed"));

        Channels.write(myChannelContext.getChannel(), lastMessage).addListener(new ChannelFutureListener() {
          public void operationComplete(ChannelFuture future) throws Exception {
            myBuildsInProgress.remove(myProjectPath);
          }
        });
      }
    }

    private BuildType convertCompileType(JpsRemoteProto.Message.Request.CompilationRequest.Type compileType) {
      switch (compileType) {
        case CLEAN: return BuildType.CLEAN;
        case MAKE: return BuildType.MAKE;
        case REBUILD: return BuildType.REBUILD;
      }
      return null;
    }

  }

}
