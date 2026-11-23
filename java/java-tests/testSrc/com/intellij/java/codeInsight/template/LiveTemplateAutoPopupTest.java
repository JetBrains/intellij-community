// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template;

import com.intellij.codeInsight.completion.JavaCompletionAutoPopupFixture;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.intellij.codeInsight.completion.JavaCompletionAutoPopupFixtureKt.javaCompletionAutoPopupFixture;
import static com.intellij.testFramework.UsefulTestCase.assertOrderedEquals;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class LiveTemplateAutoPopupTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<JavaCompletionAutoPopupFixture> autoPopupFixture = javaCompletionAutoPopupFixture(projectFixture);
  private LightJavaCodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = autoPopupFixture.get().getFixture();
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
  }

  private void type(String s) {
    autoPopupFixture.get().type(s);
  }

  @Test
  public void testDoNotShowTemplateWithoutShortcutInAutoPopup() {
    myFixture.configureByText("a.java", "<caret>");
    createTemplate("z").setShortcutChar(TemplateSettings.NONE_CHAR);
    type("z");
    assertNull(myFixture.getLookup());
  }

  @Test
  public void testShowTemplateWithShortcutInAutoPopup() {
    myFixture.configureByText("a.java", "<caret>");
    createTemplate("z").setShortcutChar(TemplateSettings.TAB_CHAR);
    type("z");
    assertNotNull(myFixture.getLookup());
  }

  @Test
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

  @Test
  public void testShowTemplatesDifferingOnlyByCase() {
    createTemplate("wC");
    createTemplate("wc");
    myFixture.configureByText("a.java", "class C { { <caret> }}");
    type("w");
    assertTrue(myFixture.getLookupElementStrings().containsAll(Arrays.asList("wC", "wc")), myFixture.getLookupElementStrings().toString());
  }

  private TemplateImpl createTemplate(String key) {
    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    TemplateImpl template = (TemplateImpl)manager.createTemplate(key, "user", "");
    TemplateContextType contextType = TemplateContextTypes.getByClass(JavaCodeContextType.Generic.class);
    template.getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());
    return template;
  }
}