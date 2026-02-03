// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template;

import com.intellij.codeInsight.completion.JavaCompletionAutoPopupTestCase;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;

import java.util.Arrays;

public class LiveTemplateAutoPopupTest extends JavaCompletionAutoPopupTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
  }

  public void testDoNotShowTemplateWithoutShortcutInAutoPopup() {
    myFixture.configureByText("a.java", "<caret>");
    createTemplate("z").setShortcutChar(TemplateSettings.NONE_CHAR);
    type("z");
    assertNull(myFixture.getLookup());
  }

  public void testShowTemplateWithShortcutInAutoPopup() {
    myFixture.configureByText("a.java", "<caret>");
    createTemplate("z").setShortcutChar(TemplateSettings.TAB_CHAR);
    type("z");
    assertNotNull(myFixture.getLookup());
  }

  public void testNoPrematureLookupHidingWhenTemplateNameContainsDash() {
    myFixture.configureByText("a.java", "class C { int aaaA; { <caret> }}");
    createTemplate("aaa-bbb");
    type("aaa");
    assertOrderedEquals(myFixture.getLookupElementStrings(), "aaaA", "aaa-bbb");
    type("-");
    assertOrderedEquals(myFixture.getLookupElementStrings(), "aaa-bbb");

    myFixture.configureByText("a.java", "class C { { <caret> }}");
    type("aaa");
    assertOrderedEquals(myFixture.getLookupElementStrings(), "aaa-bbb");
    type("-");
    assertOrderedEquals(myFixture.getLookupElementStrings(), "aaa-bbb");
  }

  public void testShowTemplatesDifferingOnlyByCase() {
    createTemplate("wC");
    createTemplate("wc");
    myFixture.configureByText("a.java", "class C { { <caret> }}");
    type("w");
    assertTrue(myFixture.getLookupElementStrings().toString(), myFixture.getLookupElementStrings().containsAll(Arrays.asList("wC", "wc")));
  }

  private TemplateImpl createTemplate(String key) {
    TemplateManager manager = TemplateManager.getInstance(getProject());
    TemplateImpl template = (TemplateImpl)manager.createTemplate(key, "user", "");
    TemplateContextType contextType = TemplateContextTypes.getByClass(JavaCodeContextType.Generic.class);
    template.getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());
    return template;
  }
}