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
    '-Dkotlinx.coroutines.debug=off',
    '-XX:+IgnoreUnrecognizedVMOptions', //todo[kb] remove when we find a way to remove outdated options like -XX:+UseConcMarkSweepGC
    '--add-opens=java.base/java.lang=ALL-UNNAMED',
    '--add-opens=java.base/java.util=ALL-UNNAMED',
    '--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED',
    '--add-opens=java.base/sun.nio.ch=ALL-UNNAMED',
    '--add-opens=java.desktop/java.awt=ALL-UNNAMED',
    '--add-opens=java.desktop/java.awt.event=ALL-UNNAMED',
    '--add-opens=java.desktop/javax.swing=ALL-UNNAMED',
    '--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED',
    '--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.awt=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.font=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.java2d=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.swing=ALL-UNNAMED',
    '--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED',
    '--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED',
    '--add-opens=java.desktop/com.apple.laf=ALL-UNNAMED'
    )

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
    return result
  }
}
