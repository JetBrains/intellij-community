/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.template;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class ListTemplateActionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
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
    TemplateContextType contextType = ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), JavaCodeContextType.class);
    template.getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, getTestRootDisposable());
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
                                                                                                                          