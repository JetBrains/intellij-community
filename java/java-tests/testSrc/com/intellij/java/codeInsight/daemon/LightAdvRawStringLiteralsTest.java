// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.RedundantBackticksAroundRawStringLiteralInspection;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LightAdvRawStringLiteralsTest extends LightCodeInsightFixtureTestCase {

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

  public void testRawToStringTransformation() {
    doTestIntention(QuickFixBundle.message("convert.to.string.text"));
  }

  public void testStringToRawTransformation() { doTestIntention(QuickFixBundle.message("convert.to.raw.string.text")); }
  public void testStringToRawTransformationWithWrongSeparators() { doTestIntention(QuickFixBundle.message("convert.to.raw.string.text")); }

  public void testStringToRawTransformationLeadingTics() { doTestIntention(QuickFixBundle.message("convert.to.raw.string.text")); }

  public void testStringToRawTransformationOnlyTics() {
    myFixture.configureByFile(getTestName(false) + ".java");
    assertEmpty(myFixture.filterAvailableIntentions(QuickFixBundle.message("convert.to.raw.string.text")));
  }

  public void testStringToRawTransformationWithTicsInside() {
    doTestIntention(QuickFixBundle.message("convert.to.raw.string.text"));
  }

  public void testSplitRawStringLiteral() { doTestIntention("Split raw string literal"); }
  public void testSplitRawStringLiteralDisabledOnTic() {
    myFixture.configureByFile(getTestName(false) + ".java");
    assertEmpty(myFixture.filterAvailableIntentions("Split raw string literal"));
  }

  public void testPasteInRawStringLiteral() {
    doTestPaste("class A {{String s = `q<caret>`;}}", "a\nb`\nc", "class A {{String s = ``qa\nb`\nc``;}}");
  }

  public void testPasteInRawStringLiteralWrongLineSeparator() {
    doTestPaste("class A {{String s = `<caret>a`;}}", "\r", "class A {{String s = `\na`;}}");
  }

  public void testPasteInRawStringStaringWithTics() {
    doTestPaste("class A {{String s = `<caret>q`;}}", "``a\n``b", "class A {{String s = \"``\" + `a\n" + "``bq`;}}");
  }

  public void testPasteInRawStringEndingWithTics() {
    doTestPaste("class A {{String s = `q<caret>`;}}", "``a\n``b``", "class A {{String s = `q``a\n" + "``b`+ \"``\";}}");
  }

  public void testPasteInRawStringStaringWithStartingTics() {
    doTestPaste("class A {{String s = `q<caret>`;}}", "`a", "class A {{String s = ``q`a``;}}");
  }

  public void testPasteInRawStringLiteralOneAndTwo() {
    doTestPaste("class A {{String s = `q<caret>`;}}", "a\n``b`\nc", "class A {{String s = ```qa\n``b`\nc```;}}");
  }

  public void testPasteInRawStringLiteralOnlyTwo() {
    doTestPaste("class A {{String s = `q<caret>`;}}", "a\n``b\nc", "class A {{String s = `qa\n``b\nc`;}}");
  }

  public void testPasteInRawStringLiteralNoTicInside() {
    doTestPaste("class A {{String s = `a<caret>`;}}", "a\nb\nc", "class A {{String s = `aa\nb\nc`;}}");
  }

  public void testRawStringValue() {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());

    PsiExpression rawStringLiteral = factory.createExpressionFromText("``abc``", null);
    assertEquals("abc", ((PsiLiteralExpressionImpl)rawStringLiteral).getRawString());

    rawStringLiteral = factory.createExpressionFromText("``abc`", null);
    assertEquals("abc", ((PsiLiteralExpressionImpl)rawStringLiteral).getRawString());

    rawStringLiteral = factory.createExpressionFromText("`abc````", null);
    assertEquals("abc", ((PsiLiteralExpressionImpl)rawStringLiteral).getRawString());
  }

  public void testReduceNumberOfBackticks() {
    doTestRedundantBackticks("class A {{String s = <caret>```a`b```;}}", "class A {{String s = ``a`b``;}}");
  }

  public void testReduceNumberOfBackticksSimple() {
    doTestRedundantBackticks("class A {{String s = <caret>```a``b```;}}", "class A {{String s = `a``b`;}}");
  }

  private void doTestRedundantBackticks(String beforeText, String afterText) {
    myFixture.configureByText("a.java", beforeText);
    myFixture.enableInspections(new RedundantBackticksAroundRawStringLiteralInspection());
    IntentionAction reduceNumberOfBackticks = myFixture.getAvailableIntention("Reduce number of backticks");
    assertNotNull(reduceNumberOfBackticks);
    myFixture.launchAction(reduceNumberOfBackticks);
    myFixture.checkResult(afterText);
  }

  public void testTypingOpeningTic() {
    myFixture.configureByFile(getTestName(false ) + ".java");
    myFixture.type('`');
    myFixture.checkResultByFile(getTestName(false) + ".after.java");
    myFixture.type('`');
    myFixture.checkResultByFile(getTestName(false) + ".1.after.java");
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

  private void doTestIntention(final String hint) {
    myFixture.configureByFile(getTestName(false) + ".java");
    List<IntentionAction> actions = myFixture.filterAvailableIntentions(hint);
    assertNotEmpty(actions);
    myFixture.launchAction(actions.get(0));
    myFixture.checkResultByFile(getTestName(false) + ".after.java");
  }

  private void performPaste() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
  }

  private void performCopy() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY);
  }
}