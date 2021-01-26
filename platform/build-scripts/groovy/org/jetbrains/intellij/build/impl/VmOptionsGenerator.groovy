// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ProductProperties

@CompileStatic
final class VmOptionsGenerator {
  static final List<String> COMMON_VM_OPTIONS =
    [
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
    ]

  static final String defaultCodeCacheSetting = '-XX:ReservedCodeCacheSize=512m'

  static List<String> computeVmOptions(JvmArchitecture arch, boolean isEAP, ProductProperties productProperties) {
    List<String> commonVmOptions
    if (isEAP) {
      // must be consistent with `com.intellij.openapi.application.ConfigImportHelper#updateVMOptions`
      commonVmOptions = ["-XX:MaxJavaStackTraceDepth=10000"] + COMMON_VM_OPTIONS
    }
    else {
      commonVmOptions = COMMON_VM_OPTIONS
    }
    return vmMemoryOptions(arch, productProperties) + commonVmOptions
  }

  private static List<String> vmMemoryOptions(JvmArchitecture arch, ProductProperties productProperties) {
    switch (arch) {
      case JvmArchitecture.x32:
        return ['-Xms128m', '-Xmx512m', '-XX:ReservedCodeCacheSize=384m']
      // when changing, please review usages of `ProductProperties#getCustomJvmMemoryOptionsX64` and synchronize if necessary
      case JvmArchitecture.x64:
      case JvmArchitecture.aarch64:
        return productProperties.customJvmMemoryOptionsX64?.split(' ')?.toList() ?: ['-Xms128m', '-Xmx750m', defaultCodeCacheSetting]
      default:
        throw new AssertionError(arch)
    }
  }
}
