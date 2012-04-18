package org.jetbrains.jps.api;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.messages.BuildMessage;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/17/12
 */
public class CmdlineProtoUtil {
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

  public static CmdlineRemoteProto.Message.BuilderMessage createCompileProgressMessageResponse(String text, float done) {
    return createCompileMessageResponse(BuildMessage.Kind.PROGRESS, text, null, -1L, -1L, -1L, -1, -1, done);
  }

  public static CmdlineRemoteProto.Message.BuilderMessage createCompileMessageResponse(final BuildMessage.Kind kind,
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

  public static CmdlineRemoteProto.Message toMessage(UUID sessionId, CmdlineRemoteProto.Message.Failure failure) {
    return CmdlineRemoteProto.Message.newBuilder().setSessionId(toProtoUUID(sessionId)).setType(CmdlineRemoteProto.Message.Type.FAILURE).setFailure(failure).build();
  }

  private static CmdlineRemoteProto.Message.UUID toProtoUUID(UUID sessionId) {
    final CmdlineRemoteProto.Message.UUID.Builder uuidBuilder = CmdlineRemoteProto.Message.UUID.newBuilder();
    return uuidBuilder.setMostSigBits(sessionId.getMostSignificantBits()).setLeastSigBits(sessionId.getLeastSignificantBits()).build();
  }
}
