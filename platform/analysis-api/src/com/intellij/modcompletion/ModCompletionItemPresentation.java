// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.openapi.util.text.MarkupText;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Visual representation of {@link ModCompletionItem}.
 * 
 * @param mainText main text describing the completion item (usually the same text that will be inserted, with optional suffix information)
 * @param detailText optional text describing the completion item in more detail (like method type, etc.)
 */
@NotNullByDefault
public record ModCompletionItemPresentation(
  MarkupText mainText,
  @Nullable Icon mainIcon,
  MarkupText detailText,
  @Nullable Icon detailIcon
) {
  /**
   * Creates a presentation with the given main text and an empty detail text.
   * 
   * @param mainText main text to use
   */
  public ModCompletionItemPresentation(MarkupText mainText) {
    this(mainText, null, MarkupText.empty(), null);
  }

  /**
   * @param mainText new main text
   * @return a new presentation with the given main text and the same detail text
   */
  public ModCompletionItemPresentation withMainText(MarkupText mainText) {
    return new ModCompletionItemPresentation(mainText, mainIcon, detailText, detailIcon);
  }
  
  public ModCompletionItemPresentation withMainIcon(@Nullable Icon icon) {
    return new ModCompletionItemPresentation(mainText, icon, detailText, detailIcon);
  }

  /**
   * @param detailText new detail text
   * @return a new presentation with the same main text and the given detail text
   */
  public ModCompletionItemPresentation withDetailText(MarkupText detailText) {
    return new ModCompletionItemPresentation(mainText, mainIcon, detailText, detailIcon);
  }
  
  public ModCompletionItemPresentation withDetailIcon(@Nullable Icon icon) {
    return new ModCompletionItemPresentation(mainText, mainIcon, detailText, icon);
  }
}
