// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LiveTemplateLookupElement extends LookupElement {
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

  @Override
  public @NotNull String getLookupString() {
    return myLookupString;
  }

  protected @NotNull @NlsSafe String getItemText() {
    return myLookupString;
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
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
