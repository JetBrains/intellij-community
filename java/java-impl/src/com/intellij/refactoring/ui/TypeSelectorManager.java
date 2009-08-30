package com.intellij.refactoring.ui;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public interface TypeSelectorManager {
  TypeSelector getTypeSelector();

  void setAllOccurences(boolean allOccurences);

  boolean isSuggestedType(final String fqName);

  void typeSelected(@NotNull PsiType type);
}
