// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
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
    "-XX:JbrShrinkingGcMaxHeapFreeRatio=40", // IJPL-181469. Used in a couple with AppIdleMemoryCleaner.runGc()
    "-XX:SoftRefLRUPolicyMSPerMB=50", // IJPL-186317. Note: the effect is not visible in short perf tests; only in prolonged IDE usage
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
    },
    context.productProperties.platformPrefix,
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

  internal fun generate(
    isEAP: Boolean,
    bundledRuntime: BundledRuntime,
    customVmMemoryOptions: Map<String, String>,
    additionalVmOptions: List<String>,
    platformPrefix: String?,
  ): List<String> {
    val result = ArrayList<String>()

    val memory = LinkedHashMap<String, String>(customVmMemoryOptions)
    memory.putIfAbsent("-Xms", DEFAULT_MIN_HEAP)
    memory.putIfAbsent("-Xmx", DEFAULT_MAX_HEAP)  // must be the same as [com.intellij.diagnostic.MemorySizeConfigurator.DEFAULT_XMX]
    for ((k, v) in memory) {
      result += k + v
    }

    result += COMMON_VM_OPTIONS

    if (isMultiRoutingFileSystemEnabledForProduct(platformPrefix)) {
      result += MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
    }

    result += additionalVmOptions

    var index = result.indexOf("-ea")
    if (index < 0) index = result.indexOfFirst { it.startsWith("-D") }
    if (index < 0) index = result.size

    result.addAll(
      index,
      @ReviseWhenPortedToJDK("21", description = "Merge into `COMMON_VM_OPTIONS`")
      if (bundledRuntime.build.startsWith("17.")) {
        listOf(
          "-XX:CompileCommand=exclude,com/intellij/openapi/vfs/impl/FilePartNodeRoot,trieDescend",  // temporary workaround for crashes in С2 (JBR-4509)
        )
      }
      else listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:TieredOldPercentage=100000")
    )

    if (isEAP) {
      result.add(index, "-XX:MaxJavaStackTraceDepth=10000")  // must be consistent with `ConfigImportHelper#updateVMOptions`
    }

    return result
  }

  internal fun writeVmOptions(file: Path, vmOptions: Sequence<String>, separator: String) {
    Files.writeString(file, vmOptions.joinToString(separator, postfix = separator), StandardCharsets.US_ASCII)
  }
}
