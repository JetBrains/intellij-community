// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

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
public class UnnecessarySuperConstructorInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new UnnecessarySuperConstructorInspection());

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
