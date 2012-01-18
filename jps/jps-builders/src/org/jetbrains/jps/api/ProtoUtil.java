package org.jetbrains.jps.api;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.messages.BuildMessage;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;

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
    return createCompileRequest(JpsRemoteProto.Message.Request.CompilationRequest.Type.MAKE, project, modules, Collections.<String>emptyList());
  }

  public static JpsRemoteProto.Message.Request createForceCompileRequest(String project, Collection<String> modules, Collection<String> paths) {
    return createCompileRequest(JpsRemoteProto.Message.Request.CompilationRequest.Type.FORCED_COMPILATION, project, modules, paths);
  }

  public static JpsRemoteProto.Message.Request createRebuildRequest(String project) {
    return createCompileRequest(JpsRemoteProto.Message.Request.CompilationRequest.Type.REBUILD, project, Collections.<String>emptyList(), Collections.<String>emptyList());
  }

  public static JpsRemoteProto.Message.Request createCleanRequest(String project, Collection<String> modules) {
    return createCompileRequest(JpsRemoteProto.Message.Request.CompilationRequest.Type.CLEAN, project, modules, Collections.<String>emptyList());
  }

  public static JpsRemoteProto.Message.Request createCancelRequest(UUID compileSessionId) {
    final JpsRemoteProto.Message.Request.CancelBuildCommand.Builder builder = JpsRemoteProto.Message.Request.CancelBuildCommand.newBuilder();
    builder.setTargetSessionId(toProtoUUID(compileSessionId));
    return JpsRemoteProto.Message.Request.newBuilder().setRequestType(JpsRemoteProto.Message.Request.Type.CANCEL_BUILD_COMMAND).setCancelBuildCommand(builder.build()).build();
  }

  public static JpsRemoteProto.Message.Request createCompileRequest(final JpsRemoteProto.Message.Request.CompilationRequest.Type command, String project, Collection<String> modules, Collection<String> paths) {
    final JpsRemoteProto.Message.Request.CompilationRequest.Builder builder = JpsRemoteProto.Message.Request.CompilationRequest.newBuilder().setCommandType(
      command);
    builder.setProjectId(project);
    if (modules.size() > 0) {
      builder.addAllModuleName(modules);
    }
    if (paths.size() > 0) {
      builder.addAllFilePath(paths);
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

  public static JpsRemoteProto.Message.Request createFSEvent(String projectPath, Collection<String> changedPaths, Collection<String> deletedPaths) {
    final JpsRemoteProto.Message.Request.FSEvent.Builder builder = JpsRemoteProto.Message.Request.FSEvent.newBuilder();
    builder.setProjectId(projectPath);
    builder.addAllChangedPaths(changedPaths);
    builder.addAllDeletedPaths(deletedPaths);
    return JpsRemoteProto.Message.Request.newBuilder().setRequestType(JpsRemoteProto.Message.Request.Type.FS_EVENT).setFsEvent(builder.build()).build();
  }

  public static JpsRemoteProto.Message.Request createSetupRequest(final Map<String, String> pathVars, List<GlobalLibrary> sdkAndLibs) {
    final JpsRemoteProto.Message.Request.SetupCommand.Builder cmdBuilder = JpsRemoteProto.Message.Request.SetupCommand.newBuilder();

    if (!pathVars.isEmpty()) {
      for (Map.Entry<String, String> entry : pathVars.entrySet()) {
        final String var = entry.getKey();
        final String value = entry.getValue();
        if (var != null && value != null) {
          final JpsRemoteProto.Message.Request.SetupCommand.PathVariable.Builder pathVarBuilder =
            JpsRemoteProto.Message.Request.SetupCommand.PathVariable.newBuilder();
          cmdBuilder.addPathVariable(pathVarBuilder.setName(var).setValue(value).build());
        }
      }
    }

    if (!sdkAndLibs.isEmpty()) {
      for (GlobalLibrary lib : sdkAndLibs) {
        final JpsRemoteProto.Message.Request.SetupCommand.GlobalLibrary.Builder libBuilder =
          JpsRemoteProto.Message.Request.SetupCommand.GlobalLibrary.newBuilder();
        libBuilder.setName(lib.getName()).addAllPath(lib.getPaths());
        if (lib instanceof SdkLibrary) {
          libBuilder.setHomePath(((SdkLibrary)lib).getHomePath());
        }
        cmdBuilder.addGlobalLibrary(libBuilder.build());
      }
    }

    return JpsRemoteProto.Message.Request.newBuilder().setRequestType(JpsRemoteProto.Message.Request.Type.SETUP_COMMAND).setSetupCommand(cmdBuilder.build()).build();
  }

  public static JpsRemoteProto.Message.Response createBuildStartedEvent(@Nullable String description) {
    return createBuildEvent(JpsRemoteProto.Message.Response.BuildEvent.Type.BUILD_STARTED, description, null, null);
  }

  public static JpsRemoteProto.Message.Response createBuildCompletedEvent(@Nullable String description, final JpsRemoteProto.Message.Response.BuildEvent.Status status) {
    return createBuildEvent(JpsRemoteProto.Message.Response.BuildEvent.Type.BUILD_COMPLETED, description, status, null);
  }

  public static JpsRemoteProto.Message.Response createCommandCompletedEvent(@Nullable String description) {
    return createBuildEvent(JpsRemoteProto.Message.Response.BuildEvent.Type.COMMAND_COMPLETED, description, null, null);
  }

  public static JpsRemoteProto.Message.Response createFileGeneratedEvent(final Collection<Pair<String, String>> paths) {
    return createBuildEvent(JpsRemoteProto.Message.Response.BuildEvent.Type.FILES_GENERATED, null, null, paths);
  }

  public static JpsRemoteProto.Message.Response createBuildEvent(final JpsRemoteProto.Message.Response.BuildEvent.Type type, @Nullable String description, final JpsRemoteProto.Message.Response.BuildEvent.Status status, Collection<Pair<String, String>> generatedPaths) {
    final JpsRemoteProto.Message.Response.BuildEvent.Builder builder = JpsRemoteProto.Message.Response.BuildEvent.newBuilder().setEventType(type);
    if (description != null) {
      builder.setDescription(description);
    }
    if (status != null) {
      builder.setCompletionStatus(status);
    }
    if (generatedPaths != null) {
      for (Pair<String, String> pair : generatedPaths) {
        final JpsRemoteProto.Message.Response.BuildEvent.GeneratedFile.Builder fileBuilder = JpsRemoteProto.Message.Response.BuildEvent.GeneratedFile.newBuilder();
        final JpsRemoteProto.Message.Response.BuildEvent.GeneratedFile generatedFile = fileBuilder.setOutputRoot(pair.first).setRelativePath(pair.second).build();
        builder.addGeneratedFiles(generatedFile);
      }
    }
    return JpsRemoteProto.Message.Response.newBuilder().setResponseType(JpsRemoteProto.Message.Response.Type.BUILD_EVENT).setBuildEvent(builder.build()).build();
  }

  public static JpsRemoteProto.Message.Response createCompileProgressMessageResponse(String text, float done) {
    return createCompileMessageResponse(BuildMessage.Kind.PROGRESS, text, null, -1L, -1L, -1L, -1, -1, done);
  }

  public static JpsRemoteProto.Message.Response createCompileMessageResponse(final BuildMessage.Kind kind,
                                                                             String text,
                                                                             String path,
                                                                             long beginOffset, long endOffset, long offset, long line,
                                                                             long column, float done) {

    final JpsRemoteProto.Message.Response.CompileMessage.Builder builder = JpsRemoteProto.Message.Response.CompileMessage.newBuilder();
    switch (kind) {
      case ERROR:
        builder.setKind(JpsRemoteProto.Message.Response.CompileMessage.Kind.ERROR);
        break;
      case WARNING:
        builder.setKind(JpsRemoteProto.Message.Response.CompileMessage.Kind.WARNING);
        break;
      case INFO:
        builder.setKind(JpsRemoteProto.Message.Response.CompileMessage.Kind.INFO);
        break;
      default:
        builder.setKind(JpsRemoteProto.Message.Response.CompileMessage.Kind.PROGRESS);
    }
    if (text != null) {
      builder.setText(text);
    }
    if (path != null) {
      builder.setSourceFilePath(path);
    }
    if (beginOffset >= 0L) {
      builder.setProblemBeginOffset(beginOffset);
    }
    if (endOffset >= 0L) {
      builder.setProblemEndOffset(endOffset);
    }
    if (offset >= 0L) {
      builder.setProblemLocationOffset(offset);
    }
    if (line > 0L) {
      builder.setLine(line);
    }
    if (column > 0L) {
      builder.setColumn(column);
    }
    if (done >= 0.0f) {
      builder.setDone(done);
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
