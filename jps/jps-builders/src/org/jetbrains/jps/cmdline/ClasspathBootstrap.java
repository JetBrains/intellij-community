// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cmdline;

import com.dynatrace.hash4j.hashing.Hashing;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.protobuf.Message;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.openapi.application.ClassPathUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import com.intellij.tracing.Tracer;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.SystemProperties;
import com.intellij.util.lang.HashMapZipFile;
import com.intellij.util.lang.JavaVersion;
import com.jgoodies.forms.layout.CellConstraints;
import com.thoughtworks.qdox.JavaProjectBuilder;
import kotlin.metadata.jvm.JvmMetadataUtil;
import net.n3.nanoxml.IXMLBuilder;
import org.h2.mvstore.MVStore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.builders.impl.java.EclipseCompilerTool;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.builders.java.JavaSourceTransformer;
import org.jetbrains.jps.dependency.DependencyGraph;
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
import java.util.function.Consumer;

@ApiStatus.Internal
public final class ClasspathBootstrap {
  private static final Logger LOG = Logger.getInstance(ClasspathBootstrap.class);

  private ClasspathBootstrap() { }

  private static final Class<?>[] COMMON_REQUIRED_CLASSES = {
    // Uncomment to use the same netty that is used in the rest of IDE
    //NetUtil.class, // netty common
    //EventLoopGroup.class, // netty transport
    //AddressResolverGroup.class, // netty resolver
    //ByteBufAllocator.class, // netty buffer
    //ByteToMessageDecoder.class, // netty codec http
    //ProtobufDecoder.class,  // netty codec protobuf
    
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

  private static final String[] FORBIDDEN_JARS = {
    "app.jar",
    "app-client.java"
  };

  private static final String DEFAULT_MAVEN_REPOSITORY_PATH = ".m2/repository";
  @VisibleForTesting
  public static final String NETTY_JPS_VERSION = "4.1.117.Final";
  private static final String NETTY_JPS_DISTRIBUTION_JAR_NAME = "netty-jps.jar";
  private static final String[] NETTY_ARTIFACT_NAMES = {
    "netty-buffer", "netty-codec-http", "netty-codec-http2", "netty-codec", "netty-common", "netty-handler", "netty-resolver", "netty-transport"
  };

  private static void getNettyForJpsClasspath(Consumer<Path> consumer) {
    Path rootPath = Path.of(getResourcePath(ExternalJavacProcess.class));
    Path nettyDistributionPath = rootPath.resolveSibling("rt").resolve(NETTY_JPS_DISTRIBUTION_JAR_NAME);
    if (Files.isRegularFile(rootPath) && Files.exists(nettyDistributionPath)) {
      // running regular installation
      consumer.accept(nettyDistributionPath);
    }
    else {
      // running from sources or on the build server
      // take the library from the local maven repository
      Path artifactRoot = getMavenLocalRepositoryDir().resolve("io").resolve("netty");
      for (String artifactName : NETTY_ARTIFACT_NAMES) {
        consumer.accept(artifactRoot.resolve(artifactName).resolve(NETTY_JPS_VERSION).resolve(artifactName + "-" + NETTY_JPS_VERSION + ".jar"));
      }
    }
  }

  private static @NotNull Path getMavenLocalRepositoryDir() {
    final String userHome = System.getProperty("user.home", null);
    return userHome != null ? Path.of(userHome, DEFAULT_MAVEN_REPOSITORY_PATH) : Path.of(DEFAULT_MAVEN_REPOSITORY_PATH);
  }

  private static void addToClassPath(Set<String> result, Class<?> aClass) {
    Path path = PathManager.getJarForClass(aClass);
    if (path == null) {
      return;
    }

    final String pathString = path.toString();

    if (result.add(pathString)) {
      if (LOG.isTraceEnabled()) {
        LOG.trace(pathString + " added to classpath to include " + aClass.getName());
      }
      assertPathDoesNotContainTheWholeWorld(pathString, path, aClass);
    }
  }

  private static void assertPathDoesNotContainTheWholeWorld(@NotNull String pathString, @NotNull Path path, @NotNull Class<?> aClass) {
    for (String jarName : FORBIDDEN_JARS) {
      if (pathString.endsWith(jarName) &&
          path.getFileName().toString().equals(jarName) &&
          path.getParent().equals(Paths.get(PathManager.getLibPath()))
      ) {
        LOG.error("Due to " + aClass.getName() + " requirement, inappropriate " + pathString + " is added to build process classpath");
      }
    }
  }

  private static void addToClassPath(Set<String> cp, @NotNull Class<?> @NotNull [] classes) {
    for (Class<?> aClass : classes) {
      addToClassPath(cp, aClass);
    }
  }

  public static @NotNull Collection<String> getBuildProcessApplicationClasspath() {
    // predictable order
    Set<String> cp = new LinkedHashSet<>();

    addToClassPath(cp, BuildMain.class);
    addToClassPath(cp, ExternalJavacProcess.class);  // intellij.platform.jps.build.javac.rt part
    addToClassPath(cp, JavacReferenceCollector.class);  // jps-javac-extension library
    addToClassPath(cp, DependencyGraph.class);  // dep-graph

    // intellij.platform.util
    addToClassPath(cp, ClassPathUtil.getUtilClasses());
    addToClassPath(cp, HashMapZipFile.class); // intellij.platform.util.zip

    ClassPathUtil.addKotlinStdlib(cp);
    addToClassPath(cp, JvmMetadataUtil.class);  // kotlin metadata parsing
    addToClassPath(cp, COMMON_REQUIRED_CLASSES);
    getNettyForJpsClasspath(path -> cp.add(path.toString()));

    addToClassPath(cp, ClassWriter.class);  // asm
    addToClassPath(cp, ClassVisitor.class);  // asm-commons
    addToClassPath(cp, RuntimeModuleRepository.class); // intellij.platform.runtime.repository
    addToClassPath(cp, JpsModel.class);  // intellij.platform.jps.model
    addToClassPath(cp, JpsModelImpl.class);  // intellij.platform.jps.model.impl
    addToClassPath(cp, JpsProjectLoader.class);  // intellij.platform.jps.model.serialization
    addToClassPath(cp, JavaVersion.class); // intellij.platform.util.multiplatform
    addToClassPath(cp, Strings.class); // intellij.platform.base.kmp
    addToClassPath(cp, AlienFormFileException.class);  // intellij.java.guiForms.compiler
    addToClassPath(cp, GridConstraints.class);  // intellij.java.guiForms.rt
    addToClassPath(cp, CellConstraints.class);  // jGoodies-forms
    cp.addAll(getInstrumentationUtilRoots());
    addToClassPath(cp, IXMLBuilder.class);  // nano-xml
    addToClassPath(cp, JavaProjectBuilder.class);  // QDox lightweight java parser
    addToClassPath(cp, Gson.class);  // gson
    // caffeine
    addToClassPath(cp, Caffeine.class);
    // Hashing
    addToClassPath(cp, Hashing.class);
    addToClassPath(cp, MVStore.class);

    addToClassPath(cp, ArtifactRepositoryManager.getClassesFromDependencies());
    addToClassPath(cp, Tracer.class); // tracing infrastructure

    try {
      Class<?> cmdLineWrapper = Class.forName("com.intellij.rt.execution.CommandLineWrapper");
      addToClassPath(cp, cmdLineWrapper);  // idea_rt.jar
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
    // Important! All dependencies must be java 7 compatible (the oldest supported javac to be launched)
    final Set<File> cp = new LinkedHashSet<>();
    cp.add(getResourceFile(ExternalJavacProcess.class)); // self
    cp.add(getResourceFile(JavacReferenceCollector.class));  // jps-javac-extension library
    cp.add(getResourceFile(SystemInfoRt.class)); // util_rt

    for (Class<?> aClass : COMMON_REQUIRED_CLASSES) {
      cp.add(getResourceFile(aClass));
    }
    getNettyForJpsClasspath(path -> cp.add(path.toFile()));

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

  public static @Nullable String getResourcePath(@NotNull Class<?> aClass) {
    return PathManager.getJarPathForClass(aClass);
  }

  public static @Nullable File getResourceFile(@NotNull Class<?> aClass) {
    final @Nullable Path resourcePath = PathManager.getJarForClass(aClass);
    return resourcePath != null ? resourcePath.toFile() : null;
  }

  public static void configureReflectionOpenPackages(Consumer<? super String> paramConsumer) {
    for (String aPackage : REFLECTION_OPEN_PACKAGES) {
      paramConsumer.accept("--add-opens");
      paramConsumer.accept(aPackage);
    }
  }

  private static List<String> getInstrumentationUtilRoots() {
    String instrumentationUtilPath = getResourcePath(NotNullVerifyingInstrumenter.class);
    assert instrumentationUtilPath != null;
    File instrumentationUtil = new File(instrumentationUtilPath);
    if (instrumentationUtil.isDirectory()) {
      //running from sources: load classes from .../out/production/intellij.java.compiler.instrumentationUtil.java8
      return Arrays.asList(instrumentationUtilPath, new File(instrumentationUtil.getParentFile(), "intellij.java.compiler.instrumentationUtil.java8").getAbsolutePath());
    }
    else {
      var relevantJarsRoot = PathManager.getArchivedCompliedClassesLocation();
      Map<String, String> mapping = PathManager.getArchivedCompiledClassesMapping();
      if (relevantJarsRoot != null && mapping != null && instrumentationUtilPath.startsWith(relevantJarsRoot)) {
        return Arrays.asList(instrumentationUtilPath, mapping.get("production/intellij.java.compiler.instrumentationUtil.java8"));
      }
      //running from jars: intellij.java.compiler.instrumentationUtil.java8 is located in the same jar
      return Collections.singletonList(instrumentationUtilPath);
    }
  }
}