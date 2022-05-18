// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Represents elements between '(' and ')' (inclusive) in {@link PsiRecordPattern}.
 */
public interface PsiRecordStructurePattern extends PsiElement {
  @NotNull
  PsiPattern @NotNull [] getRecordComponents();
}
