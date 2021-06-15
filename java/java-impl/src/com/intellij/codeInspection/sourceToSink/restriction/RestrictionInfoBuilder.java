// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink.restriction;

import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RestrictionInfoBuilder<T extends RestrictionInfo> {
  
  @NotNull T fromAnnotationOwner(@Nullable PsiAnnotationOwner annotationOwner);

  @NotNull T fromModifierListOwner(@NotNull PsiModifierListOwner modifierListOwner);
}
