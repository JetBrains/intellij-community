// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.ProductProperties
import java.util.function.BiConsumer

object VmOptionsGenerator {
  @JvmField
  val COMMON_VM_OPTIONS: List<String> = java.util.List.of("-XX:+IgnoreUnrecognizedVMOptions", "--jbr-illegal-access", "-Dsun.java2d.metal=true",
                                                          "-XX:+UseG1GC",
                                                          "-XX:SoftRefLRUPolicyMSPerMB=50", "-XX:CICompilerCount=2", "-XX:+HeapDumpOnOutOfMemoryError",
                                                          "-XX:-OmitStackTraceInFastThrow",
                                                          "-ea", "-Dsun.io.useCanonCaches=false", "-Djdk.http.auth.tunneling.disabledSchemes=\"\"",
                                                          "-Djdk.attach.allowAttachSelf=true",
                                                          "-Djdk.module.illegalAccess.silent=true", "-Dkotlinx.coroutines.debug=off")
  private val MEMORY_OPTIONS: Map<String, String> = linkedMapOf("-Xms" to "128m", "-Xmx" to "750m", "-XX:ReservedCodeCacheSize=" to "512m")

  @JvmStatic
  fun computeVmOptions(isEAP: Boolean, productProperties: ProductProperties): List<String> {
    val result = ArrayList<String>()
    val memory = LinkedHashMap<String, String>()
    memory.putAll(MEMORY_OPTIONS)
    memory.putAll(productProperties.customJvmMemoryOptions)
    memory.forEach(BiConsumer { k, v -> result.add(k + v) })
    result.addAll(COMMON_VM_OPTIONS)
    if (isEAP) {
      var place = result.indexOf("-ea")
      if (place < 0) {
        place = result.indexOfFirst { it.startsWith("-D") }
        if (place < 0) {
          place = result.size
        }
      }
      // must be consistent with `ConfigImportHelper#updateVMOptions`
      result.add(place, "-XX:MaxJavaStackTraceDepth=10000")
    }
    return result
  }
}