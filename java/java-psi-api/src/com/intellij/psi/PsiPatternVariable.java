// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * A variable declared within the pattern.
 * <p>
 *   There may be right now 2 different types of variables:
 *   <ul>
 *     <li>record variables {@code case Rec(int x) rec } - record variable here is {@code rec}</li>
 *     <li>simple pattern variables, for example: {@code instanceof String s } - variable is {@code s}.</li>
 *   </ul>
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
