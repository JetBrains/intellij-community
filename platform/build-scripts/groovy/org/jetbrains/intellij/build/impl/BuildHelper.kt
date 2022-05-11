// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.lang.CompoundRuntimeException;
import com.intellij.util.xml.dom.XmlDomReader;
import com.intellij.util.xml.dom.XmlElement;
import groovy.lang.Closure;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import kotlin.jvm.functions.Function0;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.BuildContext;
import org.jetbrains.intellij.build.CompilationContext;
import org.jetbrains.intellij.build.OpenedPackages;
import org.jetbrains.intellij.build.io.FileKt;
import org.jetbrains.intellij.build.io.ProcessKt;
import org.jetbrains.intellij.build.io.ZipKt;
import org.jetbrains.intellij.build.tasks.TraceKt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class BuildHelper {
  private BuildHelper() {
  }

  @NotNull
  public static void span(@NotNull SpanBuilder spanBuilder, @NotNull final Runnable task) {
    final Span span = spanBuilder.startSpan();
    IOGroovyMethods.withCloseable(span.makeCurrent(), new Closure<Void>(null, null) {
      public void doCall(Scope it) {
        try {
          task.run();
        }
        finally {
          span.end();
        }
      }

      public void doCall() {
        doCall(null);
      }
    });
  }

  @Nullable
  public static ForkJoinTask<?> createSkippableTask(@NotNull SpanBuilder spanBuilder,
                                                    @NotNull String taskId,
                                                    @NotNull BuildContext context,
                                                    @NotNull final Runnable task) {
    if (context.getOptions().buildStepsToSkip.contains(taskId)) {
      Span span = spanBuilder.startSpan();
      span.addEvent("skip");
      span.end();
      return null;
    }
    else {
      return TraceKt.createTask(spanBuilder, new Function0<Void>() {
        @Override
        public Void invoke() {
          task.run();
          return null;
        }
      });
    }
  }

  public static void copyDir(Path fromDir, Path targetDir) {
    FileKt.copyDir(fromDir, targetDir, null, null);
  }

  public static void copyDir(Path fromDir, Path targetDir, @NotNull Predicate<Path> dirFilter, @Nullable Predicate<Path> fileFilter) {
    FileKt.copyDir(fromDir, targetDir, dirFilter, fileFilter);
  }

  public static void copyDir(Path fromDir, Path targetDir, @NotNull Predicate<Path> dirFilter) {
    BuildHelper.copyDir(fromDir, targetDir, dirFilter, null);
  }

  /**
   * Filter is applied only to files, not to directories.
   */
  public static void copyDirWithFileFilter(Path fromDir, Path targetDir, @NotNull Predicate<Path> fileFilter) {
    FileKt.copyDir(fromDir, targetDir, null, fileFilter);
  }

  public static void moveFileToDir(Path file, Path targetDir) {
    Files.createDirectories(targetDir);
    Files.move(file, targetDir.resolve(file.getFileName()));
  }

  public static void copyFile(Path file, Path target) {
    Files.createDirectories(target.getParent());
    Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
  }

  public static void moveFile(Path source, Path target) {
    Files.createDirectories(target.getParent());
    Files.move(source, target);
  }

  public static void zip(@NotNull CompilationContext context, @NotNull Path targetFile, @NotNull Path dir, boolean compress) {
    zipWithPrefix(context, targetFile, Collections.singletonList(dir), null, compress);
  }

  public static void zipWithPrefix(@NotNull CompilationContext context,
                                   @NotNull Path targetFile,
                                   List<Path> dirs,
                                   @Nullable String prefix,
                                   boolean compress) {
    Map<Path, String> map = new LinkedHashMap<Path, String>(dirs.size());
    for (Path dir : dirs) {
      map.put(dir, StringGroovyMethods.asBoolean(prefix) ? prefix : "");
    }

    zipWithPrefixes(context, targetFile, map, compress);
  }

  public static void zipWithPrefixes(@NotNull CompilationContext context,
                                     @NotNull Path targetFile,
                                     @NotNull Map<Path, String> map,
                                     boolean compress) {
    Span span =
      TracerManager.spanBuilder("pack").setAttribute("targetFile", context.getPaths().getBuildOutputDir().relativize(targetFile).toString())
        .startSpan();
    Scope scope = span.makeCurrent();
    try {
      ZipKt.zip(targetFile, map, compress, false);
    }
    catch (Throwable e) {
      span.setStatus(StatusCode.ERROR, e.getMessage());
      throw e;
    }
    finally {
      scope.close();
      span.end();
    }
  }

  /**
   * Executes a Java class in a forked JVM
   */
  public static void runJava(CompilationContext context,
                             String mainClass,
                             Iterable<String> args,
                             Iterable<String> jvmArgs,
                             Iterable<String> classPath,
                             long timeoutMillis,
                             Path workingDir) {
    jvmArgs = DefaultGroovyMethods.plus(OpenedPackages.getCommandLineArguments(context), jvmArgs);
    ProcessKt.runJava(mainClass, args, jvmArgs, classPath, context.getStableJavaExecutable(), context.getMessages(), timeoutMillis,
                      workingDir);
  }

  /**
   * Executes a Java class in a forked JVM
   */
  public static void runJava(CompilationContext context,
                             String mainClass,
                             Iterable<String> args,
                             Iterable<String> jvmArgs,
                             Iterable<String> classPath,
                             long timeoutMillis) {
    BuildHelper.runJava(context, mainClass, args, jvmArgs, classPath, timeoutMillis, null);
  }

  /**
   * Executes a Java class in a forked JVM
   */
  public static void runJava(CompilationContext context,
                             String mainClass,
                             Iterable<String> args,
                             Iterable<String> jvmArgs,
                             Iterable<String> classPath) {
    BuildHelper.runJava(context, mainClass, args, jvmArgs, classPath, DEFAULT_TIMEOUT, null);
  }

  /**
   * Forks all tasks in the specified collection, returning when
   * {@code isDone} holds for each task or an (unchecked) exception is encountered, in which case the exception is rethrown.
   * If more than one task encounters an exception, then this method throws compound exception.
   * If any task encounters an exception, others will be not cancelled.
   * <p>
   * It is typically used when you have multiple asynchronous tasks that are not dependent on one another to complete successfully,
   * or you'd always like to know the result of each promise.
   * <p>
   * This way we can get valid artifacts for one OS if builds artifacts for another OS failed.
   */
  public static void invokeAllSettled(List<ForkJoinTask<?>> tasks) {
    for (ForkJoinTask<?> task : tasks) {
      task.fork();
    }


    joinAllSettled(tasks);
  }

  public static void joinAllSettled(List<ForkJoinTask<?>> tasks) {
    if (tasks.isEmpty()) {
      return;
    }


    List<Throwable> errors = new ArrayList<Throwable>();
    for (int i = tasks.size() - 1; ; i >= 0 ;){
      try {
        tasks.get(i).join();
      }
      catch (Throwable e) {
        errors.add(e);
      }
    }


    if (!errors.isEmpty()) {
      Throwable error;
      if (errors.size() == 1) {
        error = errors.get(0);
      }
      else {
        error = new CompoundRuntimeException(errors);
      }


      Span span = Span.current();
      span.recordException(error);
      span.setStatus(StatusCode.ERROR);
      throw error;
    }
  }

  public static void runApplicationStarter(@NotNull BuildContext context,
                                           @NotNull Path tempDir,
                                           @NotNull Set<String> ideClasspath,
                                           List<String> arguments,
                                           Map<String, Object> systemProperties,
                                           List<String> vmOptions,
                                           long timeoutMillis,
                                           @Nullable UnaryOperator<Set<String>> classpathCustomizer) {
    Files.createDirectories(tempDir);

    List<String> jvmArgs = new ArrayList<String>();
    BuildUtils.addVmProperty(jvmArgs, "idea.home.path", context.getPaths().getProjectHome());
    BuildUtils.addVmProperty(jvmArgs, "idea.system.path", FileUtilRt.toSystemIndependentName(tempDir.toString()) + "/system");
    BuildUtils.addVmProperty(jvmArgs, "idea.config.path", FileUtilRt.toSystemIndependentName(tempDir.toString()) + "/config");
    // reproducible build - avoid touching module outputs, do no write classpath.index
    BuildUtils.addVmProperty(jvmArgs, "idea.classpath.index.enabled", "false");

    BuildUtils.addVmProperty(jvmArgs, "java.system.class.loader", "com.intellij.util.lang.PathClassLoader");
    BuildUtils.addVmProperty(jvmArgs, "idea.platform.prefix", context.getProductProperties().getPlatformPrefix());
    jvmArgs.addAll(BuildUtils.propertiesToJvmArgs(systemProperties));
    jvmArgs.addAll(DefaultGroovyMethods.asBoolean(vmOptions) ? vmOptions : List.of("-Xmx1024m"));

    String debugPort = System.getProperty("intellij.build." + DefaultGroovyMethods.first(arguments) + ".debug.port");
    if (debugPort != null) {
      jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:" + debugPort.toString());
    }


    List<Path> additionalPluginPaths = context.getProductProperties().getAdditionalPluginPaths(context);
    Set<String> additionalPluginIds = new LinkedHashSet<String>();
    for (Path pluginPath : additionalPluginPaths) {
      for (Path jarFile : BuildUtils.getPluginJars(pluginPath)) {
        if (ideClasspath.add(jarFile.toString())) {
          context.getMessages().debug(String.valueOf(jarFile) + " from plugin " + String.valueOf(pluginPath));
          String pluginId = BuildUtils.readPluginId(jarFile);
          if (pluginId != null) {
            additionalPluginIds.add(pluginId);
          }
        }
      }
    }

    if (classpathCustomizer != null) {
      ideClasspath = classpathCustomizer.apply(ideClasspath);
    }


    disableCompatibleIgnoredPlugins(context, tempDir.resolve("config"), additionalPluginIds);

    runJava(context, "com.intellij.idea.Main", arguments, jvmArgs, ideClasspath, timeoutMillis);
  }

  public static void runApplicationStarter(@NotNull BuildContext context,
                                           @NotNull Path tempDir,
                                           @NotNull Set<String> ideClasspath,
                                           List<String> arguments,
                                           Map<String, Object> systemProperties,
                                           List<String> vmOptions,
                                           long timeoutMillis) {
    BuildHelper.runApplicationStarter(context, tempDir, ideClasspath, arguments, systemProperties, vmOptions, timeoutMillis, null);
  }

  public static void runApplicationStarter(@NotNull BuildContext context,
                                           @NotNull Path tempDir,
                                           @NotNull Set<String> ideClasspath,
                                           List<String> arguments,
                                           Map<String, Object> systemProperties,
                                           List<String> vmOptions) {
    BuildHelper.runApplicationStarter(context, tempDir, ideClasspath, arguments, systemProperties, vmOptions,
                                      TimeUnit.MINUTES.toMillis(10L), null);
  }

  public static void runApplicationStarter(@NotNull BuildContext context,
                                           @NotNull Path tempDir,
                                           @NotNull Set<String> ideClasspath,
                                           List<String> arguments,
                                           Map<String, Object> systemProperties) {
    BuildHelper.runApplicationStarter(context, tempDir, ideClasspath, arguments, systemProperties, null, TimeUnit.MINUTES.toMillis(10L),
                                      null);
  }

  public static void runApplicationStarter(@NotNull BuildContext context,
                                           @NotNull Path tempDir,
                                           @NotNull Set<String> ideClasspath,
                                           List<String> arguments) {
    BuildHelper.runApplicationStarter(context, tempDir, ideClasspath, arguments, Collections.emptyMap(), null,
                                      TimeUnit.MINUTES.toMillis(10L), null);
  }

  private static void disableCompatibleIgnoredPlugins(@NotNull BuildContext context,
                                                      @NotNull Path configDir,
                                                      @NotNull Set<String> explicitlyEnabledPlugins) {
    Set<String> toDisable = new LinkedHashSet<String>();
    for (String moduleName : context.getProductProperties().getProductLayout().getCompatiblePluginsToIgnore()) {
      // TODO: It is temporary solution to avoid exclude Kotlin from searchable options build because Kotlin team
      // need to use the same id in fir plugin.
      // Remove it when "kotlin.resources-fir" will removed from compatiblePluginsToIgnore
      // see: org/jetbrains/intellij/build/BaseIdeaProperties.groovy:179
      if (moduleName.equals("kotlin.resources-fir")) continue;
      Path pluginXml = context.findFileInModuleSources(moduleName, "META-INF/plugin.xml");
      final XmlElement child = XmlDomReader.readXmlAsModel(Files.newInputStream(pluginXml)).getChild("id");
      String pluginId = (child == null ? null : child.content);
      if (!explicitlyEnabledPlugins.contains(pluginId)) {
        toDisable.add(pluginId);
        context.getMessages()
          .debug("runApplicationStarter: \'" + pluginId + "\' will be disabled, because it\'s mentioned in \'compatiblePluginsToIgnore\'");
      }
    }

    if (!toDisable.isEmpty()) {
      Files.createDirectories(configDir);
      Files.writeString(configDir.resolve("disabled_plugins.txt"), String.join("\n", toDisable));
    }
  }

  public static final long DEFAULT_TIMEOUT = TimeUnit.MINUTES.toMillis(10L);
}
