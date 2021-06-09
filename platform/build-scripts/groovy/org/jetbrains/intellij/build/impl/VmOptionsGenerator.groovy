// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.ProductProperties

@CompileStatic
final class VmOptionsGenerator {
  @SuppressWarnings('SpellCheckingInspection')
  static final List<String> COMMON_VM_OPTIONS = List.of(
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

  static final Map<String, String> MEMORY_OPTIONS = Map.of(
    '-Xms', '128m',
    '-Xmx', '750m',
    '-XX:ReservedCodeCacheSize=', '512m')

  static List<String> computeVmOptions(boolean isEAP, ProductProperties productProperties) {
    List<String> result = new ArrayList<>()

    Map<String, String> memory =  new LinkedHashMap<>()
    memory.putAll(MEMORY_OPTIONS)
    memory.putAll(productProperties.customJvmMemoryOptions)
    memory.each {k,v -> result.add(k + v) }

    if (isEAP) {
      // must be consistent with `com.intellij.openapi.application.ConfigImportHelper#updateVMOptions`
      result.add('-XX:MaxJavaStackTraceDepth=10000')
    }

    result.addAll(COMMON_VM_OPTIONS)

    if (productProperties.useSplash) {
      //noinspection SpellCheckingInspection
      result.add('-Dsplash=true')
    }

    return result
  }
}
