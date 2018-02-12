/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.editorActions.AutoFormatTypedHandler;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

public class JavaReformatOnTypingTest extends LightPlatformCodeInsightFixtureTestCase {
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    AutoFormatTypedHandler.setEnabledInTests(true);
    useSpacesAroundAssignmentOperator(true, myFixture.getProject(), JavaLanguage.INSTANCE);
  }

  @Override
  public void tearDown() throws Exception {
    AutoFormatTypedHandler.setEnabledInTests(false);
    //noinspection SuperTearDownInFinally
    super.tearDown();
  }

  private static void useSpacesAroundAssignmentOperator(boolean value, Project project, Language language) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    CommonCodeStyleSettings common = settings.getCommonSettings(language);
    common.SPACE_AROUND_ASSIGNMENT_OPERATORS = value;
  }
  
  public void test_DoNotInsertSpaceIfSettingDisabled() {
    useSpacesAroundAssignmentOperator(false, myFixture.getProject(), JavaLanguage.INSTANCE);
    doTest("class T { int<caret> }", "=", "class T { int=<caret> }");
  }
  
  public void test_DoNotInsertAfterIfSettingDisabled() {
    useSpacesAroundAssignmentOperator(false, myFixture.getProject(), JavaLanguage.INSTANCE);
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
