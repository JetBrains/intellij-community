// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NotNull;

public interface DfBooleanType extends DfPrimitiveType {
  @Override
  default @NotNull PsiPrimitiveType getPsiType() {
    return PsiTypes.booleanType();
  }
}
