// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
public class ThreadLocalSetWithNullInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new ThreadLocalSetWithNullInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("""
      final class Simple {
          public static void main(String[] args) {
              ThreadLocal<Integer> threadLocal = new ThreadLocal<>();
              (getThreadLocal(threadLocal)).<warning descr="'ThreadLocal.set()' with null as an argument may cause memory leak">set</warning>((null));
              threadLocal.<warning descr="'ThreadLocal.set()' with null as an argument may cause memory leak">set</warning>(null);
          }
      
          private static ThreadLocal<Integer> getThreadLocal(ThreadLocal<Integer> threadLocal) {
              return threadLocal;
          }
      }
      """);
  }
}
