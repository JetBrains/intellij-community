// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Element with list-like structure.
 * <p>
 * Implement this interface to enable Move Element Left/Right action.
 */
public interface PsiListLikeElement extends PsiElement {

  /**
   * @return list of immediate children of this element, which could be swapped between each other,
   * for example parameters {@code a} and {@code b} in parameter list of the method call {@code foo(a, b)}
   */
  @NotNull
  List<? extends PsiElement> getComponents();
}
