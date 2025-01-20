// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productRunner

import com.intellij.openapi.application.PathManager
import com.intellij.util.lang.HashMapZipFile
import com.intellij.util.xml.dom.readXmlAsModel
import com.jetbrains.plugin.structure.base.utils.exists
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.VmProperties
import org.jetbrains.intellij.build.impl.BuildUtils
import org.jetbrains.intellij.build.impl.getCommandLineArgumentsForOpenPackages
import org.jetbrains.intellij.build.io.DEFAULT_TIMEOUT
import org.jetbrains.intellij.build.io.runJava
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.time.Duration

/**
 * Internal function which runs IntelliJ process. Use [IntellijProductRunner.runProduct] instead.
 */
suspend fun runApplicationStarter(
  context: BuildContext,
  classpath: Collection<String>,
  args: List<String>,
  vmProperties: VmProperties = VmProperties(emptyMap()),
  vmOptions: List<String> = emptyList(),
  homePath: Path = context.paths.projectHome,
  timeout: Duration = DEFAULT_TIMEOUT,
  isFinalClassPath: Boolean = false,
) {
  val appStarterId = args.firstOrNull() ?: "appStarter"
  val tempDir = createTempDirectory(context.paths.tempDir, appStarterId)
  Files.createDirectories(tempDir)

  val jvmArgs = getCommandLineArgumentsForOpenPackages(context).toMutableList()

  val systemDir = tempDir.resolve("system")

  jvmArgs.addAll(vmProperties.mutate {
    put(PathManager.PROPERTY_HOME_PATH, homePath.toString())

    put("idea.system.path", systemDir.toString())
    put("idea.config.path", "$tempDir/config")

    put("idea.builtin.server.disabled", "true")
    put("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
    context.productProperties.platformPrefix?.let {
      put("idea.platform.prefix", it)
    }

    put("ij.dir.lock.debug", "true")
    put("intellij.log.to.json.stdout", "true")
  }.toJvmArgs())
  jvmArgs.addAll(vmOptions.takeIf { it.isNotEmpty() } ?: listOf("-Xmx2g"))
  val debugProperty = "intellij.build.$appStarterId.debug.port"
  System.getProperty(debugProperty)?.let {
    jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:$it")
  }

  val effectiveIdeClasspath = if (isFinalClassPath) classpath else prepareFlatClasspath(classpath = classpath, tempDir = tempDir, context = context)
  try {
    runJava(mainClass = context.ideMainClassName, args = args, jvmArgs = jvmArgs, classPath = effectiveIdeClasspath, javaExe = context.stableJavaExecutable, timeout = timeout) {
      val logFile = findLogFile(systemDir)
      if (logFile != null) {
        val logFileToPublish = Files.createTempFile(appStarterId, ".log")
        Files.copy(logFile, logFileToPublish, StandardCopyOption.REPLACE_EXISTING)
        context.notifyArtifactBuilt(logFileToPublish)
        Span.current().addEvent("log file $logFileToPublish attached to build artifacts")
      }
    }
  }
  catch (e: Exception) {
    throw RuntimeException(
      "The application '$appStarterId' failed. " +
      "To debug it in IDE, specify '-D$debugProperty=<some debug port>', " +
      "then click 'Attach debugger' in a debugger console or use 'Remote JVM  Debug' run configuration.",
      e
    )
  }
}

private fun prepareFlatClasspath(classpath: Collection<String>, tempDir: Path, context: BuildContext): LinkedHashSet<String> {
  val effectiveIdeClasspath = LinkedHashSet(classpath)

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

  return effectiveIdeClasspath
}

@OptIn(ExperimentalPathApi::class)
private fun findLogFile(systemDir: Path): Path? {
  val logDir = systemDir.resolve("log")
  val defaultLog = logDir.resolve("idea.log")
  if (defaultLog.exists()) {
    return defaultLog
  }
  //variants of IDEs which use 'PerProcessPathCustomizer' store idea.log in a subdirectory of logDir 
  return logDir.walk().filter { it.name == "idea.log" }.firstOrNull()
}

private fun readPluginId(pluginJar: Path): String? {
  if (!pluginJar.toString().endsWith(".jar") || !Files.isRegularFile(pluginJar)) {
    return null
  }

  try {
    HashMapZipFile.load(pluginJar).use { zip ->
      return readXmlAsModel(zip.getInputStream("META-INF/plugin.xml") ?: return null).getChild("id")?.content
    }
  }
  catch (_: NoSuchFileException) {
    return null
  }
}

private fun disableCompatibleIgnoredPlugins(context: BuildContext, configDir: Path, explicitlyEnabledPlugins: Set<String?>) {
  val toDisable = LinkedHashSet<String>()
  for (moduleName in context.productProperties.productLayout.compatiblePluginsToIgnore) {
    val pluginXml = context.findFileInModuleSources(moduleName, "META-INF/plugin.xml")!!
    val child = readXmlAsModel(Files.newInputStream(pluginXml)).getChild("id")
    val pluginId = child?.content ?: continue
    if (!explicitlyEnabledPlugins.contains(pluginId)) {
      toDisable.add(pluginId)
      Span.current().addEvent("'$pluginId' will be disabled, because it's mentioned in 'compatiblePluginsToIgnore'")
    }
  }
  if (!toDisable.isEmpty()) {
    Files.createDirectories(configDir)
    Files.writeString(configDir.resolve("disabled_plugins.txt"), java.lang.String.join("\n", toDisable))
  }
}

/**
 * Runs a java process which main class depends on IntelliJ platform modules.
 *
 * Use [IntellijProductRunner.runProduct] to run an actual IntelliJ product with special command line arguments.
 */
suspend fun runJavaForIntellijModule(
  context: CompilationContext,
  mainClass: String,
  args: List<String>,
  jvmArgs: Collection<String>,
  classPath: Collection<String>,
  timeout: Duration = DEFAULT_TIMEOUT,
  workingDir: Path? = null,
  onError: (() -> Unit)? = null,
) {
  runJava(
    mainClass = mainClass,
    args = args,
    jvmArgs = getCommandLineArgumentsForOpenPackages(context) + jvmArgs + listOf("-Dij.dir.lock.debug=true", "-Dintellij.log.to.json.stdout=true"),
    classPath = classPath,
    javaExe = context.stableJavaExecutable,
    timeout = timeout,
    workingDir = workingDir,
    onError = onError
  )
}