// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ThreadLocalSetWithNullInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("""
      final class Simple {
          public static void main(String[] args) {
              ThreadLocal<Integer> threadLocal = new ThreadLocal<>();
              (getThreadLocal(threadLocal)).set((null));
              threadLocal.set(null);
          }
      
          private static ThreadLocal<Integer> getThreadLocal(ThreadLocal<Integer> threadLocal) {
              return threadLocal;
          }
      }
      """);
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ThreadLocalSetWithNullInspection();
  }
}
