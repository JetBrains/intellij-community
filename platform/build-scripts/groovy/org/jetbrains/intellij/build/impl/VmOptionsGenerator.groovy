// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.ProductProperties

@CompileStatic
final class VmOptionsGenerator {
  @SuppressWarnings('SpellCheckingInspection')
  static final List<String> COMMON_VM_OPTIONS = List.of(
    '-XX:+IgnoreUnrecognizedVMOptions',
    '-XX:+UseG1GC',
    '-XX:SoftRefLRUPolicyMSPerMB=50',
    '-XX:CICompilerCount=2',
    '-XX:+HeapDumpOnOutOfMemoryError',
    '-XX:-OmitStackTraceInFastThrow',
    '-ea',
    '-Dsun.io.useCanonCaches=false',
    '-Djdk.http.auth.tunneling.disabledSchemes=""',
    '-Djdk.attach.allowAttachSelf=true',
    '-Djdk.module.illegalAccess.silent=true',
    '-Dkotlinx.coroutines.debug=off')

  static final List<Map.Entry<String, String>> MEMORY_OPTIONS = List.of(
    Map.entry('-Xms', '128m'),
    Map.entry('-Xmx', '750m'),
    Map.entry('-XX:ReservedCodeCacheSize=', '512m'))

  static List<String> computeVmOptions(boolean isEAP, ProductProperties productProperties) {
    List<String> result = new ArrayList<>()

    Map<String, String> memory = new LinkedHashMap<>()
    memory.putAll(MEMORY_OPTIONS)
    memory.putAll(productProperties.customJvmMemoryOptions)
    memory.each { k, v -> result.add(k + v) }

    result.addAll(COMMON_VM_OPTIONS)

    if (isEAP) {
      int place = result.indexOf('-ea')
      if (place < 0) place = result.findIndexOf { it.startsWith('-D') }
      if (place < 0) place = result.size()
      result.add(place, '-XX:MaxJavaStackTraceDepth=10000')  // must be consistent with `ConfigImportHelper#updateVMOptions`
    }

    return result
  }
}
