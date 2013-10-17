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
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.RealLookupElementPresentation;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;

/**
 * @author peter
 */
public class LiveTemplateLookupElement extends LookupElement {
  private final String myPrefix;
  @NotNull private final TemplateImpl myTemplate;
  private final String myLookupString;
  public final boolean sudden;
  private final boolean myWorthShowingInAutoPopup;

  public LiveTemplateLookupElement(@NotNull TemplateImpl template, boolean sudden) {
    this(template, null, sudden, false);
  }
  
  public LiveTemplateLookupElement(@NotNull TemplateImpl template, @Nullable String lookupString, boolean sudden, boolean worthShowingInAutoPopup) {
    this.sudden = sudden;
    myLookupString = lookupString;
    myPrefix = template.getKey();
    myTemplate = template;
    myWorthShowingInAutoPopup = worthShowingInAutoPopup;
  }
  @NotNull
  @Override
  public String getLookupString() {
    return myPrefix;
  }

  @NotNull
  public TemplateImpl getTemplate() {
    return myTemplate;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    super.renderElement(presentation);
    presentation.setItemText(StringUtil.notNullize(myLookupString, myPrefix));
    if (sudden) {
      presentation.setItemTextBold(true);
      if (!presentation.isReal() || !((RealLookupElementPresentation)presentation).isLookupSelectionTouched()) {
        char shortcutChar = myTemplate.getShortcutChar();
        if (shortcutChar == TemplateSettings.DEFAULT_CHAR) {
          shortcutChar = TemplateSettings.getInstance().getDefaultShortcutChar();
        }
        presentation.setTypeText("  [" + KeyEvent.getKeyText(shortcutChar) + "] ");
      }
      String description = myTemplate.getDescription();
      if (description != null) {
        presentation.setTailText(" (" + description + ")", true);
      }
    } else {
      presentation.setTypeText(myTemplate.getDescription());
    }
  }

  @Override
  public void handleInsert(InsertionContext context) {
    context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
    context.setAddCompletionChar(false);
    TemplateManager.getInstance(context.getProject()).startTemplate(context.getEditor(), myTemplate);
  }

  @Override
  public boolean isWorthShowingInAutoPopup() {
    return myWorthShowingInAutoPopup;
  }
}
