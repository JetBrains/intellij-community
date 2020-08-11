// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ProductProperties

@CompileStatic
class VmOptionsGenerator {
  static final List<String> COMMON_VM_OPTIONS =
    [
/* Android Studio: removed by Change I4fd91d66
      '-XX:+UseConcMarkSweepGC',
Android Studio: removed by Change I4fd91d66 */
      '-XX:+UseG1GC',  // Android Studio: added by Change I4fd91d66
      '-XX:SoftRefLRUPolicyMSPerMB=50',
      '-XX:CICompilerCount=2',
/* Android Studio: removed by Change Ie7351d92
      '-XX:+HeapDumpOnOutOfMemoryError',
      '-XX:-OmitStackTraceInFastThrow',
      '-ea',
Android Studio: removed by Change Ie7351d92 */
      '-Dsun.io.useCanonPrefixCache=false',
      '-Djdk.http.auth.tunneling.disabledSchemes=""',
      '-Djdk.attach.allowAttachSelf=true',
      '-Djdk.module.illegalAccess.silent=true',
      '-Dkotlinx.coroutines.debug=off',
      '-Djna.nosys=true',  // Android Studio: added by Change Ie7351d92
      '-Djna.boot.library.path=',  // Android Studio: added by Change Ie7351d92
      '-Didea.vendor.name=Google',  // Android Studio
    ]

  static final String defaultCodeCacheSetting = '-XX:ReservedCodeCacheSize=512m'

  static List<String> computeVmOptions(JvmArchitecture arch, boolean isEAP, ProductProperties productProperties) {
    List<String> commonVmOptions
    if (isEAP) {
      // must be consistent with `com.intellij.openapi.application.ConfigImportHelper#updateVMOptions`
      // Android Studio: modified by Change Ie7351d92
      commonVmOptions = ["-XX:MaxJavaStackTraceDepth=10000", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:-OmitStackTraceInFastThrow", "-ea"] + COMMON_VM_OPTIONS
    }
    else {
      commonVmOptions = COMMON_VM_OPTIONS
    }
    return vmMemoryOptions(arch, productProperties) + commonVmOptions
  }

  private static List<String> vmMemoryOptions(JvmArchitecture arch, ProductProperties productProperties) {
    switch (arch) {
      // when changing, please review usages of `ProductProperties#getCustomJvmMemoryOptionsX64` and synchronize if necessary
      // Android Studio: modified by Change Ie7351d92
      case JvmArchitecture.x32: return ['-server', '-Xms256m', '-Xmx768m', '-XX:ReservedCodeCacheSize=384m']
      case JvmArchitecture.x64: return productProperties.customJvmMemoryOptionsX64?.split(' ')?.toList() ?: ['-Xms256m', '-Xmx1280m', defaultCodeCacheSetting]
    }
    throw new AssertionError(arch)
  }
}
