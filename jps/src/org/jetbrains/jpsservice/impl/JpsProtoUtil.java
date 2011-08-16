package org.jetbrains.jpsservice.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jpsservice.JpsRemoteProto;

import java.util.List;
import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/15/11
 */
public class JpsProtoUtil {

  public static JpsRemoteProto.Message.Request createCompileRequest(JpsRemoteProto.Message.Request.CompileRequest.Command command, String projectId, @Nullable List<String> modules) {
    final JpsRemoteProto.Message.Request.CompileRequest.Builder compilation = JpsRemoteProto.Message.Request.CompileRequest.newBuilder();
    compilation.setCommand(command);
    compilation.setProjectId(projectId);
    if (modules != null) {
      compilation.addAllModuleName(modules);
    }
    return JpsRemoteProto.Message.Request.newBuilder().setCompileRequest(compilation.build()).build();
  }


  public static JpsRemoteProto.Message.Request createStatusRequest(JpsRemoteProto.Message.UUID sessionId) {
    final JpsRemoteProto.Message.Request.StatusRequest.Builder status = JpsRemoteProto.Message.Request.StatusRequest.newBuilder();
    status.setSessionId(sessionId);
    return JpsRemoteProto.Message.Request.newBuilder().setStatusRequest(status.build()).build();
  }

  public static JpsRemoteProto.Message.Response createCompileResponse(final JpsRemoteProto.Message.UUID sessionId) {
    final JpsRemoteProto.Message.Response.CompileResponse.Builder compilation = JpsRemoteProto.Message.Response.CompileResponse.newBuilder();
    compilation.setSessionId(sessionId);
    return JpsRemoteProto.Message.Response.newBuilder().setCompileResponse(compilation.build()).build();
  }

  public static JpsRemoteProto.Message.Response createStatusResponse(final JpsRemoteProto.Message.UUID sessionId) {
    final JpsRemoteProto.Message.Response.StatusResponse.Builder status = JpsRemoteProto.Message.Response.StatusResponse.newBuilder();

    status.setSessionId(sessionId);
    status.setIsRunning(true);
    status.setCompiledModuleName("some_module");
    status.setStatusText("status_text");
    status.addCompilerMessage(createCompilerMessage(
      JpsRemoteProto.Message.Response.StatusResponse.CompilerMessage.Kind.INFO, "compile_message_text1", "path/to/source", 10, 20)
    );
    status.addCompilerMessage(createCompilerMessage(
      JpsRemoteProto.Message.Response.StatusResponse.CompilerMessage.Kind.INFO, "compile_message_text2", "path/to/source2", 20, 30)
    );

    return JpsRemoteProto.Message.Response.newBuilder().setStatusResponse(status.build()).build();
  }

  public static JpsRemoteProto.Message.UUID createProtoUUID(UUID uuid) {
    return JpsRemoteProto.Message.UUID.newBuilder().setMostSigBits(uuid.getMostSignificantBits()).setLeastSigBits(uuid.getLeastSignificantBits()).build();
  }
  public static UUID toJavaUUID(JpsRemoteProto.Message.UUID uuid) {
    return new UUID(uuid.getMostSigBits(), uuid.getLeastSigBits());
  }

  public static JpsRemoteProto.Message.Response.StatusResponse.CompilerMessage createCompilerMessage(final JpsRemoteProto.Message.Response.StatusResponse.CompilerMessage.Kind kind, @NotNull final String text, @Nullable final String path, final int line, final int column) {
    final JpsRemoteProto.Message.Response.StatusResponse.CompilerMessage.Builder builder = JpsRemoteProto.Message.Response.StatusResponse.CompilerMessage.newBuilder();
    builder.setKind(kind);
    builder.setText(text);
    if (path != null) {
      builder.setSourceFilePath(path);
    }
    if (line >= 0) {
      builder.setLine(line);
    }
    if (column >= 0) {
      builder.setColumn(column);
    }
    return builder.build();
  }

  public static JpsRemoteProto.Message.Failure createFailure(final String description) {
    return JpsRemoteProto.Message.Failure.newBuilder().setDescription(description).build();
  }
}
