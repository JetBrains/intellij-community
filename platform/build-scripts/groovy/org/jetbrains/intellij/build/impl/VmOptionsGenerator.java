// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.intellij.build.ProductProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VmOptionsGenerator {
  public static List<String> computeVmOptions(boolean isEAP, ProductProperties productProperties) {
    final List<String> result = new ArrayList<String>();

    Map<String, String> memory = new LinkedHashMap<String, String>();
    DefaultGroovyMethods.putAll(memory, MEMORY_OPTIONS);
    memory.putAll(productProperties.getCustomJvmMemoryOptions());
    DefaultGroovyMethods.each(memory, new Closure<Boolean>(null, null) {
      public Boolean doCall(Object k, Object v) { return result.add(k + v); }
    });

    result.addAll(COMMON_VM_OPTIONS);

    if (isEAP) {
      int place = result.indexOf("-ea");
      if (place < 0) {
        place = DefaultGroovyMethods.findIndexOf(result, new Closure<Boolean>(null, null) {
          public Boolean doCall(String it) { return it.startsWith("-D"); }

          public Boolean doCall() {
            return doCall(null);
          }
        });
      }
      if (place < 0) place = result.size();
      result.add(place, "-XX:MaxJavaStackTraceDepth=10000");// must be consistent with `ConfigImportHelper#updateVMOptions`
    }


    return result;
  }

  public static List<Map.Entry<String, String>> getMEMORY_OPTIONS() {
    return MEMORY_OPTIONS;
  }

  @SuppressWarnings("SpellCheckingInspection") public static final List<String> COMMON_VM_OPTIONS =
    List.of("-XX:+IgnoreUnrecognizedVMOptions", "--jbr-illegal-access", "-Dsun.java2d.metal=true", "-XX:+UseG1GC",
            "-XX:SoftRefLRUPolicyMSPerMB=50", "-XX:CICompilerCount=2", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:-OmitStackTraceInFastThrow",
            "-ea", "-Dsun.io.useCanonCaches=false", "-Djdk.http.auth.tunneling.disabledSchemes=\"\"", "-Djdk.attach.allowAttachSelf=true",
            "-Djdk.module.illegalAccess.silent=true", "-Dkotlinx.coroutines.debug=off");
  private static final List<Map.Entry<String, String>> MEMORY_OPTIONS =
    List.of(Map.entry("-Xms", "128m"), Map.entry("-Xmx", "750m"), Map.entry("-XX:ReservedCodeCacheSize=", "512m"));
}
