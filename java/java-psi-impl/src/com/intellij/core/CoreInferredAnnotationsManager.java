// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The inferred annotations manager of a project without annotation inference. It finds no annotation.
 *
 * @see JavaCoreProjectEnvironment#createInferredAnnotationsManager
 */
@ApiStatus.Internal
public class CoreInferredAnnotationsManager extends InferredAnnotationsManager {
  @Override
  public @Nullable PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    return null;
  }

  @Override
  public PsiAnnotation @NotNull [] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner) {
    return PsiAnnotation.EMPTY_ARRAY;
  }
}
