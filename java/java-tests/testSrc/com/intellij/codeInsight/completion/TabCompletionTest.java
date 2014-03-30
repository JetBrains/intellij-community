
package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;

public class TabCompletionTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal";
  }

  public void testMethodCallCompletionWithTab() throws Exception {
    configureByFile("MethodLookup3.java");
    checkResultByFile("MethodLookup3_After.java");
  }

  public void _testMethodCallBeforeAnnotation() {
    String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.completeBasic();
    myFixture.type("tos\t");
    checkResultByFile(name + "_After.java");
  }

  public void testReplaceThisWithSuper() throws Throwable {
    configureByFile("ReplaceThisWithSuper.java");
    checkResultByFile("ReplaceThisWithSuper_After.java");
  }

  public void testTabInXml() throws Throwable {
    configureByFile("TabInXml.xml");
    checkResultByFile("TabInXml_After.xml");
  }

  public void testTabInXml2() throws Throwable {
    configureByFile("TabInXml2.xml");
    checkResultByFile("TabInXml2_After.xml");
  }

  @Override
  protected void complete() {
    super.complete();
    selectItem(myItems[0], '\t');
  }

}