// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.isLanguageServer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

private const val DEFAULT_MIN_HEAP = "128m"
private const val DEFAULT_MAX_HEAP = "2048m"

private val COMMON_VM_OPTIONS: List<String> = listOf(
  "-XX:JbrShrinkingGcMaxHeapFreeRatio=40", // IJPL-181469. Used in a couple with AppIdleMemoryCleaner.runGc()
  "-XX:ReservedCodeCacheSize=512m",
  "-XX:+HeapDumpOnOutOfMemoryError",
  "-XX:-OmitStackTraceInFastThrow",
  "-XX:CICompilerCount=2",
  "-XX:+IgnoreUnrecognizedVMOptions",  // allowing the JVM to start even with outdated options stuck in user configs
  "-XX:+UnlockDiagnosticVMOptions",
  "-XX:TieredOldPercentage=100000",
  "-XX:+UseCompactObjectHeaders",  // expected to become the default in JBR 29
  "--sun-misc-unsafe-memory-access=allow",  // temporary option, to be removed before adopting JBR 29
  "-ea",
  "-Dsun.io.useCanonCaches=false",
  "-Dsun.java2d.metal=true",
  "-Djbr.catch.SIGABRT=true",
  "-Djdk.http.auth.tunneling.disabledSchemes=\"\"",
  "-Djdk.attach.allowAttachSelf=true",
  "-Djdk.module.illegalAccess.silent=true",
  "-Djdk.nio.maxCachedBufferSize=2097152",
  "-Djava.util.zip.use.nio.for.zip.file.access=true", // IJPL-149160
  "-Dkotlinx.coroutines.debug=off",
  "-Dskiko.rendering.useScreenMenuBar=false",
)

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
  val memoryOptions = LinkedHashMap<String, String>(customMemoryVmOptions).apply {
    putIfAbsent("-Xms", DEFAULT_MIN_HEAP)
    putIfAbsent("-Xmx", DEFAULT_MAX_HEAP)
  }

  val result = ArrayList<String>(50)
  result += memoryOptions.map { (k, v) -> k + v }
  result += COMMON_VM_OPTIONS
  if (isMultiRoutingFileSystemEnabledForProduct(platformPrefix)) {
    result += MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
  }
  result += additionalVmOptions
  if (isEAP) {
    var index = result.indexOf("-ea")
    if (index < 0) index = result.indexOfFirst { it.startsWith("-D") }
    if (index < 0) index = result.size
    result.add(index, "-XX:MaxJavaStackTraceDepth=10000")  // must be consistent with `ConfigImportHelper#updateVMOptions`
  }
  if (isHeadless) {
    result.removeIf { it.contains("awt.") || it.contains("swing.") || it.contains("java2d.") || it.contains("skiko.") }
    result += "-Djava.awt.headless=true"
  }
  return result
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

internal fun writeVmOptions(file: Path, vmOptions: List<String>, separator: String) {
  Files.writeString(file, vmOptions.joinToString(separator, postfix = separator), StandardCharsets.US_ASCII)
}
