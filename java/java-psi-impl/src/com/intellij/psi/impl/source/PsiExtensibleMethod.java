// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PsiExtensibleMethod extends PsiMethod {
  @NotNull
  PsiParameterList getOwnParametersList();

  @NotNull
  PsiTypeParameterList getOwnTypeParametersList();

  @NotNull
  PsiReferenceList getOwnThrowsList();

  @Nullable
  PsiCodeBlock getOwnBody();
}