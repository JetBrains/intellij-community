// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.createTask
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.xml.dom.readXmlAsModel
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.OpenedPackages.getCommandLineArguments
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.runJava
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import kotlin.io.path.copyTo

val DEFAULT_TIMEOUT = TimeUnit.MINUTES.toMillis(10L)

fun span(spanBuilder: SpanBuilder, task: Runnable) {
  spanBuilder.useWithScope {
    task.run()
  }
}

inline fun createSkippableTask(spanBuilder: SpanBuilder,
                               taskId: String,
                               context: BuildContext,
                               crossinline task: () -> Unit): ForkJoinTask<*>? {
  if (context.options.buildStepsToSkip.contains(taskId)) {
    val span = spanBuilder.startSpan()
    span.addEvent("skip")
    span.end()
    return null
  }
  else {
    return createTask(spanBuilder) { task() }
  }
}

/**
 * Filter is applied only to files, not to directories.
 */
fun copyDirWithFileFilter(fromDir: Path, targetDir: Path, fileFilter: Predicate<Path>) {
  copyDir(sourceDir = fromDir, targetDir = targetDir, fileFilter = fileFilter)
}

fun zip(context: CompilationContext, targetFile: Path, dir: Path, compress: Boolean) {
  zipWithPrefixes(context, targetFile, mapOf(dir to ""), compress)
}

fun zipWithPrefix(context: CompilationContext,
                  targetFile: Path,
                  dirs: List<Path>,
                  prefix: String?,
                  compress: Boolean) {
  zipWithPrefixes(context, targetFile, dirs.associateWithTo(LinkedHashMap(dirs.size)) { (prefix ?: "") }, compress)
}

fun zipWithPrefixes(context: CompilationContext, targetFile: Path, map: Map<Path, String>, compress: Boolean) {
  spanBuilder("pack")
    .setAttribute("targetFile", context.paths.buildOutputDir.relativize(targetFile).toString())
    .useWithScope {
      org.jetbrains.intellij.build.io.zip(targetFile = targetFile, dirs = map, compress = compress, addDirEntries = false)
    }
}

/**
 * Executes a Java class in a forked JVM
 */
@JvmOverloads
fun runJava(context: CompilationContext,
            mainClass: String,
            args: Iterable<String>,
            jvmArgs: Iterable<String>,
            classPath: Iterable<String>,
            timeoutMillis: Long = DEFAULT_TIMEOUT,
            workingDir: Path? = null,
            onError: (() -> Unit)? = null) {
  runJava(mainClass = mainClass,
          args = args,
          jvmArgs = getCommandLineArguments(context) + jvmArgs,
          classPath = classPath,
          javaExe = context.stableJavaExecutable,
          logger = context.messages,
          timeoutMillis = timeoutMillis,
          workingDir = workingDir,
          onError = onError)
}

/**
 * Forks all tasks in the specified collection, returning when
 * `isDone` holds for each task or an (unchecked) exception is encountered, in which case the exception is rethrown.
 * If more than one task encounters an exception, then this method throws compound exception.
 * If any task encounters an exception, others will be not cancelled.
 *
 * It is typically used when you have multiple asynchronous tasks that are not dependent on one another to complete successfully,
 * or you'd always like to know the result of each promise.
 *
 * This way we can get valid artifacts for one OS if builds artifacts for another OS failed.
 */
fun invokeAllSettled(tasks: List<ForkJoinTask<*>>) {
  for (task in tasks) {
    task.fork()
  }
  joinAllSettled(tasks)
}

fun joinAllSettled(tasks: List<ForkJoinTask<*>>) {
  if (tasks.isEmpty()) {
    return
  }

  val errors = ArrayList<Throwable>()
  for (task in tasks.asReversed()) {
    try {
      task.join()
    }
    catch (e: Throwable) {
      errors.add(e)
    }
  }
  if (!errors.isEmpty()) {
    val error = if (errors.size == 1) errors[0] else CompoundRuntimeException(errors)
    val span = Span.current()
    span.recordException(error)
    span.setStatus(StatusCode.ERROR)
    throw error
  }
}

fun runApplicationStarter(context: BuildContext,
                          tempDir: Path,
                          ideClasspath: Set<String>,
                          arguments: List<String>,
                          systemProperties: Map<String, Any> = emptyMap(),
                          vmOptions: List<String> = emptyList(),
                          timeoutMillis: Long = DEFAULT_TIMEOUT,
                          classpathCustomizer: ((MutableSet<String>) -> Unit)? = null) {
  Files.createDirectories(tempDir)
  val jvmArgs = ArrayList<String>()
  val systemDir = tempDir.resolve("system")
  BuildUtils.addVmProperty(jvmArgs, "idea.home.path", context.paths.projectHome)
  BuildUtils.addVmProperty(jvmArgs, "idea.system.path", FileUtilRt.toSystemIndependentName(systemDir.toString()))
  BuildUtils.addVmProperty(jvmArgs, "idea.config.path", FileUtilRt.toSystemIndependentName(tempDir.toString()) + "/config")
  // reproducible build - avoid touching module outputs, do no write classpath.index
  BuildUtils.addVmProperty(jvmArgs, "idea.classpath.index.enabled", "false")
  BuildUtils.addVmProperty(jvmArgs, "java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
  BuildUtils.addVmProperty(jvmArgs, "idea.platform.prefix", context.productProperties.platformPrefix)
  jvmArgs.addAll(BuildUtils.propertiesToJvmArgs(systemProperties))
  jvmArgs.addAll(vmOptions.takeIf { it.isNotEmpty() } ?: listOf("-Xmx1024m"))
  val debugPort = System.getProperty("intellij.build." + arguments.first() + ".debug.port")
  if (debugPort != null) {
    jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:$debugPort")
  }

  val effectiveIdeClasspath = LinkedHashSet(ideClasspath)

  val additionalPluginPaths = context.productProperties.getAdditionalPluginPaths(context)
  val additionalPluginIds = LinkedHashSet<String>()
  for (pluginPath in additionalPluginPaths) {
    for (jarFile in BuildUtils.getPluginJars(pluginPath)) {
      if (effectiveIdeClasspath.add(jarFile.toString())) {
        context.messages.debug("$jarFile from plugin $pluginPath")
        val pluginId = BuildUtils.readPluginId(jarFile)
        if (pluginId != null) {
          additionalPluginIds.add(pluginId)
        }
      }
    }
  }
  classpathCustomizer?.invoke(effectiveIdeClasspath)
  disableCompatibleIgnoredPlugins(context, tempDir.resolve("config"), additionalPluginIds)
  runJava(context, "com.intellij.idea.Main", arguments, jvmArgs, effectiveIdeClasspath, timeoutMillis) {
    val logFile = systemDir.resolve("log").resolve("idea.log")
    val logFileToPublish = File.createTempFile("idea-", ".log")
    logFile.copyTo(logFileToPublish.toPath(), true)
    context.notifyArtifactBuilt(logFileToPublish.toPath())
    context.messages.error("Log file: ${logFileToPublish.canonicalPath} attached to build artifacts")
  }
}

private fun disableCompatibleIgnoredPlugins(context: BuildContext,
                                            configDir: Path,
                                            explicitlyEnabledPlugins: Set<String?>) {
  val toDisable = LinkedHashSet<String>()
  for (moduleName in context.productProperties.productLayout.compatiblePluginsToIgnore) {
    // TODO: It is temporary solution to avoid exclude Kotlin from searchable options build because Kotlin team
    // need to use the same id in fir plugin.
    // Remove it when "kotlin.resources-fir" will removed from compatiblePluginsToIgnore
    // see: org/jetbrains/intellij/build/BaseIdeaProperties.groovy:179
    if (moduleName == "kotlin.resources-fir") {
      continue
    }

    val pluginXml = context.findFileInModuleSources(moduleName, "META-INF/plugin.xml")!!
    val child = readXmlAsModel(Files.newInputStream(pluginXml)).getChild("id")
    val pluginId = child?.content ?: continue
    if (!explicitlyEnabledPlugins.contains(pluginId)) {
      toDisable.add(pluginId)
      context.messages.debug(
        "runApplicationStarter: \'$pluginId\' will be disabled, because it\'s mentioned in \'compatiblePluginsToIgnore\'")
    }
  }
  if (!toDisable.isEmpty()) {
    Files.createDirectories(configDir)
    Files.writeString(configDir.resolve("disabled_plugins.txt"), java.lang.String.join("\n", toDisable))
  }
}