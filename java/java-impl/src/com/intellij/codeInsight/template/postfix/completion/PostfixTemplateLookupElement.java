/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.completion.PostfixTemplateCompletionContributor.getPostfixLiveTemplate;

class PostfixTemplateLookupElement extends LiveTemplateLookupElement {
  @NotNull 
  private final PostfixTemplate myTemplate;

  public PostfixTemplateLookupElement(@NotNull PostfixTemplate template, char shortcut) {
    super(createStubTemplate(template, shortcut), template.getPresentableName(), true, true);
    myTemplate = template;
  }

  @NotNull
  public PostfixTemplate getPostfixTemplate() {
    return myTemplate;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    super.renderElement(presentation);
    presentation.setTailText(" " + arrow() + " " + myTemplate.getExample());
  }

  @Override
  public void handleInsert(InsertionContext context) {
    context.setAddCompletionChar(false);
    int lengthOfTypedKey = context.getTailOffset() - context.getStartOffset();
    String templateKey = myTemplate.getKey();
    Editor editor = context.getEditor();
    if (lengthOfTypedKey < templateKey.length()) {
      context.getDocument().insertString(context.getTailOffset(), templateKey.substring(lengthOfTypedKey));
      editor.getCaretModel().moveToOffset(context.getTailOffset() + templateKey.length() - lengthOfTypedKey);
    }

    PsiFile file = context.getFile();

    PostfixLiveTemplate postfixLiveTemplate = getPostfixLiveTemplate(context.getFile(), context.getEditor());
    if (postfixLiveTemplate != null) {
      postfixLiveTemplate.expand(templateKey, new CustomTemplateCallback(editor, file, false));
    }
  }

  private static TemplateImpl createStubTemplate(@NotNull PostfixTemplate postfixTemplate, char shortcut) {
    TemplateImpl template = new TemplateImpl(postfixTemplate.getKey(), "postfixTemplate");
    template.setShortcutChar(shortcut);
    return template;
  }

  private static String arrow() {
    return SystemInfo.isMac ? "→" :"->";
  }
}
