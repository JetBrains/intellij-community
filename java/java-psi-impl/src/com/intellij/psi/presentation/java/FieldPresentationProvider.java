// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.presentation.java;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProvider;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;


public final class FieldPresentationProvider implements ItemPresentationProvider<PsiField> {
  @Override
  public ItemPresentation getPresentation(@NotNull PsiField item) {
    return JavaPresentationUtil.getFieldPresentation(item);
  }
}
