// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.ReviseWhenPortedToJDK
import org.jetbrains.intellij.build.BuildContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

object VmOptionsGenerator {
  private const val DEFAULT_MIN_HEAP = "128m"
  private const val DEFAULT_MAX_HEAP = "2048m"

  @Suppress("SpellCheckingInspection")
  private val COMMON_VM_OPTIONS: List<String> = listOf(
    "-XX:ReservedCodeCacheSize=512m",
    "-XX:+HeapDumpOnOutOfMemoryError",
    "-XX:-OmitStackTraceInFastThrow",
    "-XX:CICompilerCount=2",
    "-XX:+IgnoreUnrecognizedVMOptions",  // allowing the JVM to start even with outdated options stuck in user configs
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
  )

  /** duplicates RepositoryHelper.CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY */
  private const val CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY = "intellij.plugins.custom.built.in.repository.url"

  fun generate(context: BuildContext): List<String> = generate(
    context.applicationInfo.isEAP,
    context.bundledRuntime,
    context.productProperties.customJvmMemoryOptions,
    context.productProperties.additionalVmOptions.let {
      val customPluginRepositoryUrl = computeCustomPluginRepositoryUrl(context)
      if (customPluginRepositoryUrl == null) it
      else it + "-D${CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY}=${customPluginRepositoryUrl}"
    }
  )

  private fun computeCustomPluginRepositoryUrl(context: BuildContext): String? {
    val artifactsServer = context.proprietaryBuildTools.artifactsServer
    if (artifactsServer != null && context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
      val builtinPluginsRepoUrl = artifactsServer.urlToArtifact(context, "${context.nonBundledPlugins.name}/plugins.xml")
      if (builtinPluginsRepoUrl != null) {
        if (builtinPluginsRepoUrl.startsWith("http:")) {
          context.messages.error("Insecure artifact server: ${builtinPluginsRepoUrl}")
        }
        return builtinPluginsRepoUrl
      }
    }
    return null
  }

  internal fun generate(isEAP: Boolean, bundledRuntime: BundledRuntime, customVmMemoryOptions: Map<String, String>, additionalVmOptions: List<String>): List<String> {
    val result = ArrayList<String>()

    val memory = LinkedHashMap<String, String>(customVmMemoryOptions)
    memory.putIfAbsent("-Xms", DEFAULT_MIN_HEAP)
    memory.putIfAbsent("-Xmx", DEFAULT_MAX_HEAP)  // must be the same as [com.intellij.diagnostic.MemorySizeConfigurator.DEFAULT_XMX]
    for ((k, v) in memory) {
      result += k + v
    }

    result += COMMON_VM_OPTIONS

    @ReviseWhenPortedToJDK("21", description = "Merge into `COMMON_VM_OPTIONS`")
    result += if (bundledRuntime.build.startsWith("17.")) {
      listOf(
        "-XX:CompileCommand=exclude,com/intellij/openapi/vfs/impl/FilePartNodeRoot,trieDescend",  // temporary workaround for crashes in С2 (JBR-4509)
        "-XX:SoftRefLRUPolicyMSPerMB=50",
      )
    }
    else {
      listOf(
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:TieredOldPercentage=100000",
      )
    }

    result += additionalVmOptions

    if (isEAP) {
      var place = result.indexOf("-ea")
      if (place < 0) place = result.indexOfFirst { it.startsWith("-D") }
      if (place < 0) place = result.size
      // must be consistent with `ConfigImportHelper#updateVMOptions`
      result.add(place, "-XX:MaxJavaStackTraceDepth=10000")
    }

    return result
  }

  internal fun writeVmOptions(file: Path, vmOptions: Sequence<String>, separator: String) {
    Files.writeString(file, vmOptions.joinToString(separator = separator, postfix = separator), StandardCharsets.US_ASCII)
  }
}
