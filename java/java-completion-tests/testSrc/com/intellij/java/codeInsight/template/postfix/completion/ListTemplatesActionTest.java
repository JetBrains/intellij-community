// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.impl.ListTemplatesHandler;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ListTemplatesActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/template/postfix/listTemplates");
  }

  @Test
  public void testListTemplates() {
    doTest();
  }

  @Test
  public void testListTemplatesWithPrefix() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(true) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
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
      myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.java");
    });
  }
}
