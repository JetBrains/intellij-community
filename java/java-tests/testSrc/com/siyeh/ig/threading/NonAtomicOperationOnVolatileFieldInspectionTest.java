// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
@SuppressWarnings("NonAtomicOperationOnVolatileField")
public class NonAtomicOperationOnVolatileFieldInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new NonAtomicOperationOnVolatileFieldInspection());

  @Test
  public void testWriteToDifferentInstance() {
    inspectionFixture.get().doTest("""
             class A {
                 private volatile int v;

                 A copy() {
                     A a = new A();
                     a.v = v;
                     return a;
                 }
             }""");
  }

  @Test
  public void testPostfix() {
    inspectionFixture.get().doTest("class X {" +
           "  private volatile int v = 1;" +
           "  void m() {" +
           "    (/*Non-atomic operation on volatile field 'v'*/v/**/)++;" +
           "  }" +
           "}");
  }

  @Test
  public void testPrefix() {
    inspectionFixture.get().doTest("class X {" +
           "  private volatile int v = 1;" +
           "  void m() {" +
           "    ++(/*Non-atomic operation on volatile field 'v'*/v/**/);" +
           "  }" +
           "}");
  }

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("class X {" +
           "  private volatile int v = 1;" +
           "  void m() {" +
           "    (/*Non-atomic operation on volatile field 'v'*/v/**/) = 3 + v;" +
           "  }" +
           "}");
  }

  @Test
  public void testQualified() {
    inspectionFixture.get().doTest("class X {" +
           "  private volatile int v = 1;" +
           "  void m() {" +
           "    (this)./*Non-atomic operation on volatile field 'v'*/v/**/ = 2 * (this).v;" +
           "  }" +
           "}");
  }

  @Test
  public void testQualified2() {
    //noinspection Convert2Lambda
    inspectionFixture.get().doTest("class Segment {" +
           "  private volatile int count = 0;" +
           "  public void x() {" +
           "      Runnable r = new Runnable() {" +
           "        @Override" +
           "        public void run() {" +
           "          (Segment.this./*Non-atomic operation on volatile field 'count'*/count/**/)++;" +
           "        }" +
           "      };" +
           "  }" +
           "}");
  }

  @Test
  public void testDifferentlyQualified() {
    inspectionFixture.get().doTest("class X {" +
           "  private volatile int v = 1;" +
           "  void m() {" +
           "    /*Non-atomic operation on volatile field 'v'*/v/**/ = 2 * this.v;" +
           "  }" +
           "}");
  }
}