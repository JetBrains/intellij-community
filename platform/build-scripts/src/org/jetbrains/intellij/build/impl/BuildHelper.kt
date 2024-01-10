// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScopeBlocking
import com.intellij.util.JavaModuleOptions
import com.intellij.util.system.OS
import com.intellij.util.xml.dom.readXmlAsModel
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.io.DEFAULT_TIMEOUT
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.runJava
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.io.path.copyTo
import kotlin.time.Duration

internal fun span(spanBuilder: SpanBuilder, task: Runnable) {
  spanBuilder.useWithScopeBlocking {
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
      spanBuilder.useWithScope {
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

fun zip(context: CompilationContext, targetFile: Path, dir: Path) {
  spanBuilder("pack")
    .setAttribute("targetFile", context.paths.buildOutputDir.relativize(targetFile).toString())
    .useWithScopeBlocking {
      org.jetbrains.intellij.build.io.zip(targetFile = targetFile, dirs = mapOf(dir to ""))
    }
}

suspend fun runIdea(context: CompilationContext,
                    mainClass: String,
                    args: List<String>,
                    jvmArgs: List<String>,
                    classPath: List<String>,
                    timeout: Duration = DEFAULT_TIMEOUT,
                    workingDir: Path? = null,
                    onError: (() -> Unit)? = null) {
  runJava(mainClass = mainClass,
          args = args,
          jvmArgs = getCommandLineArgumentsForOpenPackages(context) + jvmArgs + listOf("-Dij.dir.lock.debug=true"),
          classPath = classPath,
          javaExe = context.stableJavaExecutable,
          timeout = timeout,
          workingDir = workingDir,
          onError = onError)
}

suspend fun runApplicationStarter(context: BuildContext,
                                  tempDir: Path,
                                  ideClasspath: Set<String>,
                                  arguments: List<String>,
                                  systemProperties: Map<String, Any> = emptyMap(),
                                  vmOptions: List<String> = emptyList(),
                                  timeout: Duration = DEFAULT_TIMEOUT) {
  Files.createDirectories(tempDir)
  val jvmArgs = mutableListOf<String>()
  val systemDir = tempDir.resolve("system")
  BuildUtils.addVmProperty(jvmArgs, "idea.home.path", context.paths.projectHome.toString())
  BuildUtils.addVmProperty(jvmArgs, "idea.system.path", systemDir.toString())
  BuildUtils.addVmProperty(jvmArgs, "idea.config.path", "$tempDir/config")
  // reproducible build - avoid touching module outputs, do not write classpath.index
  BuildUtils.addVmProperty(jvmArgs, "idea.classpath.index.enabled", "false")
  BuildUtils.addVmProperty(jvmArgs, "idea.builtin.server.disabled", "true")
  BuildUtils.addVmProperty(jvmArgs, "java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
  BuildUtils.addVmProperty(jvmArgs, "idea.platform.prefix", context.productProperties.platformPrefix)
  jvmArgs.addAll(BuildUtils.propertiesToJvmArgs(systemProperties.entries.map { it.key to it.value.toString() }))
  jvmArgs.addAll(vmOptions.takeIf { it.isNotEmpty() } ?: listOf("-Xmx1536m"))
  System.getProperty("intellij.build.${arguments.first()}.debug.port")?.let {
    jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:$it")
  }
  //jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5001")

  val effectiveIdeClasspath = LinkedHashSet(ideClasspath)

  val additionalPluginPaths = context.productProperties.getAdditionalPluginPaths(context)
  val additionalPluginIds = LinkedHashSet<String>()
  for (pluginPath in additionalPluginPaths) {
    for (jarFile in BuildUtils.getPluginJars(pluginPath)) {
      if (effectiveIdeClasspath.add(jarFile.toString())) {
        Span.current().addEvent("$jarFile from plugin $pluginPath")
        readPluginId(jarFile)?.let {
          additionalPluginIds.add(it)
        }
      }
    }
  }
  disableCompatibleIgnoredPlugins(context = context, configDir = tempDir.resolve("config"), explicitlyEnabledPlugins = additionalPluginIds)
  runIdea(context = context,
          mainClass = context.productProperties.mainClassName,
          args = arguments,
          jvmArgs = jvmArgs,
          classPath = effectiveIdeClasspath.toList(),
          timeout = timeout) {
    val logFile = systemDir.resolve("log").resolve("idea.log")
    if (Files.exists(logFile)) {
      val logFileToPublish = Files.createTempFile("idea-", ".log")
      logFile.copyTo(target = logFileToPublish, overwrite = true)
      context.notifyArtifactBuilt(logFileToPublish)
      Span.current().addEvent("log file $logFileToPublish attached to build artifacts")
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
      Span.current().addEvent("\'$pluginId\' will be disabled, because it\'s mentioned in \'compatiblePluginsToIgnore\'")
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
internal fun getCommandLineArgumentsForOpenPackages(context: CompilationContext, target: OsFamily? = null): List<String> {
  val file = context.paths.communityHomeDir.resolve("platform/platform-impl/resources/META-INF/OpenedPackages.txt")
  val os = when (target) {
    OsFamily.WINDOWS -> OS.Windows
    OsFamily.MACOS -> OS.macOS
    OsFamily.LINUX -> OS.Linux
    null -> OS.CURRENT
  }
  return JavaModuleOptions.readOptions(file, os)
}
