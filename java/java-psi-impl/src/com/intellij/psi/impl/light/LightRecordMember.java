// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.SyntheticElement;
import org.jetbrains.annotations.NotNull;

public interface LightRecordMember extends PsiElement, SyntheticElement {
  @NotNull
  PsiRecordComponent getRecordComponent();
}
