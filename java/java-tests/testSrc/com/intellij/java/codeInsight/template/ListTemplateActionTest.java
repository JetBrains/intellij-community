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
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ListTemplateActionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    addTemplate("simple", "simple template text", "description", "test");
    addTemplate("complex key", "complex template text", "", "test");
    addTemplate("template.with.desc", "template with description", "desc", "test");
    
    addTemplate("duplicateKey", "template with description", "desc", "test1");
    addTemplate("duplicateKey", "template with description", "desc", "test2");
  }

  private void addTemplate(String key, String text, String description, String group) {
    TemplateManager manager = TemplateManager.getInstance(getProject());
    TemplateImpl template = (TemplateImpl)manager.createTemplate(key, group, text);
    template.setDescription(description);
    TemplateContextType contextType = TemplateContextTypes.getByClass(JavaCodeContextType.Generic.class);
    template.getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/list/";
  }

  public void testWithSameKeys() {
    myFixture.configureByFile(getTestName(false) + ".java");
    new ListTemplatesAction().actionPerformedImpl(myFixture.getProject(), myFixture.getEditor());
    assertSameElements(myFixture.getLookupElementStrings(), "duplicateKey", "duplicateKey");
  }
  
  public void testWithoutPrefix() {
    doTest("simple");
  }

  public void testPartialPrefix() {
    doTest("simple");
  }

  public void testFullMatchPrefix() {
    doTest("simple");
  }

  public void testNotMatchedPrefix() {
    doTest("simple");
  }

  public void testNotMatchedPrefixAfterNonJavaCharacter() {
    doTest("simple");
  }

  public void testMatchingByTemplateDescription() {
    doTest("simple");
  }

  public void testComplexKeyWithoutPrefix() {
    doTest("complex key");
  }

  public void testComplexKeyWithPartialPrefix() {
    doTest("complex key");
  }

  public void testComplexKeyWithFullMatchPrefix() {
    doTest("complex key");
  }

  public void testComplexKeyWithNotMatchedPrefix() {
    doTest("complex key");
  }

  public void testComplexKeyWithNotMatchedPrefixAfterNonJavaCharacter() {
    doTest("complex key");
  }
  
  public void testTemplateShouldNotBeReplacedByOtherTemplateMatchedByDescription() {
    doTest("template.with.desc");
  }

  public void testMulticaret() {
    doTest("simple");
  }

  public void testMulticaretWithPrefix() {
    doTest("simple");
  }

  private void doTest(@NotNull String lookupText) {
    myFixture.configureByFile(getTestName(false) + ".java");
    new ListTemplatesAction().actionPerformedImpl(myFixture.getProject(), myFixture.getEditor());

    LookupElement[] elements = myFixture.getLookupElements();
    assertNotNull(elements);
    for (LookupElement element : elements) {
      if (lookupText.equals(element.getLookupString())) {
        myFixture.getLookup().setCurrentItem(element);
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        myFixture.checkResultByFile(getTestName(false) + "_after.java");
        return;
      }
    }
    //noinspection ConstantConditions
    fail("Lookup element with text '" + lookupText + "' not found:\n" + StringUtil.join(myFixture.getLookupElementStrings(), "\n"));
  }
}
                                                                                                                          