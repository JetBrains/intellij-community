// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.diagnostic.telemetry.useWithScope2
import com.intellij.util.JavaModuleOptions
import com.intellij.util.system.OS
import com.intellij.util.xml.dom.readXmlAsModel
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildScriptsLoggedError
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.runJava
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import kotlin.io.path.copyTo

val DEFAULT_TIMEOUT = TimeUnit.MINUTES.toMillis(10L)

internal fun span(spanBuilder: SpanBuilder, task: Runnable) {
  spanBuilder.useWithScope {
    task.run()
  }
}

inline fun CoroutineScope.createSkippableJob(spanBuilder: SpanBuilder,
                                             taskId: String,
                                             context: BuildContext,
                                             crossinline task: suspend () -> Unit): Job? {
  if (context.isStepSkipped(taskId)) {
    spanBuilder.startSpan().addEvent("skip").end()
    return null
  }
  else {
    return launch {
      spanBuilder.useWithScope2 {
        task()
      }
    }
  }
}

/**
 * Filter is applied only to files, not to directories.
 */
fun copyDirWithFileFilter(fromDir: Path, targetDir: Path, fileFilter: Predicate<Path>) {
  copyDir(sourceDir = fromDir, targetDir = targetDir, fileFilter = fileFilter)
}

fun zip(context: CompilationContext, targetFile: Path, dir: Path, compress: Boolean, addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.NONE) {
  zipWithPrefixes(context, targetFile, mapOf(dir to ""), compress, addDirEntriesMode)
}

fun zipWithPrefixes(context: CompilationContext, targetFile: Path, map: Map<Path, String>, compress: Boolean, addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.NONE) {
  spanBuilder("pack")
    .setAttribute("targetFile", context.paths.buildOutputDir.relativize(targetFile).toString())
    .useWithScope {
      org.jetbrains.intellij.build.io.zip(targetFile = targetFile, dirs = map, compress = compress, addDirEntriesMode = addDirEntriesMode)
    }
}

/**
 * Executes a Java class in a forked JVM
 */
@JvmOverloads
fun runJava(context: CompilationContext,
            mainClass: String,
            args: List<String>,
            jvmArgs: List<String>,
            classPath: List<String>,
            timeoutMillis: Long = DEFAULT_TIMEOUT,
            workingDir: Path? = null,
            onError: (() -> Unit)? = null) {
  runJava(mainClass = mainClass,
          args = args,
          jvmArgs = getCommandLineArgumentsForOpenPackages(context) + jvmArgs,
          classPath = classPath,
          javaExe = context.stableJavaExecutable,
          logger = context.messages,
          timeoutMillis = timeoutMillis,
          workingDir = workingDir,
          onError = onError)
}

fun runApplicationStarter(context: BuildContext,
                          tempDir: Path,
                          ideClasspath: Set<String>,
                          arguments: List<String>,
                          systemProperties: Map<String, Any> = emptyMap(),
                          vmOptions: List<String> = emptyList(),
                          timeoutMillis: Long = DEFAULT_TIMEOUT) {
  Files.createDirectories(tempDir)
  val jvmArgs = ArrayList<String>()
  val systemDir = tempDir.resolve("system")
  BuildUtils.addVmProperty(jvmArgs, "idea.home.path", context.paths.projectHome.toString())
  BuildUtils.addVmProperty(jvmArgs, "idea.system.path", systemDir.toString())
  BuildUtils.addVmProperty(jvmArgs, "idea.config.path", "$tempDir/config")
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
        readPluginId(jarFile)?.let {
          additionalPluginIds.add(it)
        }
      }
    }
  }
  disableCompatibleIgnoredPlugins(context, tempDir.resolve("config"), additionalPluginIds)
  runJava(context = context,
          mainClass = "com.intellij.idea.Main",
          args = arguments,
          jvmArgs = jvmArgs,
          classPath = effectiveIdeClasspath.toList(),
          timeoutMillis = timeoutMillis) {
    val logFile = systemDir.resolve("log").resolve("idea.log")
    if (Files.exists(logFile)) {
      val logFileToPublish = File.createTempFile("idea-", ".log")
      logFile.copyTo(logFileToPublish.toPath(), true)
      context.notifyArtifactBuilt(logFileToPublish.toPath())
      try {
        context.messages.error("Log file: ${logFileToPublish.canonicalPath} attached to build artifacts")
      }
      catch (_: BuildScriptsLoggedError) {
        // skip exception thrown by logger.error
      }
    }
  }
}

private fun readPluginId(pluginJar: Path): String? {
  if (!pluginJar.toString().endsWith(".jar") || !Files.isRegularFile(pluginJar)) {
    return null
  }

  try {
    FileSystems.newFileSystem(pluginJar, null as ClassLoader?).use {
      return readXmlAsModel(Files.newInputStream(it.getPath("META-INF/plugin.xml"))).getChild("id")?.content
    }
  }
  catch (ignore: NoSuchFileException) {
    return null
  }
}

private fun disableCompatibleIgnoredPlugins(context: BuildContext,
                                            configDir: Path,
                                            explicitlyEnabledPlugins: Set<String?>) {
  val toDisable = LinkedHashSet<String>()
  for (moduleName in context.productProperties.productLayout.compatiblePluginsToIgnore) {
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

/**
 * @return a list of JVM args for opened packages (JBR17+) in a format `--add-opens=PACKAGE=ALL-UNNAMED` for a specified or current OS
 */
fun getCommandLineArgumentsForOpenPackages(context: CompilationContext, target: OsFamily? = null): List<String> {
  val file = context.paths.communityHomeDir.communityRoot.resolve("plugins/devkit/devkit-core/src/run/OpenedPackages.txt")
  val os = when (target) {
    OsFamily.WINDOWS -> OS.Windows
    OsFamily.MACOS -> OS.macOS
    OsFamily.LINUX -> OS.Linux
    null -> OS.CURRENT
  }
  return JavaModuleOptions.readOptions(file, os)
}
