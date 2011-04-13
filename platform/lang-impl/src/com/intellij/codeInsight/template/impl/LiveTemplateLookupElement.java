/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.TemplateManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class LiveTemplateLookupElement extends LookupElement {
  private final String myPrefix;
  private final TemplateImpl myTemplate;

  public LiveTemplateLookupElement(TemplateImpl template) {
    myPrefix = template.getKey();
    myTemplate = template;
  }
  @NotNull
  @Override
  public String getLookupString() {
    return myPrefix;
  }

  public TemplateImpl getTemplate() {
    return myTemplate;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    super.renderElement(presentation);
    presentation.setTypeText(myTemplate.getDescription());
  }

  @Override
  public void handleInsert(InsertionContext context) {
    context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
    context.setAddCompletionChar(false);
    TemplateManager.getInstance(context.getProject()).startTemplate(context.getEditor(), myTemplate);
  }
}
