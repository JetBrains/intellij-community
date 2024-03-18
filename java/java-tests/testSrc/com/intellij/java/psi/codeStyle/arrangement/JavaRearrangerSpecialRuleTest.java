// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.FIELD;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.METHOD;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.BY_NAME;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class JavaRearrangerSpecialRuleTest extends AbstractJavaRearrangerTest {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    getCommonSettings().BLANK_LINES_AROUND_METHOD = 0;
    getCommonSettings().BLANK_LINES_AROUND_CLASS = 0;
  }

  public void test_name_and_visibility_conditions() {
    doTest("""
             class Test {
               public void getI() {}
               public void setI() {}
               private void test() {}
             }""", """
             class Test {
               public void setI() {}
               public void getI() {}
               private void test() {}
             }""", List.of(rule(PUBLIC), nameRule("get.*", PUBLIC)));
  }

  public void test_modifier_conditions() {
    doTest("""
             class Test {
               public static void a() {}
               public void b() {}
             }
             """, """
             class Test {
               public void b() {}
               public static void a() {}
             }
             """, List.of(rule(PUBLIC), rule(PUBLIC, STATIC)));
  }

  public void test_multi_modifier_condition() {
    doTest("""
             class Test {
               public abstract void a() {}
               public static void b() {}
               public void c() {}
             }
             """, """
             class Test {
               public void c() {}
               public static void b() {}
               public abstract void a() {}
             }
             """, List.of(rule(PUBLIC), rule(PUBLIC, STATIC),
                          rule(PUBLIC, ABSTRACT)));
  }

  public void test_modifier_conditions_with_sort() {
    doTest("""
             class Test {
               public void e() {}
               public static void d() {}
               public void c() {}
               public static void b() {}
               public void a() {}
             }
             """, """
             class Test {
               public void a() {}
               public void c() {}
               public void e() {}
               public static void b() {}
               public static void d() {}
             }
             """, List.of(ruleWithOrder(BY_NAME, rule(PUBLIC)),
                          ruleWithOrder(BY_NAME, rule(PUBLIC, STATIC))));
  }

  public void test_different_entries_type_with_modifier_conditions() {
    doTest("""
             class Test {
               public static void b() {}
               public void a() {}
             }
             """, """
             class Test {
               public void a() {}
               public static void b() {}
             }
             """, List.of(rule(FIELD, PUBLIC), rule(FIELD),
                          rule(METHOD, PUBLIC), rule(METHOD),
                          rule(METHOD, PUBLIC, ABSTRACT), rule(METHOD, ABSTRACT),
                          rule(FIELD, PUBLIC, STATIC), rule(FIELD, STATIC),
                          rule(METHOD, PUBLIC, STATIC), rule(METHOD, STATIC)));
  }
}
