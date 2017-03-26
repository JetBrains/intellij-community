/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.json;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.json.liveTemplates.JsonContextType;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonLiveTemplateTest extends JsonTestCase {

  private boolean isApplicableContextUnderCaret(@NotNull String text) {
    myFixture.configureByText(JsonFileType.INSTANCE, text);
    final Template template = createJsonTemplate("foo", "foo", "[42]");
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), myFixture.getCaretOffset(), (TemplateImpl)template);
  }

  @NotNull
  private Template createJsonTemplate(@NotNull String name, @NotNull String group, @NotNull String text) {
    final TemplateManager templateManager = TemplateManager.getInstance(getProject());
    final Template template = templateManager.createTemplate(name, group, text);

    final TemplateContextType context = ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), JsonContextType.class);
    assertNotNull(context);
    ((TemplateImpl)template).getTemplateContext().setEnabled(context, true);

    CodeInsightTestUtil.addTemplate(template, getTestRootDisposable());
    return template;
  }

  public void testNotExpandableInsideStringLiteral() {
    assertFalse(isApplicableContextUnderCaret("{\"bar\": \"fo<caret>o\"}"));
  }

  public void testNotExpandableInsidePropertyKey() {
    assertFalse(isApplicableContextUnderCaret("{fo<caret>o: \"bar\"}"));
  }

  public void testNotExpandableInsidePropertyKeyWithWhitespace() {
    assertFalse(isApplicableContextUnderCaret("{fo<caret>o : \"bar\"}"));
  }

  public void testExpandableAtTopLevel() {
    assertTrue(isApplicableContextUnderCaret("fo<caret>o"));
  }

  public void testExpandableInObjectLiteral() {
    assertTrue(isApplicableContextUnderCaret("{fo<caret>o}"));
  }

  public void testCustomTemplateExpansion() {
    final String templateContent = "{\n" +
                                   "  \"foo\": \"$1$\"\n" +
                                   "}";
    createJsonTemplate("foo", "foo", templateContent);
    myFixture.configureByText(JsonFileType.INSTANCE, "foo<caret>");
    final Editor editor = myFixture.getEditor();
    new ListTemplatesAction().actionPerformedImpl(getProject(), editor);
    final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    assertNotNull(lookup);
    lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    myFixture.checkResult(templateContent.replaceAll("\\$.*?\\$", ""));
  }
}
