// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel;

import org.jetbrains.jps.javac.ExternalJavacMessageHandler;
import org.jetbrains.jps.javac.JavacProtoUtil;
import org.jetbrains.jps.javac.ModulePath;
import org.jetbrains.jps.javac.rpc.JavacRemoteProto;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class JavacProtoUtilTest {

  @Test
  public void testClassDataResponseAppliesArguments() {
    List<String> imports = List.of("java.util.List", "java.io.File");
    List<String> staticImports = List.of("java.util.Objects.requireNonNull", "java.lang.Math.max");

    JavacRemoteProto.Message.Response response = JavacProtoUtil.createClassDataResponse("org.sample.Main", imports, staticImports);

    assertEquals(JavacRemoteProto.Message.Response.Type.CLASS_DATA, response.getResponseType());
    JavacRemoteProto.Message.Response.ClassData classData = response.getClassData();
    assertEquals("org.sample.Main", classData.getClassName());
    assertEquals(imports, classData.getImportStatementList());
    assertEquals(staticImports, classData.getStaticImportList());
  }

  @Test
  public void testClassDataResponseWithOnlyStaticImports() {
    List<String> staticImports = List.of("java.util.Collections.emptyList");

    JavacRemoteProto.Message.Response.ClassData classData =
      JavacProtoUtil.createClassDataResponse("org.sample.Util", List.of(), staticImports).getClassData();

    assertEquals(List.of(), classData.getImportStatementList());
    assertEquals(staticImports, classData.getStaticImportList());
  }

  @Test
  public void testCompilationRequestAppliesArguments() {
    ModulePath modulePath = ModulePath.newBuilder()
      .add("lib.module", new File("mp/lib-module.jar"))
      .add(null, new File("mp/automatic.jar"))
      .create();

    JavacRemoteProto.Message.Request request = JavacProtoUtil.createCompilationRequest(
      List.of("-g", "-deprecation"),
      List.of(new File("src/A.java"), new File("src/B.java")),
      List.of(new File("cp/dep.jar")),
      List.of(new File("boot/rt.jar")),
      modulePath,
      List.of(new File("upgrade/xml.jar")),
      List.of(new File("srcpath")),
      Map.of(new File("out/production"), Set.of(new File("src"))),
      ExternalJavacMessageHandler.WslSupport.DIRECT
    );

    assertEquals(JavacRemoteProto.Message.Request.Type.COMPILE, request.getRequestType());
    assertEquals(List.of("-g", "-deprecation"), request.getOptionList());
    assertEquals(List.of("src/A.java", "src/B.java"), request.getFileList());
    assertEquals(List.of("cp/dep.jar"), request.getClasspathList());
    assertEquals(List.of("boot/rt.jar"), request.getPlatformClasspathList());
    assertEquals(List.of("mp/lib-module.jar", "mp/automatic.jar"), request.getModulePathList());
    assertEquals(Map.of("mp/lib-module.jar", "lib.module"), request.getModuleNamesMap());
    assertEquals(List.of("upgrade/xml.jar"), request.getUpgradeModulePathList());
    assertEquals(List.of("srcpath"), request.getSourcepathList());
    assertEquals(1, request.getOutputCount());
    JavacRemoteProto.Message.Request.OutputGroup output = request.getOutput(0);
    assertEquals("out/production", output.getOutputRoot());
    assertEquals(List.of("src"), output.getSourceRootList());
  }

  @Test
  public void testCustomDataResponseAppliesArguments() {
    JavacRemoteProto.Message.Response response = JavacProtoUtil.createCustomDataResponse("my-plugin", "my-data", new byte[]{1, 2, 3});

    assertEquals(JavacRemoteProto.Message.Response.Type.CUSTOM_OUTPUT_OBJECT, response.getResponseType());
    JavacRemoteProto.Message.Response.OutputObject outputObject = response.getOutputObject();
    // CUSTOM_OUTPUT_OBJECT repurposes OutputObject fields: filePath carries the plugin id, className the data name
    assertEquals("my-plugin", outputObject.getFilePath());
    assertEquals("my-data", outputObject.getClassName());
    assertEquals(JavacRemoteProto.Message.Response.OutputObject.Kind.OTHER, outputObject.getKind());
  }

  @Test
  public void testUuidConversionRoundTrip() {
    UUID uuid = UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6");

    JavacRemoteProto.Message.UUID protoUuid = JavacProtoUtil.toProtoUUID(uuid);
    assertEquals(uuid.getMostSignificantBits(), protoUuid.getMostSigBits());
    assertEquals(uuid.getLeastSignificantBits(), protoUuid.getLeastSigBits());
    assertEquals(uuid, JavacProtoUtil.fromProtoUUID(protoUuid));

    JavacRemoteProto.Message message = JavacProtoUtil.toMessage(uuid, JavacProtoUtil.createCancelRequest());
    assertEquals(JavacRemoteProto.Message.Type.REQUEST, message.getMessageType());
    assertEquals(uuid, JavacProtoUtil.fromProtoUUID(message.getSessionId()));
    assertEquals(JavacRemoteProto.Message.Request.Type.CANCEL, message.getRequest().getRequestType());
  }
}
