package com.intellij.dupLocator;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public interface ExternalizableDuplocatorState extends DuplocatorState {
  boolean distinguishRole(@NotNull PsiElementRole role);

  boolean distinguishLiterals();
}
