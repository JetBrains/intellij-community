// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.vfs;

public class Util {

  public static int getPid() {
    try {
      java.lang.management.RuntimeMXBean runtime =
        java.lang.management.ManagementFactory.getRuntimeMXBean();
      java.lang.reflect.Field jvm = null;
      jvm = runtime.getClass().getDeclaredField("jvm");
      jvm.setAccessible(true);
      sun.management.VMManagement mgmt =
        (sun.management.VMManagement)jvm.get(runtime);
      java.lang.reflect.Method pid_method =
        mgmt.getClass().getDeclaredMethod("getProcessId");
      pid_method.setAccessible(true);

      return (Integer)pid_method.invoke(mgmt);
    }
    catch (Throwable t) {
      return -1;
    }
  }
}
