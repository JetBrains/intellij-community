// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.AutoFormatTypedHandler;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class JavaReformatOnTypingTest extends BasePlatformTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    AutoFormatTypedHandler.setEnabledInTests(true);
    useSpacesAroundAssignmentOperator(true, myFixture.getProject());
  }

  @Override
  public void tearDown() throws Exception {
    AutoFormatTypedHandler.setEnabledInTests(false);
    //noinspection SuperTearDownInFinally
    super.tearDown();
  }

  private static void useSpacesAroundAssignmentOperator(boolean value, Project project) {
    CodeStyleSettings settings = CodeStyle.getSettings(project);
    CommonCodeStyleSettings common = settings.getCommonSettings(JavaLanguage.INSTANCE);
    common.SPACE_AROUND_ASSIGNMENT_OPERATORS = value;
  }

  public void test_DoNotInsertSpaceIfSettingDisabled() {
    useSpacesAroundAssignmentOperator(false, myFixture.getProject());
    doTest("class T { int<caret> }", "=", "class T { int=<caret> }");
  }

  public void test_DoNotInsertAfterIfSettingDisabled() {
    useSpacesAroundAssignmentOperator(false, myFixture.getProject());
    doTest("class T { int a=<caret> }", "2", "class T { int a=2 }");
  }

  public void test_AddSpacesAroundAssignmentOperator() {
    doTest("class T { int<caret> }", "=", "class T { int =<caret> }");
  }

  public void test_EmptyText() {
    doTest("<caret>", "x", "x<caret>");
  }

  public void test_IgnoreSpacePressedAfterAssignmentOperator() {
    doTest("class T { int<caret> }", "= ", "class T { int = <caret> }");
  }

  public void test_DoNotInsertDoubleSpaceBeforeAssignment() {
    doTest("class T { int <caret> }", "=", "class T { int =<caret> }");
  }

  public void test_DoNotInsertDoubleSpaceAnywhere() {
    doTest("class T { int <caret> }", "= ", "class T { int = <caret> }");
  }

  public void test_InsertSpaceForAssignments() {
    doTest("c <caret>", "+=a", "c += a<caret>");
    doTest("c <caret>", "-=a", "c -= a<caret>");
    doTest("c <caret>", "*=a", "c *= a<caret>");
    doTest("c <caret>", "/=a", "c /= a<caret>");
    doTest("c <caret>", "&=a", "c &= a<caret>");
    doTest("c <caret>", "%=a", "c %= a<caret>");
    doTest("c <caret>", "^=a", "c ^= a<caret>");
    doTest("c <caret>", "|=a", "c |= a<caret>");
    doTest("c <caret>", ">>=a", "c >>= a<caret>");
    doTest("c <caret>", "<<=a", "c <<= a<caret>");
    doTest("c <caret>", ">>>=a", "c >>>= a<caret>");
  }

  public void test_DoNotInsertSpaceIfNotAssignment() {
    doTest("1 <caret>", "!=a", "1 !=a<caret>");
    doTest("c <caret>", ">=a", "c >=a<caret>");
    doTest("c <caret>", "<=a", "c <=a<caret>");
  }

  public void test_IgnoreIfInsideStringLiteral() {
    doTest("class X { String s = \"xxxxx<caret>\"; }", "=x",
           "class X { String s = \"xxxxx=x<caret>\"; }");
  }

  public void test_InsideStringLiteral() {
    doTest("class X { String x = \"x<caret> }", "=y", "class X { String x = \"x=y<caret> }");
  }

  public void test_InsideCharLiteral() {
    doTest("class X { char x = '<caret>'; }", "=", "class X { char x = '='; }");
  }

  public void test_AfterOpenedQuote() {
    doTest("class X { char x = '<caret> }", "=", "class X { char x = '= }");
  }

  public void test_DistinguishAssignmentAndEquality() {
    doTest("class T { boolean b = 1 <caret> }", "==", "class T { boolean b = 1 ==<caret> }");
  }

  private void doTest(String before, String typing, String after) {
    myFixture.configureByText(JavaFileType.INSTANCE, before);
    myFixture.type(typing);
    myFixture.checkResult(after);
  }
}
