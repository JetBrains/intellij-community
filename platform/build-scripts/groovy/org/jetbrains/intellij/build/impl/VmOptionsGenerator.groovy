// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ProductProperties

/**
 * @author nik
 */
@CompileStatic
class VmOptionsGenerator {
  static final List<String> COMMON_VM_OPTIONS =
    [
      '-XX:+UseConcMarkSweepGC', '-XX:SoftRefLRUPolicyMSPerMB=50',
      '-ea',
      '-XX:CICompilerCount=2',
      '-Dsun.io.useCanonPrefixCache=false', '-Djava.net.preferIPv4Stack=true',
      '-Djdk.http.auth.tunneling.disabledSchemes=""',
      '-XX:+HeapDumpOnOutOfMemoryError',
      '-XX:-OmitStackTraceInFastThrow',
      '-Djdk.attach.allowAttachSelf',
      '-Dkotlinx.coroutines.debug=off',
      '-Djdk.module.illegalAccess.silent=true',
    ]

  static String computeVmOptions(JvmArchitecture arch, boolean isEAP, ProductProperties productProperties) {
    String options = vmOptionsForArch(arch, productProperties) + " " + computeCommonVmOptions(isEAP)
    return options
  }

  static String computeCommonVmOptions(boolean isEAP) {
    String options = COMMON_VM_OPTIONS.join(" ")
    if (isEAP) {
      //must be consistent with com.intellij.openapi.application.ConfigImportHelper.updateVMOptions
      options += " -XX:MaxJavaStackTraceDepth=10000"
    }
    return options
  }

  static String vmOptionsForArch(JvmArchitecture arch, ProductProperties productProperties) {
    switch (arch) {
      // NOTE: when changing, please review usages of ProductProperties.getCustomJvmMemoryOptionsX64 and synchronize if necessary  
      case JvmArchitecture.x32: return "-server -Xms128m -Xmx512m -XX:ReservedCodeCacheSize=240m"
      case JvmArchitecture.x64: return productProperties.customJvmMemoryOptionsX64 ?: "-Xms128m -Xmx750m -XX:ReservedCodeCacheSize=240m"
    }
    throw new AssertionError(arch)
  }
}

