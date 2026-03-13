// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
import org.jetbrains.intellij.build.ArtifactsServer
import org.jetbrains.intellij.build.BuildContext
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

/** duplicates RepositoryHelper.CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY */
private const val CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY = "intellij.plugins.custom.built.in.repository.url"

fun generateVmOptions(context: BuildContext, extra: List<String> = emptyList()): List<String> =
  generateVmOptions(
    isEAP = context.applicationInfo.isEAP,
    customVmMemoryOptions = context.productProperties.customJvmMemoryOptions,
    additionalVmOptions = context.productProperties.additionalVmOptions.let {
      val url = computeCustomPluginRepositoryUrl(context)
      if (url == null) it else it + "-D${CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY}=${url}"
    } + extra,
    platformPrefix = context.productProperties.platformPrefix,
    headless = context.options.isLanguageServer,
  )

 internal fun generateVmOptions(
   isEAP: Boolean,
   customVmMemoryOptions: Map<String, String>,
   additionalVmOptions: List<String>,
   platformPrefix: String?,
   headless: Boolean = false,
 ): List<String> {
   val result = ArrayList<String>()
   listOfNotNull(
     LinkedHashMap<String, String>(customVmMemoryOptions).apply {
       putIfAbsent("-Xms", DEFAULT_MIN_HEAP)
       putIfAbsent("-Xmx", DEFAULT_MAX_HEAP)
     }
       .map { (k, v) -> k + v },
     COMMON_VM_OPTIONS,
     if (isMultiRoutingFileSystemEnabledForProduct(platformPrefix)) {
       MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
     }
     else null,
     additionalVmOptions,
   )
     .flatten()
     .filterNotTo(result) {
       if (headless) {
         it.contains("awt.") || it.contains("swing.") ||
         it.contains("java2d.") || it.contains("skiko.")
       }
       else false
     }

   if (isEAP) {
     var index = result.indexOf("-ea")
     if (index < 0) index = result.indexOfFirst { it.startsWith("-D") }
     if (index < 0) index = result.size
     result.add(index, "-XX:MaxJavaStackTraceDepth=10000")  // must be consistent with `ConfigImportHelper#updateVMOptions`
   }

   if (headless) {
     var index = result.indexOf("-ea") + 1
     if (index <= 0) index = result.indexOfFirst { it.startsWith("-D") }
     if (index < 0) index = result.size
     result.add(index, "-Djava.awt.headless=true")
   }
   return result
 }

private fun computeCustomPluginRepositoryUrl(context: BuildContext): String? {
  val artifactsServer = context.proprietaryBuildTools.artifactsServer
  if (artifactsServer != null && context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
    val paths = listOf(context.nonBundledPlugins.name, "${context.nonBundledPlugins.name}/${context.nonBundledPluginsToBePublished.name}")
    return paths.mapNotNull {
      getBuiltinPluginsRepoUrl(artifactsServer, it, context)
    }.takeIf { it.isNotEmpty() }?.joinToString(",")
  }
  return null
}


internal fun writeVmOptions(file: Path, vmOptions: List<String>, separator: String) {
  Files.writeString(file, vmOptions.joinToString(separator, postfix = separator), StandardCharsets.US_ASCII)
}

private fun getBuiltinPluginsRepoUrl(artifactsServer: ArtifactsServer, path: String, context: BuildContext): String? {
  val builtinPluginsRepoUrl = artifactsServer.urlToArtifact(context, "${path}/plugins.xml")
  if (builtinPluginsRepoUrl != null) {
    if (builtinPluginsRepoUrl.startsWith("http:")) {
      context.messages.logErrorAndThrow("Insecure artifact server: ${builtinPluginsRepoUrl}")
    }
    return builtinPluginsRepoUrl
  }
  return null
}
