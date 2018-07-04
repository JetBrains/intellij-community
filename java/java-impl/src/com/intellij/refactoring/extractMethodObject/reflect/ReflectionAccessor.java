// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface ReflectionAccessor {

  /**
   * Grants access to use of all inaccessible members inside the {@code element} through reflection
   */
  void accessThroughReflection(@NotNull PsiElement element);
}
