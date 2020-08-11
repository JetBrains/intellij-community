// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ProductProperties

@CompileStatic
class VmOptionsGenerator {
  static final List<String> COMMON_VM_OPTIONS =
    [
      '-XX:+UseG1GC', '-XX:SoftRefLRUPolicyMSPerMB=50',
/* Android Studio: removed by Change Ie7351d92
      '-ea',
Android Studio: removed by Change Ie7351d92 */
      '-XX:CICompilerCount=2',
      '-Dsun.io.useCanonPrefixCache=false',
      '-Djdk.http.auth.tunneling.disabledSchemes=""',
/* Android Studio: removed by Change Ie7351d92
      '-XX:+HeapDumpOnOutOfMemoryError',
      '-XX:-OmitStackTraceInFastThrow',
Android Studio: removed by Change Ie7351d92 */
      '-Djdk.attach.allowAttachSelf=true',
      '-Dkotlinx.coroutines.debug=off',
      '-Djdk.module.illegalAccess.silent=true',
      '-Djna.nosys=true',  // Android Studio: added by Change Ie7351d92
      '-Djna.boot.library.path=',  // Android Studio: added by Change Ie7351d92
      '-Didea.vendor.name=Google',  // Android Studio
    ]

  static List<String> computeVmOptions(JvmArchitecture arch, boolean isEAP, ProductProperties productProperties) {
    List<String> commonVmOptions
    if (isEAP) {
      //must be consistent with com.intellij.openapi.application.ConfigImportHelper.updateVMOptions
      // Android Studio: modified by Change Ie7351d92
      commonVmOptions = COMMON_VM_OPTIONS + ["-XX:MaxJavaStackTraceDepth=10000", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:-OmitStackTraceInFastThrow", "-ea"]
    }
    else {
      commonVmOptions = COMMON_VM_OPTIONS
    }
    return vmMemoryOptions(arch, productProperties) + commonVmOptions
  }

  private static List<String> vmMemoryOptions(JvmArchitecture arch, ProductProperties productProperties) {
    switch (arch) {
      // NOTE: when changing, please review usages of ProductProperties.getCustomJvmMemoryOptionsX64 and synchronize if necessary  
      // Android Studio: modified by Change Ie7351d92
      case JvmArchitecture.x32: return ['-server', '-Xms256m', '-Xmx768m', '-XX:ReservedCodeCacheSize=240m']
      case JvmArchitecture.x64: return productProperties.customJvmMemoryOptionsX64?.split(' ')?.toList() ?: ['-Xms256m', '-Xmx1280m', '-XX:ReservedCodeCacheSize=240m']
    }
    throw new AssertionError(arch)
  }
}

