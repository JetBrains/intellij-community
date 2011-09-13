package org.jetbrains.jpsservice.impl;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jpsservice.JpsRemoteProto;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/15/11
 */
public class ProtoUtil {

  public static JpsRemoteProto.Message.Failure createFailure(final String description) {
    return createFailure(description, null);
  }

  public static JpsRemoteProto.Message.Failure createFailure(final String description, @Nullable Throwable reason) {
    final JpsRemoteProto.Message.Failure.Builder builder = JpsRemoteProto.Message.Failure.newBuilder().setDescription(description);
    if (reason != null) {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      reason.printStackTrace(new PrintStream(baos));
      builder.setStacktrace(new String(baos.toByteArray()));
    }
    return builder.build();
  }

  public static JpsRemoteProto.Message.Request createMakeRequest(String project, Collection<String> modules) {
    return createCompileRequest(JpsRemoteProto.Message.Request.CompilationRequest.Type.MAKE, project, modules);
  }

  public static JpsRemoteProto.Message.Request createRebuildRequest(String project, Collection<String> modules) {
    return createCompileRequest(JpsRemoteProto.Message.Request.CompilationRequest.Type.REBUILD, project, modules);
  }

  public static JpsRemoteProto.Message.Request createCleanRequest(String project, Collection<String> modules) {
    return createCompileRequest(JpsRemoteProto.Message.Request.CompilationRequest.Type.CLEAN, project, modules);
  }

  public static JpsRemoteProto.Message.Request createCancelRequest(String project) {
    return createCompileRequest(JpsRemoteProto.Message.Request.CompilationRequest.Type.CANCEL, project, Collections.<String>emptyList());
  }

  public static JpsRemoteProto.Message.Request createCompileRequest(final JpsRemoteProto.Message.Request.CompilationRequest.Type command, String project, Collection<String> modules) {
    final JpsRemoteProto.Message.Request.CompilationRequest.Builder builder = JpsRemoteProto.Message.Request.CompilationRequest.newBuilder().setCommandType(
      command);
    builder.setProjectId(project);
    if (modules.size() > 0) {
      builder.addAllModuleName(modules);
    }
    return JpsRemoteProto.Message.Request.newBuilder().setRequestType(JpsRemoteProto.Message.Request.Type.COMPILE_REQUEST).setCompileRequest(
      builder.build()).build();
  }

  public static JpsRemoteProto.Message.Request createShutdownRequest(boolean cancelRunningBuilds) {
    final JpsRemoteProto.Message.Request.ShutdownCommand.Builder builder = JpsRemoteProto.Message.Request.ShutdownCommand.newBuilder();
    builder.setShutdownPolicy(
      cancelRunningBuilds? JpsRemoteProto.Message.Request.ShutdownCommand.ShutdownPolicy.CANCEL_RUNNING_BUILDS : JpsRemoteProto.Message.Request.ShutdownCommand.ShutdownPolicy.WAIT_RUNNING_BUILDS
    );
    return JpsRemoteProto.Message.Request.newBuilder().setRequestType(JpsRemoteProto.Message.Request.Type.SHUTDOWN_COMMAND).setShutdownCommand(builder.build()).build();
  }

  public static JpsRemoteProto.Message.Request createReloadProjectRequest(Collection<String> projects) {
    final JpsRemoteProto.Message.Request.ReloadProjectCommand.Builder builder = JpsRemoteProto.Message.Request.ReloadProjectCommand.newBuilder();
    builder.addAllProjectId(projects);
    return JpsRemoteProto.Message.Request.newBuilder().setRequestType(JpsRemoteProto.Message.Request.Type.RELOAD_PROJECT_COMMAND).setReloadProjectCommand(builder.build()).build();
  }

  public static JpsRemoteProto.Message.Request createSetupRequest(final Map<String, String> pathVars) {
    final JpsRemoteProto.Message.Request.SetupCommand.Builder cmdBuilder = JpsRemoteProto.Message.Request.SetupCommand.newBuilder();

    if (pathVars.size() > 0) {
      final JpsRemoteProto.Message.Request.SetupCommand.PathVariable.Builder pathVarBuilder =
        JpsRemoteProto.Message.Request.SetupCommand.PathVariable.newBuilder();

      for (Map.Entry<String, String> entry : pathVars.entrySet()) {
        final String var = entry.getKey();
        final String value = entry.getValue();
        if (var != null && value != null) {
          cmdBuilder.addPathVariable(pathVarBuilder.setName(var).setValue(value).build());
        }
      }
    }

    return JpsRemoteProto.Message.Request.newBuilder().setRequestType(JpsRemoteProto.Message.Request.Type.SETUP_COMMAND).setSetupCommand(cmdBuilder.build()).build();
  }

  public static JpsRemoteProto.Message.Response createBuildStartedEvent(@Nullable String description) {
    return createBuildEvent(JpsRemoteProto.Message.Response.BuildEvent.Type.BUILD_STARTED, description);
  }

  public static JpsRemoteProto.Message.Response createBuildCompletedEvent(@Nullable String description) {
    return createBuildEvent(JpsRemoteProto.Message.Response.BuildEvent.Type.BUILD_COMPLETED, description);
  }

  public static JpsRemoteProto.Message.Response createBuildCanceledEvent(@Nullable String description) {
    return createBuildEvent(JpsRemoteProto.Message.Response.BuildEvent.Type.BUILD_CANCELED, description);
  }

  public static JpsRemoteProto.Message.Response createCommandCompletedEvent(@Nullable String description) {
    return createBuildEvent(JpsRemoteProto.Message.Response.BuildEvent.Type.COMMAND_COMPLETED, description);
  }

  public static JpsRemoteProto.Message.Response createBuildEvent(final JpsRemoteProto.Message.Response.BuildEvent.Type type, @Nullable String description) {
    final JpsRemoteProto.Message.Response.BuildEvent.Builder builder = JpsRemoteProto.Message.Response.BuildEvent.newBuilder().setEventType(type);
    if (description != null) {
      builder.setDescription(description);
    }
    return JpsRemoteProto.Message.Response.newBuilder().setResponseType(JpsRemoteProto.Message.Response.Type.BUILD_EVENT).setBuildEvent(builder.build()).build();
  }

  public static JpsRemoteProto.Message.Response createCompileInfoMessageResponse(String text, String path) {
    return createCompileMessageResponse(JpsRemoteProto.Message.Response.CompileMessage.Kind.INFO, text, path, -1, -1);
  }

  public static JpsRemoteProto.Message.Response createCompileProgressMessageResponse(String text) {
    return createCompileMessageResponse(JpsRemoteProto.Message.Response.CompileMessage.Kind.PROGRESS, text, null, -1, -1);
  }

  public static JpsRemoteProto.Message.Response createCompileWarningMessageResponse(String text, String path, int line, int column) {
    return createCompileMessageResponse(JpsRemoteProto.Message.Response.CompileMessage.Kind.WARNING, text, path, line, column);
  }

  public static JpsRemoteProto.Message.Response createCompileErrorMessageResponse(String text, String path, int line, int column) {
    return createCompileMessageResponse(JpsRemoteProto.Message.Response.CompileMessage.Kind.ERROR, text, path, line, column);
  }

  public static JpsRemoteProto.Message.Response createCompileMessageResponse(final JpsRemoteProto.Message.Response.CompileMessage.Kind msgKind, String text, String path, int line, int column) {
    final JpsRemoteProto.Message.Response.CompileMessage.Builder builder = JpsRemoteProto.Message.Response.CompileMessage.newBuilder().setKind(msgKind);
    if (text != null) {
      builder.setText(text);
    }
    if (path != null) {
      builder.setSourceFilePath(path);
    }
    if (line >=0) {
      builder.setLine(line);
    }
    if (column >=0) {
      builder.setColumn(column);
    }
    return JpsRemoteProto.Message.Response.newBuilder().setResponseType(JpsRemoteProto.Message.Response.Type.COMPILE_MESSAGE).setCompileMessage(builder.build()).build();
  }

  public static JpsRemoteProto.Message toMessage(final UUID sessionId, JpsRemoteProto.Message.Response response) {
    return JpsRemoteProto.Message.newBuilder().setSessionId(toProtoUUID(sessionId)).setMessageType(JpsRemoteProto.Message.Type.RESPONSE).setResponse(response).build();
  }

  public static JpsRemoteProto.Message toMessage(final UUID sessionId, JpsRemoteProto.Message.Request request) {
    return JpsRemoteProto.Message.newBuilder().setSessionId(toProtoUUID(sessionId)).setMessageType(JpsRemoteProto.Message.Type.REQUEST).setRequest(request).build();
  }

  public static JpsRemoteProto.Message toMessage(final UUID sessionId, JpsRemoteProto.Message.Failure failure) {
    return JpsRemoteProto.Message.newBuilder().setSessionId(toProtoUUID(sessionId)).setMessageType(JpsRemoteProto.Message.Type.FAILURE).setFailure(failure).build();
  }

  public static JpsRemoteProto.Message.UUID toProtoUUID(UUID requestId) {
    return JpsRemoteProto.Message.UUID.newBuilder().setMostSigBits(requestId.getMostSignificantBits()).setLeastSigBits(requestId.getLeastSignificantBits()).build();
  }

  public static UUID fromProtoUUID(JpsRemoteProto.Message.UUID uuid) {
    return new UUID(uuid.getMostSigBits(), uuid.getLeastSigBits());
  }

}
