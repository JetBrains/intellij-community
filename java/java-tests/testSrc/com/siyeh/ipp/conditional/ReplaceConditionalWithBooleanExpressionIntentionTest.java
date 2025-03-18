// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.conditional;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceConditionalWithBooleanExpressionIntentionTest extends IPPTestCase {

  public void testPrecedence() {
    doTest("""
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

  public void testIncomplete() {
    doTest("""
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

  public void testIgnoreConstantBranches() {
    doTestIntentionNotAvailable("""
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
