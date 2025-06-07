// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import com.google.protobuf.ByteString;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.javac.rpc.JavacRemoteProto;

import javax.tools.Diagnostic;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.util.*;

@ApiStatus.Internal
public final class JavacProtoUtil {
  public static JavacRemoteProto.Message.Request createCancelRequest() {
    return JavacRemoteProto.Message.Request.newBuilder().setRequestType(JavacRemoteProto.Message.Request.Type.CANCEL).build();
  }

  public static JavacRemoteProto.Message.Request createShutdownRequest() {
    return JavacRemoteProto.Message.Request.newBuilder().setRequestType(JavacRemoteProto.Message.Request.Type.SHUTDOWN).build();
  }

  public static JavacRemoteProto.Message.Request createCompilationRequest(Iterable<String> options,
                                                                          Iterable<? extends File> files,
                                                                          Iterable<? extends File> classpath,
                                                                          Iterable<? extends File> platformCp,
                                                                          ModulePath modulePath,
                                                                          Iterable<? extends File> upgradeModulePath,
                                                                          Iterable<? extends File> sourcePath,
                                                                          Map<File, Set<File>> outs,
                                                                          ExternalJavacMessageHandler.WslSupport wslSupport) {
    final JavacRemoteProto.Message.Request.Builder builder = JavacRemoteProto.Message.Request.newBuilder();
    builder.setRequestType(JavacRemoteProto.Message.Request.Type.COMPILE);
    builder.addAllOption(options);
    for (File file : files) {
      builder.addFile(wslSupport.convertPath(file.getPath()));
    }
    for (File file : classpath) {
      builder.addClasspath(wslSupport.convertPath(file.getPath()));
    }
    for (File file : platformCp) {
      builder.addPlatformClasspath(wslSupport.convertPath(file.getPath()));
    }
    for (File file : modulePath.getPath()) {
      final String pathEntry = wslSupport.convertPath(file.getPath());
      builder.addModulePath(pathEntry);
      final String moduleName = modulePath.getModuleName(file);
      if (moduleName != null) {
        builder.putModuleNames(pathEntry, moduleName);
      }
    }
    for (File file : upgradeModulePath) {
      builder.addUpgradeModulePath(wslSupport.convertPath(file.getPath()));
    }
    for (File file : sourcePath) {
      builder.addSourcepath(wslSupport.convertPath(file.getPath()));
    }
    for (Map.Entry<File, Set<File>> entry : outs.entrySet()) {
      final JavacRemoteProto.Message.Request.OutputGroup.Builder groupBuilder = JavacRemoteProto.Message.Request.OutputGroup.newBuilder();
      groupBuilder.setOutputRoot(wslSupport.convertPath(entry.getKey().getPath()));
      for (File srcRoot : entry.getValue()) {
        groupBuilder.addSourceRoot(wslSupport.convertPath(srcRoot.getPath()));
      }
      builder.addOutput(groupBuilder.build());
    }
    return builder.build();
  }


  public static JavacRemoteProto.Message.Response createOutputObjectResponse(OutputFileObject fileObject) {
    final JavacRemoteProto.Message.Response.OutputObject.Builder msgBuilder = JavacRemoteProto.Message.Response.OutputObject.newBuilder();

    msgBuilder.setKind(convertKind(fileObject.getKind()));
    msgBuilder.setFilePath(DefaultFileOperations.toSystemIndependentName(fileObject.getFile().getPath()));
    msgBuilder.setIsGenerated(fileObject.isGenerated());

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
      msgBuilder.setOutputRoot(DefaultFileOperations.toSystemIndependentName(outputRoot.getPath()));
    }
    final String relativePath = fileObject.getRelativePath();
    if (relativePath != null) {
      msgBuilder.setRelativePath(relativePath);
    }
    for (URI uri : fileObject.getSourceUris()) {
      msgBuilder.addSourceUri(uri.toString());
    }
    final JavaFileManager.Location location = fileObject.getLocation();
    if (location != null) {
      msgBuilder.setLocation(location.getName());
    }
    final JavacRemoteProto.Message.Response.Builder builder = JavacRemoteProto.Message.Response.newBuilder();
    builder.setResponseType(JavacRemoteProto.Message.Response.Type.OUTPUT_OBJECT).setOutputObject(msgBuilder.build());

    return builder.build();
  }

  public static JavacRemoteProto.Message.Response createCustomDataResponse(String pluginId, String dataName, byte[] data) {
    final JavacRemoteProto.Message.Response.OutputObject outObjMsg = JavacRemoteProto.Message.Response.OutputObject.newBuilder()
      .setKind(JavacRemoteProto.Message.Response.OutputObject.Kind.OTHER)
      .setIsGenerated(false)
      .setFilePath(pluginId)
      .setClassName(dataName)
      .setContent(ByteString.copyFrom(data))
      .build();
    return JavacRemoteProto.Message.Response.newBuilder()
      .setResponseType(JavacRemoteProto.Message.Response.Type.CUSTOM_OUTPUT_OBJECT)
      .setOutputObject(outObjMsg)
      .build();
  }

  public static JavacRemoteProto.Message.Response createSourceFileLoadedResponse(File srcFile) {
    final JavacRemoteProto.Message.Response.OutputObject outObjMsg = JavacRemoteProto.Message.Response.OutputObject.newBuilder()
      .setKind(convertKind(JavaFileObject.Kind.SOURCE)).setIsGenerated(false).setFilePath(DefaultFileOperations.toSystemIndependentName(srcFile.getPath())).build();

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