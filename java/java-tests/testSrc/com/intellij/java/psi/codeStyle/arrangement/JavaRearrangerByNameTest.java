// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.codeStyle.excludedFiles.NamedScopeDescriptor;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PROTECTED;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PUBLIC;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.BY_NAME;

public class JavaRearrangerByNameTest extends AbstractJavaRearrangerTest {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getCommonSettings().BLANK_LINES_AROUND_METHOD = 0;
    getCommonSettings().BLANK_LINES_AROUND_CLASS = 0;
  }

  public void testOnlyNameCondition() {
    doTest("""
             class Test {
               public void setI() {}
               public void getI() {}
               public void test() {}
             }""",
           """
             class Test {
               public void getI() {}
               public void setI() {}
               public void test() {}
             }""",
           List.of(nameRule("get.*")));
  }

  public void testNameConditionAndOthers() {
    doTest("""
             class Test {
               private void getInner() {}
               public void getOuter() {}
               protected void test() {}
             """,
           """
             class Test {
               public void getOuter() {}
               protected void test() {}
               private void getInner() {}
             """,
           List.of(nameRule("get.*", PUBLIC), rule(PROTECTED)));
  }

  public void testNameAndSort() {
    doTest("""
             class Test {
               private void getC() {}
               public void test() {}
               public void getA() {}
               public void getB() {}
             """,
           """
             class Test {
               public void getA() {}
               public void getB() {}
               private void getC() {}
               public void test() {}
             """,
           List.of(ruleWithOrder(BY_NAME, nameRule("get.*"))));
  }

  public void testWithFormattingDisabled() {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    NamedScopeDescriptor descriptor = new NamedScopeDescriptor("testScope");
    descriptor.setPattern("file:*.java");
    settings.getExcludedFiles().addDescriptor(descriptor);

    doTest("""
             class Test {
               private void getC() {}
               public void test() {}
               public void getA() {}
               public void getB() {}
             """,
           """
             class Test {
               private void getC() {}
               public void test() {}
               public void getA() {}
               public void getB() {}
             """,
           List.of(ruleWithOrder(BY_NAME, nameRule("get.*"))));
  }
}