// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.xml.dom.XmlDomReader
import groovy.transform.CompileStatic
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Scope
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.io.FileKt
import org.jetbrains.intellij.build.io.ProcessKt
import org.jetbrains.intellij.build.io.ZipKt
import org.jetbrains.intellij.build.tasks.BlockmapKt
import org.jetbrains.intellij.build.tasks.TraceKt

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.function.UnaryOperator

@CompileStatic
final class BuildHelper {
  private static final BuildHelper instance = new BuildHelper()

  public static final long DEFAULT_TIMEOUT = TimeUnit.MINUTES.toMillis(10L)

  private BuildHelper() {
  }

  @NotNull
  <T> ForkJoinTask<T> createTask(@NotNull SpanBuilder spanBuilder, @NotNull Supplier<T> task) {
    return TraceKt.createTask(spanBuilder, task)
  }

  @NotNull
  void span(@NotNull SpanBuilder spanBuilder, @NotNull Runnable task) {
    Span span = spanBuilder.startSpan()
    span.makeCurrent().withCloseable {
      try {
        task.run()
      }
      finally {
        span.end()
      }
    }
  }

  @Nullable
  ForkJoinTask<?> createSkippableTask(@NotNull SpanBuilder spanBuilder,
                                      @NotNull String taskId,
                                      @NotNull BuildContext context,
                                      @NotNull Runnable task) {
    if (context.options.buildStepsToSkip.contains(taskId)) {
      Span span = spanBuilder.startSpan()
      span.addEvent("skip")
      span.end()
      return null
    }
    else {
      return TraceKt.createTask(spanBuilder, new Supplier<Void>() {
        @Override
        Void get() {
          task.run()
          return null
        }
      })
    }
  }

  void copyDir(Path fromDir, Path targetDir) {
    FileKt.copyDir(fromDir, targetDir, null, null)
  }

  void copyDir(Path fromDir, Path targetDir, @NotNull Predicate<Path> dirFilter, @Nullable Predicate<Path> fileFilter = null) {
    FileKt.copyDir(fromDir, targetDir, dirFilter, fileFilter)
  }

  /**
   * Filter is applied only to files, not to directories.
   */
  void copyDirWithFileFilter(Path fromDir, Path targetDir, @NotNull Predicate<Path> fileFilter) {
    FileKt.copyDir(fromDir, targetDir, null, fileFilter)
  }

  static void copyFileToDir(Path file, Path targetDir) {
    doCopyFile(file, targetDir.resolve(file.fileName), targetDir, true)
  }

  static void moveFileToDir(Path file, Path targetDir) {
    Files.createDirectories(targetDir)
    Files.move(file, targetDir.resolve(file.fileName))
  }

  static void copyFile(Path file, Path target) {
    doCopyFile(file, target, target.parent, true)
  }

  static void copyFile(Path file, Path target, boolean useHardlink) {
    doCopyFile(file, target, target.parent, useHardlink)
  }

  // target.parent creates new instance of Path every call, pass targetDir explicitly
  private static void doCopyFile(Path file, Path target, Path targetDir, boolean useHardlink) {
    Files.createDirectories(targetDir)

    if (useHardlink && !SystemInfoRt.isWindows && Files.getFileStore(file) == Files.getFileStore(targetDir)) {
      Files.createLink(target, file)
    }
    else {
      Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES)
    }
  }

  static void moveFile(Path source, Path target) {
    Files.createDirectories(target.parent)
    Files.move(source, target)
  }

  static void zip(@NotNull CompilationContext context, @NotNull Path targetFile, @NotNull Path dir, boolean compress) {
    zipWithPrefix(context, targetFile, Collections.singletonList(dir), null, compress)
  }

  static void zipWithPrefix(@NotNull CompilationContext context,
                            @NotNull Path targetFile, List<Path> dirs,
                            @Nullable String prefix,
                            boolean compress) {
    Map<Path, String> map = new LinkedHashMap<>(dirs.size())
    for (Path dir : dirs) {
      map.put(dir, prefix ?: "")
    }
    zipWithPrefixes(context, targetFile, map, compress)
  }

  static void zipWithPrefixes(@NotNull CompilationContext context,
                              @NotNull Path targetFile,
                              @NotNull Map<Path, String> map,
                              boolean compress) {
    Span span = TracerManager.spanBuilder("pack")
      .setAttribute("targetFile", context.paths.buildOutputDir.relativize(targetFile).toString())
      .startSpan()
    Scope scope = span.makeCurrent()
    try {
      ZipKt.zip(targetFile, map, compress, false)
    }
    catch (Throwable e) {
      span.setStatus(StatusCode.ERROR, e.message)
      throw e
    }
    finally {
      scope.close()
      span.end()
    }
  }

  void bulkZipWithPrefix(@NotNull Path commonSourceDir, @NotNull List<Map.Entry<String, Path>> items, boolean compress) {
    BlockmapKt.bulkZipWithPrefix(commonSourceDir, items, compress)
  }

  /**
   * Executes a Java class in a forked JVM
   */
  static void runJava(CompilationContext context,
                      String mainClass,
                      Iterable<String> args,
                      Iterable<String> jvmArgs,
                      Iterable<String> classPath,
                      long timeoutMillis = DEFAULT_TIMEOUT,
                      Path workingDir = null) {
    ProcessKt.runJava(mainClass, args, jvmArgs, classPath, context.messages, timeoutMillis,
                                                           workingDir)
  }

  static void runProcess(CompilationContext context, List<String> args) {
    ProcessKt.runProcess(args, null, context.messages)
  }

  static void runProcess(CompilationContext context, List<String> args, @Nullable Path workingDir) {
    ProcessKt.runProcess(args, workingDir, context.messages)
  }

  /**
   * Forks all tasks in the specified collection, returning when
   * {@code isDone} holds for each task or an (unchecked) exception is encountered, in which case the exception is rethrown.
   * If more than one task encounters an exception, then this method throws compound exception.
   * If any task encounters an exception, others will be not cancelled.
   *
   * It is typically used when you have multiple asynchronous tasks that are not dependent on one another to complete successfully,
   * or you'd always like to know the result of each promise.
   *
   * This way we can get valid artifacts for one OS if builds artifacts for another OS failed.
   */
  static void invokeAllSettled(List<ForkJoinTask<?>> tasks) {
    for (ForkJoinTask<?> task : tasks) {
      task.fork()
    }

    joinAllSettled(tasks)
  }

  static void joinAllSettled(List<ForkJoinTask<?>> tasks) {
    if (tasks.isEmpty()) {
      return
    }

    List<Throwable> errors = new ArrayList<>()
    for (int i = tasks.size() - 1; i >= 0; i--) {
      try {
        tasks.get(i).join()
      }
      catch (Throwable e) {
        errors.add(e)
      }
    }

    if (!errors.isEmpty()) {
      Throwable error
      if (errors.size() == 1) {
        error = errors.get(0)
      }
      else {
        error = new CompoundRuntimeException(errors)
      }

      Span span = Span.current()
      span.recordException(error)
      span.setStatus(StatusCode.ERROR)
      throw error
    }
  }

  static void runApplicationStarter(@NotNull BuildContext context,
                                    @NotNull Path tempDir,
                                    @NotNull Collection<String> modules,
                                    List<String> arguments,
                                    Map<String, Object> systemProperties = Collections.emptyMap(),
                                    List<String> vmOptions = null,
                                    long timeoutMillis = TimeUnit.MINUTES.toMillis(10L),
                                    @Nullable UnaryOperator<Set<String>> classpathCustomizer = null) {
    Files.createDirectories(tempDir)

    Set<String> ideClasspath = new LinkedHashSet<String>()

    Span.current().addEvent("collect classpath to run application starter", Attributes.of(AttributeKey.stringArrayKey("args"), arguments))
    context.messages.debug("Collecting classpath to run application starter '${arguments.first()}:")
    for (moduleName in modules) {
      for (pathElement in context.getModuleRuntimeClasspath(context.findRequiredModule(moduleName), false)) {
        if (ideClasspath.add(pathElement)) {
          context.messages.debug(" $pathElement from $moduleName")
        }
      }
    }

    List<String> jvmArgs = new ArrayList<>()
    BuildUtils.addVmProperty(jvmArgs, "idea.home.path", context.paths.projectHome)
    BuildUtils.addVmProperty(jvmArgs, "idea.system.path", FileUtilRt.toSystemIndependentName(tempDir.toString()) + "/system")
    BuildUtils.addVmProperty(jvmArgs, "idea.config.path", FileUtilRt.toSystemIndependentName(tempDir.toString()) + "/config")
    // reproducible build - avoid touching module outputs, do no write classpath.index
    BuildUtils.addVmProperty(jvmArgs, "idea.classpath.index.enabled", "false")

    BuildUtils.addVmProperty(jvmArgs, "java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
    BuildUtils.addVmProperty(jvmArgs, "idea.platform.prefix", context.productProperties.platformPrefix)
    jvmArgs.addAll(BuildUtils.propertiesToJvmArgs(systemProperties))
    jvmArgs.addAll(vmOptions ?: List.of("-Xmx1024m"))

    String debugPort = System.getProperty("intellij.build.${arguments.first()}.debug.port")
    if (debugPort != null) {
      jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:$debugPort".toString())
    }

    List<Path> additionalPluginPaths = context.productProperties.getAdditionalPluginPaths(context)
    Set<String> additionalPluginIds = new LinkedHashSet<>()
    for (Path pluginPath : additionalPluginPaths) {
      for (Path jarFile : BuildUtils.getPluginJars(pluginPath)) {
        if (ideClasspath.add(jarFile.toString())) {
          context.messages.debug("$jarFile from plugin $pluginPath")
          String pluginId = BuildUtils.readPluginId(jarFile)
          if (pluginId != null) {
            additionalPluginIds.add(pluginId)
          }
        }
      }
    }
    if (classpathCustomizer != null) {
      ideClasspath = classpathCustomizer.apply(ideClasspath)
    }

    disableCompatibleIgnoredPlugins(context, tempDir.resolve("config"), additionalPluginIds)

    runJava(
      context,
      "com.intellij.idea.Main",
      arguments,
      jvmArgs,
      ideClasspath,
      timeoutMillis)
  }

  private static void disableCompatibleIgnoredPlugins(@NotNull BuildContext context,
                                                      @NotNull Path configDir,
                                                      @NotNull Set<String> explicitlyEnabledPlugins) {
    Set<String> toDisable = new LinkedHashSet<>()
    for (String moduleName : context.productProperties.productLayout.compatiblePluginsToIgnore) {
      Path pluginXml = context.findFileInModuleSources(moduleName, "META-INF/plugin.xml")
      String pluginId = XmlDomReader.readXmlAsModel(Files.newInputStream(pluginXml)).getChild("id")?.content
      if (!explicitlyEnabledPlugins.contains(pluginId)) {
        toDisable.add(pluginId)
        context.messages.debug("runApplicationStarter: '$pluginId' will be disabled, because it's mentioned in 'compatiblePluginsToIgnore'")
      }
    }
    if (!toDisable.isEmpty()) {
      Files.createDirectories(configDir)
      Files.writeString(configDir.resolve("disabled_plugins.txt"), String.join("\n", toDisable))
    }
  }

  static BuildHelper getInstance(@NotNull CompilationContext context) {
    return instance
  }
}
