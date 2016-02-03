/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.io.File;

public class JavaTypingTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testMulticaretIndentLBrace() {
    doTest('{');
  }

  public void testMulticaretIndentRBrace() {
    doTest('}');
  }

  public void testMulticaretSkipSemicolon() {
    doTest(';');
  }

  public void testMulticaretSkipGt() {
    doTest('>');
  }

  public void testMulticaretInsertGt() {
    doTest('<');
  }

  public void testMulticaretSkipRParen() {
    doTest(')');
  }

  public void testMulticaretInsertRParen() {
    doTest('(');
  }

  public void testMulticaretSkipQuote() {
    doTest('"');
  }

  public void testMulticaretInsertQuote() {
    doTest('"');
  }

  public void testColumnMode() {
    myFixture.configureByFile(getTestName(true) + "_before.java");
    ((EditorEx)myFixture.getEditor()).setColumnMode(true);
    myFixture.type('(');
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  public void testInvalidInitialSyntax() {
    myFixture.configureByFile(getTestName(true) + "_before.java");
    myFixture.type('\\');
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments(); // emulates background commit after typing first character
    myFixture.type('\\');
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  public void testFixIfByBrace() {
    doTest('{');
  }

  public void testFixIfByBraceNewObject() {
    doTest('{');
  }

  public void testFixIfByBraceCompositeCondition() {
    doTest('{');
  }

  public void testFixWhileByBrace() {
    doTest('{');
  }
  
  public void testInsertPairParenBeforeTryBlock() {
    doTest('(');
  }
  
  public void testInsertPairedBraceBeforeDot() {
    doTest('{');
  }
  
  public void testInsertPairedBraceForLambdaBody() {
    doTest('{');
  }
  
  private void doTest(char c) {
    myFixture.configureByFile(getTestName(true) + "_before.java");
    myFixture.type(c);
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/java/java-tests/testData/codeInsight/typing";
  }
}
