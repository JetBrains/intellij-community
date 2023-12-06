// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.LiteralAsArgToStringEqualsInspection;

public class LiteralAsArgToStringEqualsFixTest extends IGQuickFixesTestCase {

  public void testSimple() {
    doTest("Flip 'equals()'",
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

  @SuppressWarnings("EqualsBetweenInconvertibleTypes")
  public void testNoQualifier() {
    assertQuickfixNotAvailable("Flip 'equals()'",
           "class X {" +
           "  boolean x() {" +
           "    return equals(\"x\"/**/);" +
           "  }" +
           "}");
  }

  public void testParentheses() {
    doTest("Flip 'equalsIgnoreCase()'",
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

  @Override
  protected BaseInspection getInspection() {
    return new LiteralAsArgToStringEqualsInspection();
  }
}
