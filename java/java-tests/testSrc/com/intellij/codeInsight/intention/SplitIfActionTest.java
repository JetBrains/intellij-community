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
    configureByFile("/codeInsight/splitIfAction/before1.java");
    perform();
    checkResultByFile("/codeInsight/splitIfAction/after1.java");
  }

  public void test2() throws Exception {
    configureByFile("/codeInsight/splitIfAction/before2.java");
    perform();
    checkResultByFile("/codeInsight/splitIfAction/after2.java");
  }

  public void test3() throws Exception {
    configureByFile("/codeInsight/splitIfAction/before3.java");
    perform();
    checkResultByFile("/codeInsight/splitIfAction/after3.java");
  }

  public void test4() throws Exception {
    configureByFile("/codeInsight/splitIfAction/before4.java");
    perform();
    checkResultByFile("/codeInsight/splitIfAction/after4.java");
  }

  public void test5() throws Exception {
    configureByFile("/codeInsight/splitIfAction/before5.java");
    perform();
    checkResultByFile("/codeInsight/splitIfAction/after5.java");
  }

  public void test6() throws Exception {
    configureByFile("/codeInsight/splitIfAction/beforeParenthesis.java");
    perform();
    checkResultByFile("/codeInsight/splitIfAction/afterParenthesis.java");
  }

  public void test7() throws Exception {
    configureByFile("/codeInsight/splitIfAction/beforeOrParenthesis.java");
    perform();
    checkResultByFile("/codeInsight/splitIfAction/afterOrParenthesis.java");
  }



  private void perform() throws Exception {
    SplitIfAction action = new SplitIfAction();
    assertTrue(action.isAvailable(getProject(), getEditor(), getFile()));
    action.invoke(getProject(), getEditor(), getFile());
  }
}
