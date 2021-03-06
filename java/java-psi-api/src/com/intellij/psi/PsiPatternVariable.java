// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * A variable declared within the pattern
 */
public interface PsiPatternVariable extends PsiParameter, PsiModifierListOwner {
  @NotNull
  @Override
  String getName();

  @NotNull
  @Override
  PsiTypeElement getTypeElement();

  @NotNull
  @Override
  PsiIdentifier getNameIdentifier();

  @NotNull
  PsiPattern getPattern();
}
