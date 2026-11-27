// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.LiteralAsArgToStringEqualsInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class LiteralAsArgToStringEqualsFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new LiteralAsArgToStringEqualsInspection()));

  @Test
  public void testSimple() {
    ig.get().doTest("Flip 'equals()'",
           """
             class X {
               void test(String s) {
                 System.out.println(s.equals("<caret>foo"));
               }
             }""",

           """
             class X {
               void test(String s) {
                 System.out.println("foo".equals(s));
               }
             }"""
    );
  }

  @Test
  @SuppressWarnings("EqualsBetweenInconvertibleTypes")
  public void testNoQualifier() {
    ig.get().assertQuickfixNotAvailable("Flip 'equals()'",
           "class X {" +
           "  boolean x() {" +
           "    return equals(\"x\"/**/);" +
           "  }" +
           "}");
  }

  @Test
  public void testParentheses() {
    ig.get().doTest("Flip 'equalsIgnoreCase()'",
           """
             class X {
               void test(String s) {
                 System.out.println(((s)).equalsIgnoreCase((/*1*/("<caret>foo"))));
               }
             }""",

           """
             class X {
               void test(String s) {
                 System.out.println(/*1*/"foo".equalsIgnoreCase(s));
               }
             }"""
    );
  }

}
