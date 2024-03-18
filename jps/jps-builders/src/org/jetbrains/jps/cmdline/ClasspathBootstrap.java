// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cmdline;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.protobuf.Message;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.openapi.application.ClassPathUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import com.intellij.tracing.Tracer;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.SystemProperties;
import com.intellij.util.lang.Xxh3;
import com.jgoodies.forms.layout.CellConstraints;
import com.thoughtworks.qdox.JavaProjectBuilder;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.NetUtil;
import kotlinx.metadata.jvm.JvmMetadataUtil;
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

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

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
    Message.class, // protobuf
  };

  private static final String[] REFLECTION_OPEN_PACKAGES = {
    // needed for jps core functioning
    "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",

    // needed for some lombok and google errorprone compiler versions to function
    "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED"
  };

  private static void addToClassPath(Class<?> aClass, Set<String> result) {
    Path path = PathManager.getJarForClass(aClass);
    if (path == null) {
      return;
    }

    final String pathString = path.toString();

    if (result.add(pathString) && pathString.endsWith("app.jar") && path.getFileName().toString().equals("app.jar")) {
      if (path.getParent().equals(Paths.get(PathManager.getLibPath()))) {
        LOG.error("Due to " + aClass.getName() + " requirement, inappropriate " + pathString + " is added to build process classpath");
      }
    }
  }

  private static void addToClassPath(Set<String> cp, @NotNull Class<?> @NotNull [] classes) {
    for (Class<?> aClass : classes) {
      addToClassPath(aClass, cp);
    }
  }

  public static @NotNull Collection<String> getBuildProcessApplicationClasspath() {
    // predictable order
    Set<String> cp = new LinkedHashSet<>();

    addToClassPath(BuildMain.class, cp);
    addToClassPath(ExternalJavacProcess.class, cp);  // intellij.platform.jps.build.javac.rt part
    addToClassPath(JavacReferenceCollector.class, cp);  // jps-javac-extension library

    // intellij.platform.util
    addToClassPath(cp, ClassPathUtil.getUtilClasses());

    ClassPathUtil.addKotlinStdlib(cp);
    addToClassPath(JvmMetadataUtil.class, cp);  // kotlin metadata parsing
    addToClassPath(cp, COMMON_REQUIRED_CLASSES);

    addToClassPath(ClassWriter.class, cp);  // asm
    addToClassPath(ClassVisitor.class, cp);  // asm-commons
    addToClassPath(RuntimeModuleRepository.class, cp); // intellij.platform.runtime.repository
    addToClassPath(JpsModel.class, cp);  // intellij.platform.jps.model
    addToClassPath(JpsModelImpl.class, cp);  // intellij.platform.jps.model.impl
    addToClassPath(JpsProjectLoader.class, cp);  // intellij.platform.jps.model.serialization
    addToClassPath(AlienFormFileException.class, cp);  // intellij.java.guiForms.compiler
    addToClassPath(GridConstraints.class, cp);  // intellij.java.guiForms.rt
    addToClassPath(CellConstraints.class, cp);  // jGoodies-forms
    cp.addAll(getInstrumentationUtilRoots());
    addToClassPath(IXMLBuilder.class, cp);  // nano-xml
    addToClassPath(JavaProjectBuilder.class, cp);  // QDox lightweight java parser
    addToClassPath(Gson.class, cp);  // gson
    addToClassPath(Xxh3.class, cp);
    // caffeine
    addToClassPath(Caffeine.class, cp);

    addToClassPath(cp, ArtifactRepositoryManager.getClassesFromDependencies());
    addToClassPath(Tracer.class, cp); // tracing infrastructure

    try {
      Class<?> cmdLineWrapper = Class.forName("com.intellij.rt.execution.CommandLineWrapper");
      addToClassPath(cmdLineWrapper, cp);  // idea_rt.jar
    }
    catch (Throwable ignored) { }

    return cp;
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
    // Important! All dependencies must be java 6 compatible (the oldest supported javac to be launched)
    final Set<File> cp = new LinkedHashSet<>();
    cp.add(getResourceFile(ExternalJavacProcess.class)); // self
    cp.add(getResourceFile(JavacReferenceCollector.class));  // jps-javac-extension library
    cp.add(getResourceFile(SystemInfoRt.class)); // util_rt

    for (Class<?> aClass : COMMON_REQUIRED_CLASSES) {
      cp.add(getResourceFile(aClass));
    }

    try {
      final Class<?> cmdLineWrapper = Class.forName("com.intellij.rt.execution.CommandLineWrapper");
      cp.add(getResourceFile(cmdLineWrapper));  // idea_rt.jar
    }
    catch (Throwable th) {
      LOG.info(th);
    }

    try {
      final String localJavaHome = FileUtilRt.toSystemIndependentName(SystemProperties.getJavaHome());
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
          String localJarPath = FileUtilRt.toSystemIndependentName(resourceFile.getPath());
          String relPath = FileUtilRt.getRelativePath(localJavaHome, localJarPath, '/');
          if (relPath != null) {
            if (relPath.contains("..")) {
              relPath = FileUtilRt.getRelativePath(FileUtilRt.toSystemIndependentName(new File(localJavaHome).getParent()), localJarPath, '/');
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

  public static @Nullable String getResourcePath(Class<?> aClass) {
    return PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
  }

  public static @Nullable File getResourceFile(Class<?> aClass) {
    final String resourcePath = getResourcePath(aClass);
    return resourcePath != null? new File(resourcePath) : null;
  }

  public static void configureReflectionOpenPackages(Consumer<? super String> paramConsumer) {
    for (String aPackage : REFLECTION_OPEN_PACKAGES) {
      paramConsumer.accept("--add-opens");
      paramConsumer.accept(aPackage);
    }
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