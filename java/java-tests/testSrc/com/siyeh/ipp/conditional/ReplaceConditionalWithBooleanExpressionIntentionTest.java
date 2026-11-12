// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.conditional;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ReplaceConditionalWithBooleanExpressionIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture);

  @Test
  public void testPrecedence() {
    ipp.get().doTest("""
             class X {
                 void test(boolean foo, boolean bar, boolean other) {
                     boolean c = false;
                     boolean b = /*_Replace '?:' with boolean expression*/foo ? other : c || bar;
                 }
             }""",
           """
             class X {
                 void test(boolean foo, boolean bar, boolean other) {
                     boolean c = false;
                     boolean b = foo && other || !foo && (c || bar);
                 }
             }""");
  }

  @Test
  public void testIncomplete() {
    ipp.get().doTest("""
             class X {
                 boolean test(boolean a, boolean b, boolean c) {
                     return a /*_Replace '?:' with boolean expression*/? b ? c;
                 }
             }""",
           """
             class X {
                 boolean test(boolean a, boolean b, boolean c) {
                     return a && (b ? c) || !a &&;
                 }
             }""");
  }

  @Test
  public void testIgnoreConstantBranches() {
    ipp.get().doTestIntentionNotAvailable("""
             class X {
               void x(int i) {
                 Boolean b = i == 10 ? null : /*_Replace '?:' with boolean expression*/getBoolean(i);
               }
             
               boolean getBoolean(int i) {
                 return i < 100;
               }
             }
             """);
  }
}
