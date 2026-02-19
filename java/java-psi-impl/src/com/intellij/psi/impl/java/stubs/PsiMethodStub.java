// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.cache.TypeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiMethodStub extends PsiMemberStub<PsiMethod> {
  boolean isConstructor();
  boolean isVarArgs();
  boolean isAnnotationMethod();

  @Nullable String getDefaultValueText();
  @NotNull TypeInfo getReturnTypeText();

  PsiParameterStub findParameter(int idx);
}
