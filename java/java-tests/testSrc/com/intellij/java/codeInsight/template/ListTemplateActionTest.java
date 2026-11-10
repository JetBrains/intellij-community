// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ListTemplateActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/template/list/");
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    addTemplate("simple", "simple template text", "description", "test");
    addTemplate("complex key", "complex template text", "", "test");
    addTemplate("template.with.desc", "template with description", "desc", "test");
    
    addTemplate("duplicateKey", "template with description", "desc", "test1");
    addTemplate("duplicateKey", "template with description", "desc", "test2");
  }

  private void addTemplate(String key, String text, String description, String group) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    TemplateImpl template = (TemplateImpl)manager.createTemplate(key, group, text);
    template.setDescription(description);
    TemplateContextType contextType = TemplateContextTypes.getByClass(JavaCodeContextType.Generic.class);
    template.getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());
  }

  @Test
  public void testWithSameKeys() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      new ListTemplatesAction().actionPerformedImpl(myFixture.getProject(), myFixture.getEditor());
      assertThat(myFixture.getLookupElementStrings()).containsOnly("duplicateKey", "duplicateKey");
    });
  }
  
  @Test
  public void testWithoutPrefix() {
    doTest("simple");
  }

  @Test
  public void testPartialPrefix() {
    doTest("simple");
  }

  @Test
  public void testFullMatchPrefix() {
    doTest("simple");
  }

  @Test
  public void testNotMatchedPrefix() {
    doTest("simple");
  }

  @Test
  public void testNotMatchedPrefixAfterNonJavaCharacter() {
    doTest("simple");
  }

  @Test
  public void testMatchingByTemplateDescription() {
    doTest("simple");
  }

  @Test
  public void testComplexKeyWithoutPrefix() {
    doTest("complex key");
  }

  @Test
  public void testComplexKeyWithPartialPrefix() {
    doTest("complex key");
  }

  @Test
  public void testComplexKeyWithFullMatchPrefix() {
    doTest("complex key");
  }

  @Test
  public void testComplexKeyWithNotMatchedPrefix() {
    doTest("complex key");
  }

  @Test
  public void testComplexKeyWithNotMatchedPrefixAfterNonJavaCharacter() {
    doTest("complex key");
  }
  
  @Test
  public void testTemplateShouldNotBeReplacedByOtherTemplateMatchedByDescription() {
    doTest("template.with.desc");
  }

  @Test
  public void testMulticaret() {
    doTest("simple");
  }

  @Test
  public void testMulticaretWithPrefix() {
    doTest("simple");
  }

  private void doTest(@NotNull String lookupText) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      new ListTemplatesAction().actionPerformedImpl(myFixture.getProject(), myFixture.getEditor());

      LookupElement[] elements = myFixture.getLookupElements();
      assertNotNull(elements);
      for (LookupElement element : elements) {
        if (lookupText.equals(element.getLookupString())) {
          myFixture.getLookup().setCurrentItem(element);
          myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
          myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
          return;
        }
      }
      //noinspection ConstantConditions
      fail("Lookup element with text '" + lookupText + "' not found:\n" + StringUtil.join(myFixture.getLookupElementStrings(), "\n"));
    });
  }
}
                                                                                                                          