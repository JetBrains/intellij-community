// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

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
   * Get text specifically for tooltip view
   */
  @NlsContexts.Tooltip
  default String getTooltipText() {
    return getText();
  }

  /**
   * Return the set of ranges to highlight in the editor when the selection in the Context Actions popup moves to this action.
   * @param editor the host editor where the popup is displayed
   * @param file the host PSI file where the popup is displayed; the PSI may be uncommitted
   */
  default @NotNull List<RangeToHighlight> getRangesToHighlight(@NotNull Editor editor, @NotNull PsiFile file) {
    return List.of();
  }

  /** The highlighting request to be returned from {@link #getRangesToHighlight} */
  class RangeToHighlight {
    private final PsiElement psi;
    private final TextRange rangeInPsi;
    private final TextAttributesKey highlightKey;

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

    public TextAttributesKey getHighlightKey() {
      return highlightKey;
    }
  }
}
