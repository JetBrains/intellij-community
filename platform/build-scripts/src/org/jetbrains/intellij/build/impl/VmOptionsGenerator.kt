// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiConsumer

@Suppress("IdentifierGrammar")
object VmOptionsGenerator {
  /** duplicates RepositoryHelper.CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY */
  private const val CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY = "intellij.plugins.custom.built.in.repository.url"
  @Suppress("SpellCheckingInspection")
  private val COMMON_VM_OPTIONS: List<String> = listOf(
    "-XX:+UseG1GC",
    "-XX:SoftRefLRUPolicyMSPerMB=50",
    "-XX:CICompilerCount=2",
    "-XX:+HeapDumpOnOutOfMemoryError",
    "-XX:-OmitStackTraceInFastThrow",
    "-XX:+IgnoreUnrecognizedVMOptions",  // allowing the JVM to start even with outdated options stuck in user configs
    "-XX:CompileCommand=exclude,com/intellij/openapi/vfs/impl/FilePartNodeRoot,trieDescend", // temporary workaround for crashes in С2 (JBR-4509)
    "-ea",
    "-Dsun.io.useCanonCaches=false",
    "-Dsun.java2d.metal=true",
    "-Djbr.catch.SIGABRT=true",
    "-Djdk.http.auth.tunneling.disabledSchemes=\"\"",
    "-Djdk.attach.allowAttachSelf=true",
    "-Djdk.module.illegalAccess.silent=true",
    "-Dkotlinx.coroutines.debug=off"
  )

  private const val DEFAULT_XMS = 128

  /** Must be the same as [com.intellij.diagnostic.MemorySizeConfigurator.DEFAULT_XMX]. */
  private const val DEFAULT_XMX = 2048

  private val MEMORY_OPTIONS: Map<String, String> = linkedMapOf(
    "-Xms" to "${DEFAULT_XMS}m",
    "-Xmx" to "${DEFAULT_XMX}m",
    "-XX:ReservedCodeCacheSize=" to "512m"
  )

  fun computeVmOptions(context: BuildContext): List<String> {
    var additionalVmOptions = context.productProperties.additionalVmOptions
    val customPluginRepositoryUrl = computeCustomPluginRepositoryUrl(context)
    if (customPluginRepositoryUrl != null) {
      additionalVmOptions = additionalVmOptions.add("-D$CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY=$customPluginRepositoryUrl")  
    }
    return computeVmOptions(context.applicationInfo.isEAP, context.productProperties.customJvmMemoryOptions,
                            additionalVmOptions)
  }

  private fun computeCustomPluginRepositoryUrl(context: BuildContext): String? {
    val artifactsServer = context.proprietaryBuildTools.artifactsServer
    if (artifactsServer == null || !context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
      return null
    }
    val builtinPluginsRepoUrl = artifactsServer.urlToArtifact(context, "${context.applicationInfo.productCode}-plugins/plugins.xml")
                                ?: return null
    if (builtinPluginsRepoUrl.startsWith("http:")) {
      context.messages.error("Insecure artifact server: $builtinPluginsRepoUrl")
    }
    return builtinPluginsRepoUrl
  }

  fun computeVmOptions(isEAP: Boolean, customJvmMemoryOptions: Map<String, String>?, additionalVmOptions: List<String>? = null): List<String> {
    val result = ArrayList<String>()

    if (customJvmMemoryOptions != null) {
      val memory = LinkedHashMap<String, String>()
      memory.putAll(MEMORY_OPTIONS)
      memory.putAll(customJvmMemoryOptions)
      memory.forEach(BiConsumer { k, v -> result.add(k + v) })
    }

    result.addAll(COMMON_VM_OPTIONS)

    if (additionalVmOptions != null) {
      result.addAll(additionalVmOptions)
    }

    if (isEAP) {
      var place = result.indexOf("-ea")
      if (place < 0) place = result.indexOfFirst { it.startsWith("-D") }
      if (place < 0) place = result.size
      // must be consistent with `ConfigImportHelper#updateVMOptions`
      result.add(place, "-XX:MaxJavaStackTraceDepth=10000")
    }

    return result
  }

  fun writeVmOptions(file: Path, vmOptions: List<String>, separator: String) {
    Files.writeString(file, vmOptions.joinToString(separator = separator, postfix = separator), StandardCharsets.US_ASCII)
  }
}
