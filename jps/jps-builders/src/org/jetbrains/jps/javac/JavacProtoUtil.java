/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.javac;

import com.google.protobuf.ByteString;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.BinaryContent;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/23/12
 */
public class JavacProtoUtil {

  public static JavacRemoteProto.Message.Request createCancelRequest() {
    return JavacRemoteProto.Message.Request.newBuilder().setRequestType(JavacRemoteProto.Message.Request.Type.CANCEL).build();
  }

  public static JavacRemoteProto.Message.Request createShutdownRequest() {
    return JavacRemoteProto.Message.Request.newBuilder().setRequestType(JavacRemoteProto.Message.Request.Type.SHUTDOWN).build();
  }

  public static JavacRemoteProto.Message.Request createCompilationRequest(List<String> options, Collection<File> files, Collection<File> classpath, Collection<File> platformCp, Collection<File> modulePath, Collection<File> sourcePath, Map<File, Set<File>> outs) {
    final JavacRemoteProto.Message.Request.Builder builder = JavacRemoteProto.Message.Request.newBuilder();
    builder.setRequestType(JavacRemoteProto.Message.Request.Type.COMPILE);
    builder.addAllOption(options);
    for (File file : files) {
      builder.addFile(FileUtil.toSystemIndependentName(file.getPath()));
    }
    for (File file : classpath) {
      builder.addClasspath(FileUtil.toSystemIndependentName(file.getPath()));
    }
    for (File file : platformCp) {
      builder.addPlatformClasspath(FileUtil.toSystemIndependentName(file.getPath()));
    }
    for (File file : modulePath) {
      builder.addModulePath(FileUtil.toSystemIndependentName(file.getPath()));
    }
    for (File file : sourcePath) {
      builder.addSourcepath(FileUtil.toSystemIndependentName(file.getPath()));
    }
    for (Map.Entry<File, Set<File>> entry : outs.entrySet()) {
      final JavacRemoteProto.Message.Request.OutputGroup.Builder groupBuilder = JavacRemoteProto.Message.Request.OutputGroup.newBuilder();
      groupBuilder.setOutputRoot(FileUtil.toSystemIndependentName(entry.getKey().getPath()));
      for (File srcRoot : entry.getValue()) {
        groupBuilder.addSourceRoot(FileUtil.toSystemIndependentName(srcRoot.getPath()));
      }
      builder.addOutput(groupBuilder.build());
    }
    return builder.build();
  }


  public static JavacRemoteProto.Message.Response createOutputObjectResponse(OutputFileObject fileObject) {
    final JavacRemoteProto.Message.Response.OutputObject.Builder msgBuilder = JavacRemoteProto.Message.Response.OutputObject.newBuilder();

    msgBuilder.setKind(convertKind(fileObject.getKind()));
    msgBuilder.setFilePath(FileUtil.toSystemIndependentName(fileObject.getFile().getPath()));
    final BinaryContent content = fileObject.getContent();
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
    final URI srcUri = fileObject.getSourceUri();
    if (srcUri != null) {
      msgBuilder.setSourceUri(srcUri.toString());
    }

    final JavacRemoteProto.Message.Response.Builder builder = JavacRemoteProto.Message.Response.newBuilder();
    builder.setResponseType(JavacRemoteProto.Message.Response.Type.OUTPUT_OBJECT).setOutputObject(msgBuilder.build());

    return builder.build();
  }

  public static JavacRemoteProto.Message.Response createSourceFileLoadedResponse(File srcFile) {

    final JavacRemoteProto.Message.Response.OutputObject outObjMsg = JavacRemoteProto.Message.Response.OutputObject.newBuilder()
      .setKind(convertKind(JavaFileObject.Kind.SOURCE)).setFilePath(FileUtil.toSystemIndependentName(srcFile.getPath())).build();

    final JavacRemoteProto.Message.Response.Builder builder = JavacRemoteProto.Message.Response.newBuilder();
    builder.setResponseType(JavacRemoteProto.Message.Response.Type.SRC_FILE_LOADED).setOutputObject(outObjMsg);

    return builder.build();
  }

  public static JavacRemoteProto.Message.Response createClassDataResponse(String className, Collection<String> imports, Collection<String> staticImports) {
    final JavacRemoteProto.Message.Response.ClassData.Builder msgBuilder = JavacRemoteProto.Message.Response.ClassData.newBuilder();
    msgBuilder.setClassName(className);
    if (!imports.isEmpty()) {
      msgBuilder.addAllImportStatement(imports);
    }
    if (!staticImports.isEmpty()) {
      msgBuilder.addAllStaticImport(imports);
    }
    final JavacRemoteProto.Message.Response.Builder builder = JavacRemoteProto.Message.Response.newBuilder();
    builder.setResponseType(JavacRemoteProto.Message.Response.Type.CLASS_DATA).setClassData(msgBuilder.build());
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

  public static JavacRemoteProto.Message.Response createBuildMessageResponse(Diagnostic<? extends JavaFileObject> diagnostic) {
    final JavacRemoteProto.Message.Response.CompileMessage.Builder msgBuilder = JavacRemoteProto.Message.Response.CompileMessage.newBuilder();

    msgBuilder.setKind(convertKind(diagnostic.getKind()));
    msgBuilder.setText(diagnostic.getMessage(Locale.US));

    final JavaFileObject source = diagnostic.getSource();
    URI srcUri = null;
    try {
      srcUri = source != null? source.toUri() : null;
    }
    catch (Exception ignored) {
    }
    if (srcUri != null) {
      msgBuilder.setSourceUri(srcUri.toString());
    }
    msgBuilder.setLine(diagnostic.getLineNumber());
    msgBuilder.setColumn(diagnostic.getColumnNumber());
    msgBuilder.setProblemBeginOffset(diagnostic.getStartPosition());
    msgBuilder.setProblemEndOffset(diagnostic.getEndPosition());
    msgBuilder.setProblemLocationOffset(diagnostic.getPosition());

    final JavacRemoteProto.Message.Response.Builder builder = JavacRemoteProto.Message.Response.newBuilder();
    builder.setResponseType(JavacRemoteProto.Message.Response.Type.BUILD_MESSAGE).setCompileMessage(msgBuilder.build());

    return builder.build();
  }

  public static JavacRemoteProto.Message.Response createRequestAckResponse() {
    return JavacRemoteProto.Message.Response.newBuilder().setResponseType(JavacRemoteProto.Message.Response.Type.REQUEST_ACK).build();
  }

  public static JavacRemoteProto.Message.Response createBuildCompletedResponse(boolean code) {
    final JavacRemoteProto.Message.Response.Builder builder = JavacRemoteProto.Message.Response.newBuilder();
    builder.setResponseType(JavacRemoteProto.Message.Response.Type.BUILD_COMPLETED).setCompletionStatus(code);
    return builder.build();
  }

  public static JavacRemoteProto.Message.Failure createFailure(String description, @Nullable Throwable ex) {
    final JavacRemoteProto.Message.Failure.Builder builder = JavacRemoteProto.Message.Failure.newBuilder();
    builder.setDescription(description);
    if (ex != null) {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ex.printStackTrace(new PrintStream(baos));
      builder.setStacktrace(new String(baos.toByteArray()));
    }
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
      case ERROR: return JavacRemoteProto.Message.Response.CompileMessage.Kind.ERROR;
      case MANDATORY_WARNING: return JavacRemoteProto.Message.Response.CompileMessage.Kind.MANDATORY_WARNING;
      case WARNING: return JavacRemoteProto.Message.Response.CompileMessage.Kind.WARNING;
      case NOTE: return JavacRemoteProto.Message.Response.CompileMessage.Kind.NOTE;
      default:
        return JavacRemoteProto.Message.Response.CompileMessage.Kind.OTHER;
    }
  }

}
