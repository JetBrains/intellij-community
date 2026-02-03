// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Intention action with UI representation that can be customized. It can be used to:
 * <li> forcefully disable submenu
 * <li> make the intention action non-selectable
 * <li> fully remove icon
 * <li> create custom tooltip text
 * <li> highlight parts of the editor when this action is hovered in the context action popup
 */
public interface CustomizableIntentionAction extends IntentionAction {
  /**
   * Define if submenu (or so-called options)
   * of intention action should be shown
   */
  default boolean isShowSubmenu() {
    return true;
  }

  /**
   * Define if element in popup should be
   * selectable
   */
  default boolean isSelectable() {
    return true;
  }

  /**
   * Define if icon should be shown or
   * completely removed
   */
  default boolean isShowIcon() {
    return true;
  }

  /**
   * @return true if light bulb must be shown when such action is available
   */
  default boolean isShowLightBulb() {
    return true;
  }

  /**
   * Define if icon of this action must override the light bulb.
   * It may be necessary for promoting promising language-specific actions
   * that get blend in with other intentions.
   * If there are many custom icons, the default yellow bulb will be shown.
   *
   * @see com.intellij.codeInsight.intention.impl.IntentionHintComponent.LightBulbUtil#findSingleCustomBulbIcon
   */
  default boolean isOverrideIntentionBulb() {
    return false;
  }

  /**
   * Get text specifically for tooltip view
   */
  default @NlsContexts.Tooltip String getTooltipText() {
    return getText();
  }

  /**
   * Return the set of ranges to highlight in the editor when the selection in the Context Actions popup moves to this action.
   * @param editor the host editor where the popup is displayed
   * @param file the host PSI file where the popup is displayed; the PSI may be uncommitted
   */
  default @Unmodifiable @NotNull List<RangeToHighlight> getRangesToHighlight(@NotNull Editor editor, @NotNull PsiFile file) {
    return List.of();
  }

  /**
   * Determines if there is a separator above the customizable intention action.
   * @return {@code true} if there is a separator above the action, {@code false} otherwise.
   */
  default boolean hasSeparatorAbove() {
    return false;
  }

  /** The highlighting request to be returned from {@link #getRangesToHighlight} */
  final class RangeToHighlight {
    private final PsiElement psi;
    private final TextRange rangeInPsi;
    private final TextAttributesKey highlightKey;

    /**
     * @param psi psi element to highlight
     * @param rangeInPsi range within psi element to highlight
     * @param highlightKey highlighting key to use
     */
    public RangeToHighlight(@NotNull PsiElement psi, @NotNull TextRange rangeInPsi, @NotNull TextAttributesKey highlightKey) {
      this.psi = psi;
      this.rangeInPsi = rangeInPsi;
      this.highlightKey = highlightKey;
    }

    public TextRange getRangeInFile() {
      return rangeInPsi.shiftRight(psi.getTextRange().getStartOffset());
    }

    public PsiFile getContainingFile() {
      return psi.getContainingFile();
    }

    @NotNull
    public TextAttributesKey getHighlightKey() {
      return highlightKey;
    }

    @Contract("null, _ -> null; !null, _ -> !null")
    public static RangeToHighlight from(@Nullable PsiElement psi, @NotNull TextAttributesKey highlightKey) {
      return psi == null ? null : new RangeToHighlight(psi, TextRange.create(0, psi.getTextLength()), highlightKey);
    }
  }
}
