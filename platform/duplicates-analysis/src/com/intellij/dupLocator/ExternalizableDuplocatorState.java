package com.intellij.dupLocator;

import org.jetbrains.annotations.NotNull;

public interface ExternalizableDuplocatorState extends DuplocatorState {
  boolean distinguishRole(@NotNull PsiElementRole role);

  boolean distinguishLiterals();
}
