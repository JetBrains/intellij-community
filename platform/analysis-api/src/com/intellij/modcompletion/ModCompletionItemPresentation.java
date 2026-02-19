// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.openapi.util.text.MarkupText;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Visual representation of {@link ModCompletionItem}.
 * Icons are lazy, so if a client doesn't use them, the supplier will not be called.
 */
@NotNullByDefault
public final class ModCompletionItemPresentation {
  private static final Supplier<@Nullable Icon> NO_ICON = () -> null;
  private final MarkupText mainText;
  private final Supplier<@Nullable Icon> mainIcon;
  private final MarkupText detailText;
  private final Supplier<@Nullable Icon> detailIcon;

  /**
   * @param mainText   main text describing the completion item (usually the same text that will be inserted, with optional suffix information)
   * @param mainIcon   supplier of icon for main text
   * @param detailText optional text describing the completion item in more detail (like method type, etc.)
   * @param detailIcon supplier of icon for detail text
   */
  private ModCompletionItemPresentation(
    MarkupText mainText,
    Supplier<@Nullable Icon> mainIcon,
    MarkupText detailText,
    Supplier<@Nullable Icon> detailIcon
  ) {
    this.mainText = mainText;
    this.mainIcon = mainIcon;
    this.detailText = detailText;
    this.detailIcon = detailIcon;
  }

  /**
   * Creates a presentation with the given main text and an empty detail text.
   *
   * @param mainText main text to use
   */
  public ModCompletionItemPresentation(MarkupText mainText) {
    this(mainText, NO_ICON, MarkupText.empty(), NO_ICON);
  }

  /**
   * @param mainText new main text
   * @return a new presentation with the given main text and the same detail text
   */
  public ModCompletionItemPresentation withMainText(MarkupText mainText) {
    return new ModCompletionItemPresentation(mainText, mainIcon, detailText, detailIcon);
  }

  /**
   * @param icon supplier of icon for main text
   * @return a new presentation with the given main icon. The rest is the same.
   * Icons are lazy, so if a client doesn't use them, the supplier will not be called.
   */
  public ModCompletionItemPresentation withMainIcon(Supplier<@Nullable Icon> icon) {
    return new ModCompletionItemPresentation(mainText, icon, detailText, detailIcon);
  }

  /**
   * @param detailText new detail text
   * @return a new presentation with the same main text and the given detail text
   */
  public ModCompletionItemPresentation withDetailText(MarkupText detailText) {
    return new ModCompletionItemPresentation(mainText, mainIcon, detailText, detailIcon);
  }

  /**
   * @param icon supplier of icon for detail text
   * @return a new presentation with the given detail icon. The rest is the same.
   * Icons are lazy, so if a client doesn't use them, the supplier will not be called.
   */
  public ModCompletionItemPresentation withDetailIcon(Supplier<@Nullable Icon> icon) {
    return new ModCompletionItemPresentation(mainText, mainIcon, detailText, icon);
  }

  public MarkupText mainText() { return mainText; }
  
  public @Nullable Icon mainIcon() { return mainIcon.get(); }

  public MarkupText detailText() { return detailText; }

  public @Nullable Icon detailIcon() { return detailIcon.get(); }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ModCompletionItemPresentation)obj;
    return Objects.equals(this.mainText, that.mainText) &&
           Objects.equals(this.mainIcon, that.mainIcon) &&
           Objects.equals(this.detailText, that.detailText) &&
           Objects.equals(this.detailIcon, that.detailIcon);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mainText, mainIcon, detailText, detailIcon);
  }

  @Override
  public String toString() {
    return "ModCompletionItemPresentation[" +
           "mainText=" + mainText + ", " +
           "mainIcon=" + mainIcon + ", " +
           "detailText=" + detailText + ", " +
           "detailIcon=" + detailIcon + ']';
  }
}
