// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

public interface DfBooleanType extends DfPrimitiveType {
  @NotNull
  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiType.BOOLEAN;
  }
}
