// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class UnnecessarySuperConstructorInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new UnnecessarySuperConstructorInspection());

  @Test
  public void testQualifiedSuper() {
    inspectionFixture.get().doTest("""
             class Outer {
               class Super {}
               class Inner extends Super {
                 Inner(Outer outer) {
                   outer.super();
                 }
               }
             }""");
  }

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("""
             class Simple {
               Simple() {
                 /*'super()' is unnecessary*/super()/**/;
               }
             }""");
  }
  
  @Test
  public void testFlexibleConstructorBody() {
    inspectionFixture.get().doTest("""
             class Friend {
               Friend() {
                 System.out.println(0);
               }
             }
             class Flexible extends Friend {
             
               Flexible() {
                 System.out.println("before");
                 super();
                 System.out.println("after");
               }
             }
             """);
  }
}
