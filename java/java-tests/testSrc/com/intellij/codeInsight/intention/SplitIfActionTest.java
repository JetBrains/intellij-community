package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.intention.impl.SplitIfAction;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author mike
 */
public class SplitIfActionTest extends LightCodeInsightTestCase {
  public void test1() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).ELSE_ON_NEW_LINE= true;
    doTest();
  }

  public void test2() throws Exception {
    doTest();
  }

  public void test3() throws Exception {
    doTest();
  }

  public void test4() throws Exception {
    doTest();
  }

  public void test5() throws Exception {
    doTest();
  }

  public void testParenthesis() throws Exception {
    doTest();
  }

  public void testOrParenthesis() throws Exception {
    doTest();
  }

  public void testComment() throws Exception {
    doTest();
  }

  public void testWithoutSpaces() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    configureByFile("/codeInsight/splitIfAction/before" + getTestName(false)+ ".java");
    perform();
    checkResultByFile("/codeInsight/splitIfAction/after" + getTestName(false) + ".java");
  }

   public void test8() throws Exception {
    configureByFile("/codeInsight/splitIfAction/beforeOrAndMixed.java");
    SplitIfAction action = new SplitIfAction();
    assertFalse(action.isAvailable(getProject(), getEditor(), getFile()));
  }


  private void perform() throws Exception {
    SplitIfAction action = new SplitIfAction();
    assertTrue(action.isAvailable(getProject(), getEditor(), getFile()));
    action.invoke(getProject(), getEditor(), getFile());
  }
}
