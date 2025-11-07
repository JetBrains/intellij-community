// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.openapi.util.text.MarkupText;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Visual representation of {@link CompletionItem}.
 * 
 * @param mainText main text describing the completion item (usually the same text that will be inserted, with optional suffix information)
 * @param detailText optional text describing the completion item in more detail (like method type, etc.)
 */
@NotNullByDefault
public record CompletionItemPresentation(
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
  public CompletionItemPresentation(MarkupText mainText) {
    this(mainText, null, MarkupText.empty(), null);
  }

  /**
   * @param mainText new main text
   * @return a new presentation with the given main text and the same detail text
   */
  public CompletionItemPresentation withMainText(MarkupText mainText) {
    return new CompletionItemPresentation(mainText, mainIcon, detailText, detailIcon);
  }
  
  public CompletionItemPresentation withMainIcon(@Nullable Icon icon) {
    return new CompletionItemPresentation(mainText, icon, detailText, detailIcon);
  }

  /**
   * @param detailText new detail text
   * @return a new presentation with the same main text and the given detail text
   */
  public CompletionItemPresentation withDetailText(MarkupText detailText) {
    return new CompletionItemPresentation(mainText, mainIcon, detailText, detailIcon);
  }
  
  public CompletionItemPresentation withDetailIcon(@Nullable Icon icon) {
    return new CompletionItemPresentation(mainText, mainIcon, detailText, icon);
  }
}
