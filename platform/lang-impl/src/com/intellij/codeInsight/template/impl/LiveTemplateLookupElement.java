/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
abstract public class LiveTemplateLookupElement extends LookupElement {
  private final @NlsSafe String myLookupString;
  public final boolean sudden;
  private final boolean myWorthShowingInAutoPopup;
  private final @NlsSafe String myDescription;

  LiveTemplateLookupElement(@NotNull @NlsSafe String lookupString, @Nullable @NlsSafe String description, boolean sudden, boolean worthShowingInAutoPopup) {
    myDescription = description;
    this.sudden = sudden;
    myLookupString = lookupString;
    myWorthShowingInAutoPopup = worthShowingInAutoPopup;
  }

  @NotNull
  @Override
  public String getLookupString() {
    return myLookupString;
  }

  @NotNull
  protected @NlsSafe String getItemText() {
    return myLookupString;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setItemText(getItemText());
    presentation.setTypeText(myDescription);
    presentation.setIcon(AllIcons.Nodes.Template);
  }

  @Override
  public AutoCompletionPolicy getAutoCompletionPolicy() {
    return AutoCompletionPolicy.NEVER_AUTOCOMPLETE;
  }

  @Override
  public boolean isWorthShowingInAutoPopup() {
    return myWorthShowingInAutoPopup;
  }

  public abstract char getTemplateShortcut();
}
