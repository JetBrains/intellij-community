// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import com.google.protobuf.Message;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.SystemProperties;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.NetUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.builders.java.JavaSourceTransformer;
import org.jetbrains.jps.javac.ast.JavacReferenceCollector;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes the classpath of the forked javac process started by {@link ExternalJavacManager}.
 */
@ApiStatus.Internal
public final class ExternalJavacProcessClasspath {
  private static final Logger LOG = Logger.getInstance(ExternalJavacProcessClasspath.class);

  private static final Class<?>[] COMMON_REQUIRED_CLASSES = new Class[]{
    NetUtil.class, // netty common
    EventLoopGroup.class, // netty transport
    AddressResolverGroup.class, // netty resolver
    ByteBufAllocator.class, // netty buffer
    ByteToMessageDecoder.class, // netty codec http
    ProtobufDecoder.class,  // netty codec protobuf
    Message.class, // protobuf
  };

  private ExternalJavacProcessClasspath() { }

  /**
   * Classes whose jars (netty, protobuf) are needed both by the forked javac process and by the JPS build process.
   */
  public static @NotNull List<Class<?>> getCommonRequiredClasses() {
    return Arrays.asList(COMMON_REQUIRED_CLASSES.clone());
  }

  public static List<File> getExternalJavacProcessClasspath(String sdkHome, JavaCompilingTool compilingTool) {
    // Important! All dependencies must be java 8 compatible (the oldest supported javac to be launched)
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
          compilerClass = Class.forName("com.sun.tools.javac.api.JavacTool", false, ExternalJavacProcessClasspath.class.getClassLoader());
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

  private static @Nullable File getResourceFile(@NotNull Class<?> aClass) {
    final @Nullable Path resourcePath = PathManager.getJarForClass(aClass);
    return resourcePath != null ? resourcePath.toFile() : null;
  }
}
