package com.intellij.psi;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Pair;

/**
 * @author max
 */
public interface PsiLanguageInjectionHost extends PsiElement {
  /**
   * @return injected PSI element and text range inside host element where injection occurs.
   * For example, in string literals we might want to inject something inside double quotes.
   * To express this, use <code>return Pair.create(injectedPsi, new TextRange(1, literalLength-2))</code>.
   */
  @Nullable
  Pair<PsiElement, TextRange> getInjectedPsi();
}
