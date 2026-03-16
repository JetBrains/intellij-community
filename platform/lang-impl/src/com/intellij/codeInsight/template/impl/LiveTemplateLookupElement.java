// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LiveTemplateLookupElement extends LookupElement {
  private final @NlsSafe String myLookupString;
  private final boolean mySudden;
  private final boolean myWorthShowingInAutoPopup;
  private final @NlsSafe String myDescription;

  /**
   * @param lookupString            the text to show in lookup
   * @param description             the description to show in lookup
   * @param sudden                  see doc for {@link #isSudden()}
   * @param worthShowingInAutoPopup see doc for {@link LookupElement#isWorthShowingInAutoPopup()}
   */
  LiveTemplateLookupElement(@NotNull @NlsSafe String lookupString,
                            @Nullable @NlsSafe String description,
                            boolean sudden,
                            boolean worthShowingInAutoPopup) {
    myDescription = description;
    mySudden = sudden;
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
  public @NotNull AutoCompletionPolicy getAutoCompletionPolicy() {
    return AutoCompletionPolicy.NEVER_AUTOCOMPLETE;
  }

  @Override
  public boolean isWorthShowingInAutoPopup() {
    return myWorthShowingInAutoPopup;
  }

  /**
   * @return true if this template is "sudden" meaning that this template is shown for the fully matching prefix only
   */
  @ApiStatus.Internal
  public boolean isSudden() {
    return mySudden;
  }

  public abstract char getTemplateShortcut();
}
