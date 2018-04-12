// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightAdvRawStringLiteralsHighlightingTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advRawStringLiteral";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11_PREVIEW;
  }

  public void testStringAssignability() {
    doTestHighlighting();
  }

  public void testPasteInRawStringLiteral() {
    doTestPaste("class A {{String s = `q<caret>`;}}", "a\nb`\nc", "class A {{String s = ``qa\nb`\nc``;}}");
  }

  public void testPasteInRawStringLiteralNoTicInside() {
    doTestPaste("class A {{String s = `a<caret>`;}}", "a\nb\nc", "class A {{String s = `aa\nb\nc`;}}");
  }

  private void doTestHighlighting() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  private void doTestPaste(String documentTextBefore, String textToPaste, String expectedDocumentText) {
    myFixture.configureByText("plain.txt", "<selection>" + textToPaste + "</selection>");
    performCopy();
    myFixture.configureByText("a.java", documentTextBefore);
    performPaste();
    myFixture.checkResult(expectedDocumentText);
  }

  private void performPaste() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
  }

  private void performCopy() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY);
  }
}