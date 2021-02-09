// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cmdline;

import com.google.gson.Gson;
import com.google.protobuf.Message;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.jgoodies.forms.layout.CellConstraints;
import com.thoughtworks.qdox.JavaProjectBuilder;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.NetUtil;
import kotlin.Pair;
import net.n3.nanoxml.IXMLBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.builders.impl.java.EclipseCompilerTool;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.builders.java.JavaSourceTransformer;
import org.jetbrains.jps.javac.ExternalJavacProcess;
import org.jetbrains.jps.javac.ast.JavacReferenceCollector;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.impl.JpsModelImpl;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import javax.tools.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class ClasspathBootstrap {
  private static final Logger LOG = Logger.getInstance(ClasspathBootstrap.class);

  private ClasspathBootstrap() { }

  private static final Class<?>[] COMMON_REQUIRED_CLASSES = {
    NetUtil.class, // netty common
    EventLoopGroup.class, // netty transport
    AddressResolverGroup.class, // netty resolver
    ByteBufAllocator.class, // netty buffer
    ProtobufDecoder.class,  // netty codec
  };


  private static final String DEFAULT_MAVEN_REPOSITORY_PATH = ".m2/repository";
  private static final String PROTOBUF_JAVA6_VERSION = "3.5.1";
  private static final String PROTOBUF_JAVA6_JAR_NAME = "protobuf-java-" + PROTOBUF_JAVA6_VERSION + ".jar";

  private static final String EXTERNAL_JAVAC_MODULE_NAME = "intellij.platform.jps.build.javac.rt.rpc";
  private static final String EXTERNAL_JAVAC_JAR_NAME = "jps-javac-rt-rpc.jar";

  public static List<String> getBuildProcessApplicationClasspath() {
    final Set<String> cp = new HashSet<>();

    cp.add(getResourcePath(BuildMain.class));
    cp.add(getResourcePath(ExternalJavacProcess.class));  // intellij.platform.jps.build.javac.rt part
    cp.add(getResourcePath(JavacReferenceCollector.class));  // jps-javac-extension library

    cp.addAll(PathManager.getUtilClassPath()); // intellij.platform.util

    for (Class<?> aClass : COMMON_REQUIRED_CLASSES) {
      cp.add(getResourcePath(aClass));
    }

    cp.add(getResourcePath(Message.class));  // protobuf
    cp.add(getResourcePath(ClassWriter.class));  // asm
    cp.add(getResourcePath(ClassVisitor.class));  // asm-commons
    cp.add(getResourcePath(JpsModel.class));  // intellij.platform.jps.model
    cp.add(getResourcePath(JpsModelImpl.class));  // intellij.platform.jps.model.impl
    cp.add(getResourcePath(JpsProjectLoader.class));  // intellij.platform.jps.model.serialization
    cp.add(getResourcePath(AlienFormFileException.class));  // intellij.java.guiForms.compiler
    cp.add(getResourcePath(GridConstraints.class));  // intellij.java.guiForms.rt
    cp.add(getResourcePath(CellConstraints.class));  // jGoodies-forms
    cp.addAll(getInstrumentationUtilRoots());
    cp.add(getResourcePath(IXMLBuilder.class));  // nano-xml
    cp.add(getResourcePath(JavaProjectBuilder.class));  // QDox lightweight java parser
    cp.add(getResourcePath(Gson.class));  // gson

    cp.add(getResourcePath(Pair.class)); // kotlin-stdlib
    cp.add(PathManager.getResourceRoot(ClasspathBootstrap.class, "/kotlin/jdk7/AutoCloseableKt.class")); // kotlin-stdlib-jdk7
    cp.add(PathManager.getResourceRoot(ClasspathBootstrap.class, "/kotlin/streams/jdk8/StreamsKt.class")); // kotlin-stdlib-jdk8

    cp.addAll(ContainerUtil.map(ArtifactRepositoryManager.getClassesFromDependencies(), ClasspathBootstrap::getResourcePath));

    try {
      final Class<?> cmdLineWrapper = Class.forName("com.intellij.rt.execution.CommandLineWrapper");
      cp.add(getResourcePath(cmdLineWrapper));  // idea_rt.jar
    }
    catch (Throwable ignored) { }

    return new ArrayList<>(cp);
  }

  public static void appendJavaCompilerClasspath(Collection<? super String> cp, boolean includeEcj) {
    if (includeEcj) {
      File file = EclipseCompilerTool.findEcjJarFile();
      if (file != null) {
        cp.add(file.getAbsolutePath());
      }
    }
  }

  public static List<File> getExternalJavacProcessClasspath(String sdkHome, JavaCompilingTool compilingTool) {
    final Set<File> cp = new LinkedHashSet<>();
    cp.add(getResourceFile(ExternalJavacProcess.class)); // self
    cp.add(getResourceFile(JavacReferenceCollector.class));  // jps-javac-extension library

    // util
    for (String path : PathManager.getUtilClassPath()) {
      cp.add(new File(path));
    }

    for (Class<?> aClass : COMMON_REQUIRED_CLASSES) {
      cp.add(getResourceFile(aClass));
    }
    addExternalJavacRpcClasspath(cp);

    try {
      final Class<?> cmdLineWrapper = Class.forName("com.intellij.rt.execution.CommandLineWrapper");
      cp.add(getResourceFile(cmdLineWrapper));  // idea_rt.jar
    }
    catch (Throwable th) {
      LOG.info(th);
    }

    try {
      final String localJavaHome = FileUtil.toSystemIndependentName(SystemProperties.getJavaHome());
      // sdkHome is not the same as the sdk used to run this process
      final File candidate = new File(sdkHome, "lib/tools.jar");
      if (candidate.exists()) {
        cp.add(candidate);
      }
      else {
        // last resort
        final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
        Class<?> compilerClass;
        if (systemCompiler != null) {
          compilerClass = systemCompiler.getClass();
        }
        else {
          compilerClass = Class.forName("com.sun.tools.javac.api.JavacTool", false, ClasspathBootstrap.class.getClassLoader());
        }
        final File resourceFile = getResourceFile(compilerClass);
        if (resourceFile != null) {
          String localJarPath = FileUtil.toSystemIndependentName(resourceFile.getPath());
          String relPath = FileUtil.getRelativePath(localJavaHome, localJarPath, '/');
          if (relPath != null) {
            if (relPath.contains("..")) {
              relPath = FileUtil.getRelativePath(FileUtil.toSystemIndependentName(new File(localJavaHome).getParent()), localJarPath, '/');
            }
            if (relPath != null) {
              final File targetFile = new File(sdkHome, relPath);
              cp.add(targetFile);  // tools.jar
            }
          }
        }
      }
    }
    catch (Throwable th) {
      LOG.info(th);
    }

    cp.addAll(compilingTool.getAdditionalClasspath());

    for (JavaSourceTransformer t : JavaSourceTransformer.getTransformers()) {
      cp.add(getResourceFile(t.getClass()));
    }

    return new ArrayList<>(cp);
  }

  private static void addExternalJavacRpcClasspath(@NotNull Collection<File> cp) {
    Path rootPath = Paths.get(getResourcePath(ExternalJavacProcess.class));
    if (Files.isRegularFile(rootPath)) {
      // running regular installation
      Path rtDirPath = rootPath.resolveSibling("rt");
      cp.add(rtDirPath.resolve(EXTERNAL_JAVAC_JAR_NAME).toFile());
      cp.add(rtDirPath.resolve(PROTOBUF_JAVA6_JAR_NAME).toFile());
    }
    else {
      // running from sources or on the build server
      cp.add(rootPath.resolveSibling(EXTERNAL_JAVAC_MODULE_NAME).toFile());

      // take the library from the local maven repository
      File localRepositoryDir = getMavenLocalRepositoryDir();
      File protobufJava6File = new File(FileUtil.join(localRepositoryDir.getAbsolutePath(),
                               "com", "google", "protobuf", "protobuf-java", PROTOBUF_JAVA6_VERSION,
                               PROTOBUF_JAVA6_JAR_NAME));
      cp.add(protobufJava6File);
    }
  }

  private static @NotNull File getMavenLocalRepositoryDir() {
    final String userHome = System.getProperty("user.home", null);
    return userHome != null ? new File(userHome, DEFAULT_MAVEN_REPOSITORY_PATH) : new File(DEFAULT_MAVEN_REPOSITORY_PATH);
  }

  @Nullable
  public static String getResourcePath(Class<?> aClass) {
    return PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
  }

  @Nullable
  public static File getResourceFile(Class<?> aClass) {
    final String resourcePath = getResourcePath(aClass);
    return resourcePath != null? new File(resourcePath) : null;
  }

  private static List<String> getInstrumentationUtilRoots() {
    String instrumentationUtilPath = getResourcePath(NotNullVerifyingInstrumenter.class);
    File instrumentationUtil = new File(instrumentationUtilPath);
    if (instrumentationUtil.isDirectory()) {
      //running from sources: load classes from .../out/production/intellij.java.compiler.instrumentationUtil.java8
      return Arrays.asList(instrumentationUtilPath, new File(instrumentationUtil.getParentFile(), "intellij.java.compiler.instrumentationUtil.java8").getAbsolutePath());
    }
    else {
      //running from jars: intellij.java.compiler.instrumentationUtil.java8 is located in the same jar
      return Collections.singletonList(instrumentationUtilPath);
    }
  }

}