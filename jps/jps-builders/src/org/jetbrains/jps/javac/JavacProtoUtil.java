package org.jetbrains.jps.javac;

import com.google.protobuf.ByteString;
import com.intellij.openapi.util.io.FileUtil;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/23/12
 */
public class JavacProtoUtil {

  public static JavacRemoteProto.Message.Response createOutputObjectResponse(OutputFileObject fileObject) {
    final JavacRemoteProto.Message.Response.OutputObject.Builder msgBuilder = JavacRemoteProto.Message.Response.OutputObject.newBuilder();

    msgBuilder.setKind(convertKind(fileObject.getKind()));
    msgBuilder.setFilePath(FileUtil.toSystemIndependentName(fileObject.getFile().getPath()));
    final OutputFileObject.Content content = fileObject.getContent();
    if (content != null) {
      msgBuilder.setContent(ByteString.copyFrom(content.getBuffer(), content.getOffset(), content.getLength()));
    }
    final String className = fileObject.getClassName();
    if (className != null) {
      msgBuilder.setClassName(className);
    }
    final File outputRoot = fileObject.getOutputRoot();
    if (outputRoot != null) {
      msgBuilder.setOutputRoot(FileUtil.toSystemIndependentName(outputRoot.getPath()));
    }
    final String relativePath = fileObject.getRelativePath();
    if (relativePath != null) {
      msgBuilder.setRelativePath(relativePath);
    }
    final File sourceFile = fileObject.getSourceFile();
    if (sourceFile != null) {
      msgBuilder.setSourcePath(FileUtil.toSystemIndependentName(sourceFile.getPath()));
    }

    final JavacRemoteProto.Message.Response.Builder builder = JavacRemoteProto.Message.Response.newBuilder();
    builder.setResponseType(JavacRemoteProto.Message.Response.Type.OUTPUT_OBJECT).setOutputObject(msgBuilder.build());

    return builder.build();
  }

  public static JavacRemoteProto.Message.Response createStdOutputResponse(String text) {
    final JavacRemoteProto.Message.Response.CompileMessage.Builder msgBuilder = JavacRemoteProto.Message.Response.CompileMessage.newBuilder();
    msgBuilder.setKind(JavacRemoteProto.Message.Response.CompileMessage.Kind.STD_OUT);
    msgBuilder.setText(text);
    final JavacRemoteProto.Message.Response.Builder builder = JavacRemoteProto.Message.Response.newBuilder();
    builder.setResponseType(JavacRemoteProto.Message.Response.Type.BUILD_MESSAGE).setCompileMessage(msgBuilder.build());

    return builder.build();
  }

  public static JavacRemoteProto.Message.Response createBuildMessageResponse(Diagnostic.Kind kind, String text, final String srcPath, final long line, final long column, final long beginOffset, final long endOffset) {
    final JavacRemoteProto.Message.Response.CompileMessage.Builder msgBuilder = JavacRemoteProto.Message.Response.CompileMessage.newBuilder();
    msgBuilder.setKind(convertKind(kind));
    msgBuilder.setText(text);
    msgBuilder.setSourceFilePath(srcPath);
    msgBuilder.setLine(line);
    msgBuilder.setColumn(column);
    msgBuilder.setProblemBeginOffset(beginOffset);
    msgBuilder.setProblemEndOffset(endOffset);

    final JavacRemoteProto.Message.Response.Builder builder = JavacRemoteProto.Message.Response.newBuilder();
    builder.setResponseType(JavacRemoteProto.Message.Response.Type.BUILD_MESSAGE).setCompileMessage(msgBuilder.build());

    return builder.build();
  }

  public static JavacRemoteProto.Message.Response createBuildCompletedResponse(boolean code) {
    final JavacRemoteProto.Message.Response.Builder builder = JavacRemoteProto.Message.Response.newBuilder();
    builder.setResponseType(JavacRemoteProto.Message.Response.Type.BUILD_COMPLETED).setCompletionStatus(code);
    return builder.build();
  }

  public static JavacRemoteProto.Message.Failure createFailure(String description) {
    final JavacRemoteProto.Message.Failure.Builder builder = JavacRemoteProto.Message.Failure.newBuilder();
    builder.setDescription(description);
    return builder.build();
  }

  public static JavacRemoteProto.Message toMessage(UUID requestId, JavacRemoteProto.Message.Request request) {
    return JavacRemoteProto.Message.newBuilder().setMessageType(JavacRemoteProto.Message.Type.REQUEST).setSessionId(toProtoUUID(requestId)).setRequest(request).build();
  }

  public static JavacRemoteProto.Message toMessage(UUID requestId, JavacRemoteProto.Message.Response response) {
    return JavacRemoteProto.Message.newBuilder().setMessageType(JavacRemoteProto.Message.Type.RESPONSE).setSessionId(toProtoUUID(requestId)).setResponse(response).build();
  }

  public static JavacRemoteProto.Message toMessage(UUID requestId, JavacRemoteProto.Message.Failure failure) {
    return JavacRemoteProto.Message.newBuilder().setMessageType(JavacRemoteProto.Message.Type.FAILURE).setSessionId(toProtoUUID(requestId)).setFailure(failure).build();
  }

  public static JavacRemoteProto.Message.UUID toProtoUUID(UUID requestId) {
    return JavacRemoteProto.Message.UUID.newBuilder().setMostSigBits(requestId.getMostSignificantBits()).setLeastSigBits(requestId.getLeastSignificantBits()).build();
  }
  public static UUID fromProtoUUID(JavacRemoteProto.Message.UUID requestId) {
    return new UUID(requestId.getMostSigBits(), requestId.getLeastSigBits());
  }

  private static JavacRemoteProto.Message.Response.OutputObject.Kind convertKind(JavaFileObject.Kind kind) {
    switch (kind) {
      case CLASS:
        return JavacRemoteProto.Message.Response.OutputObject.Kind.CLASS;
      case SOURCE:
        return JavacRemoteProto.Message.Response.OutputObject.Kind.SOURCE;
      case HTML:
        return JavacRemoteProto.Message.Response.OutputObject.Kind.HTML;
      default:
        return JavacRemoteProto.Message.Response.OutputObject.Kind.OTHER;
    }
  }
  private static JavacRemoteProto.Message.Response.CompileMessage.Kind convertKind(Diagnostic.Kind kind) {
    switch (kind) {
      case ERROR:
        return JavacRemoteProto.Message.Response.CompileMessage.Kind.ERROR;
      case MANDATORY_WARNING:
      case WARNING:
      case NOTE:
        return JavacRemoteProto.Message.Response.CompileMessage.Kind.WARNING;
      default:
        return JavacRemoteProto.Message.Response.CompileMessage.Kind.INFO;
    }
  }

}
