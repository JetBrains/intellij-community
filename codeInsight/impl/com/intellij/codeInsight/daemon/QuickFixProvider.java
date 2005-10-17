package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.PsiReference;

/**
 * @author dyoma
 */
public interface QuickFixProvider {
  QuickFixProvider NULL = new QuickFixProvider() {
    public void registerQuickfix(HighlightInfo info, PsiReference reference) {}
  };

  void registerQuickfix(HighlightInfo info, PsiReference reference);
}
