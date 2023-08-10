// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.restriction;

import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Constructs restriction info based on psi.
 * 
 * @param <T> type of restriction info to construct
 */
public interface RestrictionInfoFactory<T extends RestrictionInfo> {
  
  @NotNull T fromAnnotationOwner(@Nullable PsiAnnotationOwner annotationOwner);

  @NotNull T fromModifierListOwner(@NotNull PsiModifierListOwner modifierListOwner);
}
