// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import com.google.gson.Gson;
import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.psi.impl.compiled.ClsParsingUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool;
import org.jetbrains.jps.cmdline.BuildMain;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.javac.ExternalJavacProcess;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BytecodeCompatibilityTest extends JpsBuildTestCase {
  private static final int MIN_REQUIRED_JPS_BUILD_RUNTIME_VERSION = 11;
  private static final int MIN_REQUIRED_EXTERNAL_JAVAC_RUNTIME_VERSION = ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION;

  public void testJpsBytecode() throws IOException {

    List<URL> classpath = new ArrayList<>();
    for (String path : ClasspathBootstrap.getBuildProcessApplicationClasspath()) {
      classpath.add(new File(path).toURI().toURL());
    }

    ensureBytecodeVersion(List.of(

      BuildMain.class.getName(), // JPS Build
      ClassWriter.class.getName(),  // ASM
      JpsModel.class.getName(), // JPS Model
      Gson.class.getName()

    ), MIN_REQUIRED_JPS_BUILD_RUNTIME_VERSION, classpath);
  }

  public void testForkedJavacBytecode() throws IOException {

    List<URL> classpath = new ArrayList<>();
    for (File file : ClasspathBootstrap.getExternalJavacProcessClasspath(SystemProperties.getJavaHome(), new JavacCompilerTool())) {
      classpath.add(file.toURI().toURL());
    }

    ensureBytecodeVersion(List.of(
      ExternalJavacProcess.class.getName(),
      "com.google.protobuf.Message",   // protobuf
      "io.netty.util.NetUtil",  // netty common
      "io.netty.channel.EventLoopGroup", // netty transport
      "io.netty.resolver.AddressResolverGroup", // netty resolver
      "io.netty.buffer.ByteBufAllocator", // netty buffer
      "io.netty.handler.codec.ByteToMessageDecoder", // netty codec http
      "io.netty.handler.codec.protobuf.ProtobufDecoder", // netty codec protobuf
      "org.jetbrains.jps.javac.ast.JavacReferenceCollector", // reference collector
      SystemInfoRt.class.getName() // util_rt

      ), MIN_REQUIRED_EXTERNAL_JAVAC_RUNTIME_VERSION, classpath);
  }

  protected void ensureBytecodeVersion(List<String> classesToCheck, int maxJavaVersionFeatureLevel, Collection<URL> classpath) throws IOException {

    try (URLClassLoader loader = new URLClassLoader(classpath.toArray(URL[]::new), null)) {
      for (String clsName : classesToCheck) {
        try (InputStream stream = loader.getResourceAsStream(clsName.replace('.', '/') + ".class")) {
          assertNotNull("Required class " + clsName + " is not found in the classpath of the external javac process", stream);

          int majorBytecodeVersion = InstrumenterClassWriter.getClassFileVersion(new FailSafeClassReader(stream));

          JavaSdkVersion targetSdkVersion = ClsParsingUtil.getJdkVersionByBytecode(majorBytecodeVersion);
          assertNotNull(targetSdkVersion);

          assertTrue("Maximum allowed bytecode version for class " + clsName + " in the classpath of external javac process is " + maxJavaVersionFeatureLevel + " actual version is " + majorBytecodeVersion, targetSdkVersion.getMaxLanguageLevel().feature() <= maxJavaVersionFeatureLevel);
        }
      }
    }
  }

}
