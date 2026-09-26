// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.impl.ListTemplatesHandler;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class ListTemplatesActionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testListTemplates() {
    doTest();
  }

  public void testListTemplatesWithPrefix() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + ".java");
    new ListTemplatesHandler().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
    LookupElement[] elements = myFixture.getLookupElements();
    assertNotNull(elements);

    LookupElement targetElement = null;
    for (LookupElement element : elements) {
      if (element.getLookupString().equals(".not")) {
        targetElement = element;
      }
    }
    assertNotNull(targetElement);
    myFixture.getLookup().setCurrentItem(targetElement);
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/postfix/listTemplates";
  }
}
