package org.jetbrains.jpsservice.impl;

import org.codehaus.gant.GantBinding;
import org.jboss.netty.channel.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.ProjectWrapper;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.listeners.BuildInfoPrinter;
import org.jetbrains.jpsservice.JpsRemoteProto;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/11/11
 */
public class JpsServerMessageHandler extends SimpleChannelHandler {
  private ConcurrentHashMap<String, String> myBuildsInProgress = new ConcurrentHashMap<String, String>();
  private final ExecutorService myBuildsExecutorService;

  public JpsServerMessageHandler(ExecutorService buildsExecutorService) {
    myBuildsExecutorService = buildsExecutorService;
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
            // todo: perform stop and pay attention to policy
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
    private final List<String> myModules;

    public CompilationTask(UUID sessionId, ChannelHandlerContext channelContext, JpsRemoteProto.Message.Request.CompilationRequest.Type compileType, String projectId, List<String> modules) {
      mySessionId = sessionId;
      myChannelContext = channelContext;
      myCompileType = compileType;
      myProjectPath = projectId;
      myModules = modules;
    }

    public void run() {
      Channels.write(myChannelContext.getChannel(), ProtoUtil.toMessage(mySessionId, ProtoUtil.createCommandAcceptedResponse("build started")));
      Throwable error = null;
      try {
        final int size = myModules.size();

        final Map<String,String> pathVars = new HashMap<String, String>(); // todo
        pathVars.put("MAVEN_REPOSITORY", "C:/Users/jeka/.m2/repository");

        final ProjectWrapper proj = ProjectWrapper.load(new GantBinding(), myProjectPath, getStartupScript(), pathVars, myCompileType == JpsRemoteProto.Message.Request.CompilationRequest.Type.MAKE);

        proj.getProject().getBuilder().setBuildInfoPrinter(new BuildInfoPrinter() {
          public Object printProgressMessage(Project project, String message) {
            Channels.write(myChannelContext.getChannel(), ProtoUtil.toMessage(mySessionId, ProtoUtil.createCompileProgressMessageResponse(message)));
            return null;
          }

          public Object printCompilationErrors(Project project, String compilerName, String messages) {
            Channels.write(myChannelContext.getChannel(), ProtoUtil.toMessage(mySessionId, ProtoUtil.createCompileErrorMessageResponse(messages, null, -1, -1)));
            return null;
          }
        });

        switch (myCompileType) {
          case REBUILD:
            proj.rebuild();
            break;
          case MAKE:
            proj.makeModules(null, createMakeFlags());
            break;
          case CLEAN:
            proj.clean();
            break;
        }
        proj.save();
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

    private String getStartupScript() {
      return "import org.jetbrains.jps.*\n" +
        "\n" +
        //"project.createJavaSdk (\n" +
        //"   \"IDEA jdk\", \n" +
        //"   \"/home/db/develop/jetbrains/jdk1.6.0_22\", \n" +
        //"   {      \n" +
        //"     getDelegate ().classpath (\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/plugin.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/charsets.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/jce.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/rt.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/management-agent.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/resources.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/deploy.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/jsse.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/javaws.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/alt-rt.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/ext/sunpkcs11.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/ext/dnsns.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/ext/localedata.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/ext/sunjce_provider.jar\",\n" +
        //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/lib/tools.jar\"\n" +
        //"     )\n" +
        //"   }\n" +
        //")\n" +
        //"\n" +
        //"project.projectSdk = project.sdks [\"IDEA jdk\"]\n" +
        "project.builder.useInProcessJavac = true";
    }

    private ProjectWrapper.Flags createMakeFlags() {
      return new ProjectWrapper.Flags() {
        public boolean tests() {
          return true;
        }

        public boolean incremental() {
          return true;
        }

        public boolean force() {
          return false;
        }

        public PrintStream logStream() {
          return null; // todo
        }
      };
    }
  }

}
