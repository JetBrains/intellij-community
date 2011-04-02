
package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;

public class TabCompletionTest extends LightCompletionTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testMethodCallCompletionWithTab() throws Exception {
    configureByFile("/codeInsight/completion/normal/MethodLookup3.java");
    checkResultByFile("/codeInsight/completion/normal/MethodLookup3_After.java");
  }

  public void testTabInXml() throws Throwable {
    configureByFile("/codeInsight/completion/normal/TabInXml.xml");
    checkResultByFile("/codeInsight/completion/normal/TabInXml_After.xml");
  }
  
  public void testTabInXml2() throws Throwable {
    configureByFile("/codeInsight/completion/normal/TabInXml2.xml");
    checkResultByFile("/codeInsight/completion/normal/TabInXml2_After.xml");
  }

  @Override
  protected void complete() {
    super.complete();
    selectItem(myItems[0], '\t');
  }

}