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
 * Intention action UI representation of which can be customized
 *
 * Mostly, it can be used to:
 * * forcefully disable submenu,
 * * make intention action non-selectable
 * * fully remove icon
 * * create custom tooltip text
 */
public interface CustomizableIntentionAction extends IntentionAction {
  /**
   * Define if submenu (or so-called options)
   * of intention action should be shown
   */
  boolean isShowSubmenu();

  /**
   * Define if element in popup should be
   * selectable
   */
  boolean isSelectable();

  /**
   * Define if icon should be shown or
   * completely removed
   */
  boolean isShowIcon();

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
