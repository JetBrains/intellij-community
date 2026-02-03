package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.testFramework.NeedsIndex;

public class SmartTypeCompletionDfaTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/smartType/";
  }

  @Override
  protected void complete() {
    myItems = myFixture.complete(CompletionType.SMART);
  }

  private void doTest() {
    configureByTestName();
    checkResultByTestName();
  }

  private void checkResultByTestName() {
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  @NeedsIndex.Full
  public void testCastGenericQualifier() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testDontAutoCastWhenAlreadyCasted() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "s", "toString");
    myFixture.type("\n");
    checkResultByTestName();
  }

  @NeedsIndex.ForStandardLibrary
  public void testAutoCastWhenAlreadyCasted() {
    configureByTestName();
    myFixture.type("\n");
    checkResultByTestName();
  }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestCastedValueAfterCast() { doTest(); }

  @NeedsIndex.Full
  public void testSuggestInstanceofedValue() { doTest(); }

  public void testSuggestInstanceofedValueInTernary() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestInstanceofedValueInComplexIf() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestInstanceofedValueInElseNegated() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestInstanceofedValueAfterReturn() { doTest(); }

  public void testNoInstanceofedValueWhenBasicSuits() { doTest(); }

  public void testNoInstanceofedValueInElse() { doAntiTest(); }

  public void testNoInstanceofedValueInThenNegated() { doAntiTest(); }

  public void testNoInstanceofedValueInElseWithComplexIf() { doAntiTest(); }

  public void testInstanceofedInsideAnonymous() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testCastToTypeWithWildcard() { doTest(); }
}
