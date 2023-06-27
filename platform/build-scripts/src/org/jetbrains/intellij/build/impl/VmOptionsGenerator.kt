// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.ProductProperties
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiConsumer

@Suppress("IdentifierGrammar")
object VmOptionsGenerator {
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

  fun computeVmOptions(isEAP: Boolean, productProperties: ProductProperties): List<String> =
    computeVmOptions(isEAP, productProperties.customJvmMemoryOptions, productProperties.additionalVmOptions)

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
