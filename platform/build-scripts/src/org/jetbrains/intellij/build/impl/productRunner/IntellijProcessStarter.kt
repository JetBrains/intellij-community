// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productRunner

import com.intellij.openapi.application.PathManager
import com.intellij.util.lang.HashMapZipFile
import com.intellij.util.xml.dom.readXmlAsModel
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.BuildUtils
import org.jetbrains.intellij.build.impl.getCommandLineArgumentsForOpenPackages
import org.jetbrains.intellij.build.io.DEFAULT_TIMEOUT
import org.jetbrains.intellij.build.io.runJava
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.time.Duration

/**
 * Internal function which runs IntelliJ process. Use [IntellijProductRunner.runProduct] instead.
 */
suspend fun runApplicationStarter(
  context: BuildContext,
  tempDir: Path,
  ideClasspath: Collection<String>,
  arguments: List<String>,
  systemProperties: Map<String, Any> = emptyMap(),
  vmOptions: List<String> = emptyList(),
  homePath: Path = context.paths.projectHome,
  timeout: Duration = DEFAULT_TIMEOUT,
) {
  Files.createDirectories(tempDir)
  val jvmArgs = mutableListOf<String>()
  val systemDir = tempDir.resolve("system")
  BuildUtils.addVmProperty(jvmArgs, PathManager.PROPERTY_HOME_PATH, homePath.toString())
  BuildUtils.addVmProperty(jvmArgs, "idea.system.path", systemDir.toString())
  BuildUtils.addVmProperty(jvmArgs, "idea.config.path", "$tempDir/config")
  BuildUtils.addVmProperty(jvmArgs, "idea.builtin.server.disabled", "true")
  BuildUtils.addVmProperty(jvmArgs, "java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
  BuildUtils.addVmProperty(jvmArgs, "idea.platform.prefix", context.productProperties.platformPrefix)
  jvmArgs.addAll(BuildUtils.propertiesToJvmArgs(systemProperties.entries.map { it.key to it.value.toString() }))
  jvmArgs.addAll(vmOptions.takeIf { it.isNotEmpty() } ?: listOf("-Xmx2g"))
  System.getProperty("intellij.build.${arguments.first()}.debug.port")?.let {
    jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:$it")
  }

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
  runIdea(
    context = context,
    mainClass = context.productProperties.mainClassName,
    args = arguments,
    jvmArgs = jvmArgs,
    classPath = effectiveIdeClasspath.toList(),
    timeout = timeout
  ) {
    val logFile = systemDir.resolve("log").resolve("idea.log")
    if (Files.exists(logFile)) {
      val logFileToPublish = Files.createTempFile("idea-", ".log")
      Files.copy(logFile, logFileToPublish, StandardCopyOption.REPLACE_EXISTING)
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
    HashMapZipFile.load(pluginJar).use { zip ->
      return readXmlAsModel(zip.getInputStream("META-INF/plugin.xml") ?: return null).getChild("id")?.content
    }
  }
  catch (ignore: NoSuchFileException) {
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
      Span.current().addEvent("\'$pluginId\' will be disabled, because it\'s mentioned in \'compatiblePluginsToIgnore\'")
    }
  }
  if (!toDisable.isEmpty()) {
    Files.createDirectories(configDir)
    Files.writeString(configDir.resolve("disabled_plugins.txt"), java.lang.String.join("\n", toDisable))
  }
}

/**
 * Internal function which runs a java process for IntelliJ product. Use [IntellijProductRunner.runProduct] instead.
 */
suspend fun runIdea(context: CompilationContext,
                    mainClass: String,
                    args: List<String>,
                    jvmArgs: List<String>,
                    classPath: List<String>,
                    timeout: Duration = DEFAULT_TIMEOUT,
                    workingDir: Path? = null,
                    onError: (() -> Unit)? = null) {
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