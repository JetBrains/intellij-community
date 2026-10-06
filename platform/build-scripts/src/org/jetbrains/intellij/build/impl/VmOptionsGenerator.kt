// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.isLanguageServer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

private const val DEFAULT_MIN_HEAP = "128m"
private const val DEFAULT_MAX_HEAP = "2048m"

/** The lines of `bin/common.vmoptions` under the community root, without the blank lines and the `#` lines. */
private val COMMON_VM_OPTIONS: List<String> by lazy {
  Files.readAllLines(COMMUNITY_ROOT.communityRoot.resolve("bin/common.vmoptions")).filter { it.isNotBlank() && !it.startsWith('#') }
}

/** duplicates `RepositoryHelper.CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY` */
private const val CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY = "intellij.plugins.custom.built.in.repository.url"

internal fun generateVmOptions(context: BuildContext, extra: List<String>): List<String> = generateVmOptions(
  isEAP = context.applicationInfo.isEAP,
  customMemoryVmOptions = context.productProperties.customJvmMemoryOptions,
  additionalVmOptions = context.productProperties.additionalVmOptions + customPluginRepositoryOptions(context) + extra,
  platformPrefix = context.productProperties.platformPrefix,
  isHeadless = context.isLanguageServer,
)

internal fun generateVmOptions(
  isEAP: Boolean,
  customMemoryVmOptions: Map<String, String>,
  additionalVmOptions: List<String>,
  platformPrefix: String?,
  isHeadless: Boolean,
): List<String> {
  val result = ArrayList<String>(50)
  result += memoryVmOptions(customMemoryVmOptions)
  result += COMMON_VM_OPTIONS
  result += multiRoutingFileSystemVmOptions(platformPrefix)
  result += additionalVmOptions
  if (isEAP) {
    insertEapVmOptions(result)
  }
  if (isHeadless) {
    result.removeIf { it.contains("awt.") || it.contains("swing.") || it.contains("java2d.") || it.contains("skiko.") }
    result += "-Djava.awt.headless=true"
  }
  return result
}

/** The memory lines: [customMemoryVmOptions], then the default `-Xms` and `-Xmx` when they do not state them. */
internal fun memoryVmOptions(customMemoryVmOptions: Map<String, String>): List<String> {
  val memoryOptions = LinkedHashMap<String, String>(customMemoryVmOptions)
  memoryOptions.putIfAbsent("-Xms", DEFAULT_MIN_HEAP)
  memoryOptions.putIfAbsent("-Xmx", DEFAULT_MAX_HEAP)
  return memoryOptions.map { (key, value) -> key + value }
}

/** The lines that turn on the multi-routing file system, when the product of [platformPrefix] uses it. */
internal fun multiRoutingFileSystemVmOptions(platformPrefix: String?): List<String> {
  return if (isMultiRoutingFileSystemEnabledForProduct(platformPrefix)) MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS else emptyList()
}

/**
 * Inserts the line that an EAP build adds: before `-ea`, else before the first `-D` line, else at the end.
 *
 * The tool `product-files` ports this rule, because the dev-dist launch model states no EAP flag.
 */
internal fun insertEapVmOptions(vmOptions: MutableList<String>) {
  var index = vmOptions.indexOf("-ea")
  if (index < 0) index = vmOptions.indexOfFirst { it.startsWith("-D") }
  if (index < 0) index = vmOptions.size
  vmOptions.add(index, "-XX:MaxJavaStackTraceDepth=10000")  // must be consistent with `ConfigImportHelper#updateVMOptions`
}

private fun customPluginRepositoryOptions(context: BuildContext): List<String> {
  val artifactsServer = context.proprietaryBuildTools.artifactsServer
  if (artifactsServer != null && context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
    val paths = listOf(context.nonBundledPlugins.name, "${context.nonBundledPlugins.name}/${context.nonBundledPluginsToBePublished.name}")
    val urls = paths.mapNotNull { path ->
      val url = artifactsServer.urlToArtifact(context, "${path}/plugins.xml")
      if (url != null && url.startsWith("http:")) {
        context.messages.logErrorAndThrow("Insecure artifact server: ${url}")
      }
      url
    }
    if (urls.isNotEmpty()) {
      return listOf("-D${CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY}=${urls.joinToString(",")}")
    }
  }
  return emptyList()
}

/** The vmoptions lines the distribution of [os] adds after those of the product. */
internal fun osVmOptions(os: OsFamily, platformPrefix: String?): List<String> = when (os) {
  OsFamily.MACOS -> listOf("-Dapple.awt.application.appearance=system")
  OsFamily.LINUX -> listOfNotNull(
    "-Dsun.tools.attach.tmp.only=true",
    "-Dawt.lock.fair=true",
    // disabled for Gateway until JBR supports system tray in the Wayland toolkit (IJPL-231661/JBR-9966)
    "-Dawt.toolkit.name=auto".takeIf { platformPrefix != "Gateway" },
    "-Dsun.java2d.vulkan=True",
  )
  OsFamily.WINDOWS -> emptyList()
}

internal fun writeVmOptions(file: Path, vmOptions: List<String>, separator: String) {
  Files.writeString(file, vmOptions.joinToString(separator, postfix = separator), StandardCharsets.US_ASCII)
}
