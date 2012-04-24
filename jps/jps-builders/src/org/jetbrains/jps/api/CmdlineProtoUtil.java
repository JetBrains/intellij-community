package org.jetbrains.jps.api;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.messages.BuildMessage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/17/12
 */
public class CmdlineProtoUtil {

  public static CmdlineRemoteProto.Message.ControllerMessage createMakeRequest(String project,
                                                                               Collection<String> modules,
                                                                               Collection<String> artifacts,
                                                                               final Map<String, String> userData,
                                                                               final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals) {
    return createBuildParametersMessage(CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.Type.MAKE, project, modules, artifacts,
                                userData, Collections.<String>emptyList(),
                                globals);
  }

  public static CmdlineRemoteProto.Message.ControllerMessage createForceCompileRequest(String project,
                                                                                       Collection<String> modules,
                                                                                       Collection<String> artifacts,
                                                                                       Collection<String> paths,
                                                                                       final Map<String, String> userData,
                                                                                       final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals) {
    return createBuildParametersMessage(CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.Type.FORCED_COMPILATION, project, modules,
                                        artifacts,
                                        userData, paths, globals);
  }

  public static CmdlineRemoteProto.Message.ControllerMessage createRebuildRequest(String project,
                                                                                  final Map<String, String> userData,
                                                                                  final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals) {
    return createBuildParametersMessage(CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.Type.REBUILD, project,
                                        Collections.<String>emptyList(),
                                        Collections.<String>emptyList(), userData, Collections.<String>emptyList(),
                                        globals);
  }

  public static CmdlineRemoteProto.Message.ControllerMessage createCleanRequest(String project,
                                                                                Collection<String> modules,
                                                                                Collection<String> artifacts,
                                                                                final Map<String, String> userData,
                                                                                final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals) {
    return createBuildParametersMessage(
      CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.Type.CLEAN, project, modules, artifacts, userData, Collections.<String>emptyList(),
      globals
    );
  }

  public static CmdlineRemoteProto.Message.ControllerMessage createBuildParametersMessage(CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.Type buildType,
                                                                                          String project,
                                                                                          Collection<String> modules,
                                                                                          Collection<String> artifacts,
                                                                                          Map<String, String> userData,
                                                                                          Collection<String> paths,
                                                                                          final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals) {
    final CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.Builder
      builder = CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.newBuilder();
    builder.setGlobalSettings(globals);
    builder.setBuildType(buildType);
    builder.setProjectId(project);
    if (!modules.isEmpty()) {
      builder.addAllModuleName(modules);
    }
    if (!artifacts.isEmpty()) {
      builder.addAllArtifactName(artifacts);
    }
    if (!userData.isEmpty()) {
      for (Map.Entry<String, String> entry : userData.entrySet()) {
        final String key = entry.getKey();
        final String value = entry.getValue();
        if (key != null && value != null) {
          builder.addBuilderParameter(createPair(key, value));
        }
      }
    }
    if (!paths.isEmpty()) {
      builder.addAllFilePath(paths);
    }
    return CmdlineRemoteProto.Message.ControllerMessage.newBuilder().setType(CmdlineRemoteProto.Message.ControllerMessage.Type.BUILD_PARAMETERS).setParamsMessage(builder.build()).build();
  }

  public static CmdlineRemoteProto.Message.KeyValuePair createPair(String key, String value) {
    return CmdlineRemoteProto.Message.KeyValuePair.newBuilder().setKey(key).setValue(value).build();
  }


  public static CmdlineRemoteProto.Message.Failure createFailure(String description, Throwable cause) {
    final CmdlineRemoteProto.Message.Failure.Builder builder = CmdlineRemoteProto.Message.Failure.newBuilder();
    builder.setDescription(description);
    if (cause != null) {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      cause.printStackTrace(new PrintStream(baos));
      builder.setStacktrace(new String(baos.toByteArray()));
    }
    return builder.build();
  }

  public static CmdlineRemoteProto.Message.ControllerMessage createCancelCommand() {
    return CmdlineRemoteProto.Message.ControllerMessage.newBuilder()
      .setType(CmdlineRemoteProto.Message.ControllerMessage.Type.CANCEL_BUILD_COMMAND).build();
  }

  public static CmdlineRemoteProto.Message.BuilderMessage createCompileProgressMessageResponse(String text, float done) {
    return createCompileMessage(BuildMessage.Kind.PROGRESS, text, null, -1L, -1L, -1L, -1, -1, done);
  }

  public static CmdlineRemoteProto.Message.BuilderMessage createCompileMessage(final BuildMessage.Kind kind,
                                                                               String text,
                                                                               String path,
                                                                               long beginOffset, long endOffset, long offset, long line,
                                                                               long column, float done) {

    final CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Builder builder = CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.newBuilder();
    switch (kind) {
      case ERROR:
        builder.setKind(CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.ERROR);
        break;
      case WARNING:
        builder.setKind(CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.WARNING);
        break;
      case INFO:
        builder.setKind(CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.INFO);
        break;
      default:
        builder.setKind(CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.PROGRESS);
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
    return CmdlineRemoteProto.Message.BuilderMessage.newBuilder().setType(CmdlineRemoteProto.Message.BuilderMessage.Type.COMPILE_MESSAGE).setCompileMessage(builder.build()).build();
  }

  public static CmdlineRemoteProto.Message.BuilderMessage createBuildCompletedEvent(@Nullable String description, final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status status) {
    return createBuildEvent(CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Type.BUILD_COMPLETED, description, status, null);
  }

  public static CmdlineRemoteProto.Message.BuilderMessage createFileGeneratedEvent(final Collection<Pair<String, String>> paths) {
    return createBuildEvent(CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Type.FILES_GENERATED, null, null, paths);
  }

  public static CmdlineRemoteProto.Message.BuilderMessage createBuildEvent(final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Type type, @Nullable String description, final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status status, Collection<Pair<String, String>> generatedPaths) {
    final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Builder builder = CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.newBuilder().setEventType(type);
    if (description != null) {
      builder.setDescription(description);
    }
    if (status != null) {
      builder.setCompletionStatus(status);
    }
    if (generatedPaths != null) {
      for (Pair<String, String> pair : generatedPaths) {
        final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.GeneratedFile.Builder fileBuilder = CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.GeneratedFile.newBuilder();
        final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.GeneratedFile generatedFile = fileBuilder.setOutputRoot(pair.first).setRelativePath(pair.second).build();
        builder.addGeneratedFiles(generatedFile);
      }
    }
    return CmdlineRemoteProto.Message.BuilderMessage.newBuilder().setType(CmdlineRemoteProto.Message.BuilderMessage.Type.BUILD_EVENT).setBuildEvent(builder.build()).build();
  }

  public static CmdlineRemoteProto.Message.BuilderMessage createParamRequest() {
    return CmdlineRemoteProto.Message.BuilderMessage.newBuilder().setType(CmdlineRemoteProto.Message.BuilderMessage.Type.PARAM_REQUEST).build();
  }

  public static CmdlineRemoteProto.Message toMessage(UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage builderMessage) {
    return CmdlineRemoteProto.Message.newBuilder().setSessionId(toProtoUUID(sessionId)).setType(CmdlineRemoteProto.Message.Type.BUILDER_MESSAGE).setBuilderMessage(builderMessage).build();
  }

  public static CmdlineRemoteProto.Message toMessage(UUID sessionId, CmdlineRemoteProto.Message.ControllerMessage builderMessage) {
    return CmdlineRemoteProto.Message.newBuilder().setSessionId(toProtoUUID(sessionId)).setType(CmdlineRemoteProto.Message.Type.CONTROLLER_MESSAGE).setControllerMessage(
      builderMessage).build();
  }

  public static CmdlineRemoteProto.Message toMessage(UUID sessionId, CmdlineRemoteProto.Message.Failure failure) {
    return CmdlineRemoteProto.Message.newBuilder().setSessionId(toProtoUUID(sessionId)).setType(CmdlineRemoteProto.Message.Type.FAILURE).setFailure(failure).build();
  }

  private static CmdlineRemoteProto.Message.UUID toProtoUUID(UUID sessionId) {
    final CmdlineRemoteProto.Message.UUID.Builder uuidBuilder = CmdlineRemoteProto.Message.UUID.newBuilder();
    return uuidBuilder.setMostSigBits(sessionId.getMostSignificantBits()).setLeastSigBits(sessionId.getLeastSignificantBits()).build();
  }

  public static CmdlineRemoteProto.Message.BuilderMessage createTimestampsMessage(Collection<Pair<File, Long>> toUpdate, Collection<File> toRemove) {
    final CmdlineRemoteProto.Message.BuilderMessage.TimestampsMessage.Builder tsBuilder =
      CmdlineRemoteProto.Message.BuilderMessage.TimestampsMessage.newBuilder();

    for (Pair<File, Long> p : toUpdate) {
      final File file = p.first;
      final Long timestamp = p.second;
      final CmdlineRemoteProto.Message.PathStampPair pair = CmdlineRemoteProto.Message.PathStampPair.newBuilder().setPath(
        FileUtil.toSystemIndependentName(file.getPath())).setStamp(timestamp).build();
      tsBuilder.addToUpdate(pair);
    }

    for (File file : toRemove) {
      tsBuilder.addToRemove(FileUtil.toSystemIndependentName(file.getPath()));
    }

    return CmdlineRemoteProto.Message.BuilderMessage.newBuilder().setType(CmdlineRemoteProto.Message.BuilderMessage.Type.TIMESTAMPS_EVENT).setTimestampsMessage(tsBuilder.build()).build();
  }
}
