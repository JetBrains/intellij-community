// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor.setShowTemplatesInTests;

abstract public class PostfixTemplateTestCase extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/postfix/templates/" + getSuffix();
  }

  @NotNull
  protected abstract String getSuffix();

  protected void doTest() {
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.type('\t');
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResultByFile(getTestName(true) + "_after.java", true);
  }

  protected void doTestCompletion(@Nullable String text) {
    setShowTemplatesInTests(true, getTestRootDisposable());
    myFixture.configureByFile(getTestName(true) + ".java");
    LookupElement[] elements = myFixture.completeBasic();
    if (text != null) {
      for (LookupElement element : elements) {
        LookupElementPresentation presentation = new LookupElementPresentation();
        element.renderElement(presentation);
        if (presentation.getItemText().toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)) ||
            presentation.getTypeText().toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
          myFixture.getLookup().setCurrentItem(element);
          break;
        }
      }
    }
    myFixture.type('\n');
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResultByFile(getTestName(true) + "_after.java", true);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }
}
