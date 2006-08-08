package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.PsiReference;

/**
 * @author dyoma
 */
public interface QuickFixProvider<T extends PsiReference> {
  QuickFixProvider NULL = new QuickFixProvider() {
    public void registerQuickfix(HighlightInfo info, PsiReference reference) {}
  };

  void registerQuickfix(HighlightInfo info, T reference);
}
