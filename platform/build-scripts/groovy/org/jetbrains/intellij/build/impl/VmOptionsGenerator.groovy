/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ProductProperties

/**
 * @author nik
 */
@CompileStatic
class VmOptionsGenerator {
  private static final String COMMON_VM_OPTIONS = "-XX:+UseConcMarkSweepGC -XX:SoftRefLRUPolicyMSPerMB=50 -ea " +
                                          "-Dsun.io.useCanonCaches=false -Djava.net.preferIPv4Stack=true " +
                                          "-Djdk.http.auth.tunneling.disabledSchemes=\"\" " +
                                          "-XX:+HeapDumpOnOutOfMemoryError -XX:-OmitStackTraceInFastThrow"

  static String computeVmOptions(JvmArchitecture arch, boolean isEAP, ProductProperties productProperties, String yourkitSessionName = null) {
    String options = vmOptionsForArch(arch, productProperties) + " " + computeCommonVmOptions(isEAP)
    if (yourkitSessionName != null) {
      options += " " + yourkitOptions(yourkitSessionName, arch.fileSuffix)
    }
    return options
  }

  static String computeCommonVmOptions(boolean isEAP) {
    String options = COMMON_VM_OPTIONS
    if (isEAP) {
      options += " -XX:MaxJavaStackTraceDepth=-1"
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

  static String yourkitOptions(String sessionName, String fileSuffix) {
    "-agentlib:yjpagent$fileSuffix=probe_disable=*,disablealloc,disabletracing,onlylocal,disableexceptiontelemetry,delay=10000,sessionname=$sessionName".trim()
  }
}

