// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.presentation.java;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProvider;
import com.intellij.psi.PsiRecordComponent;
import org.jetbrains.annotations.NotNull;


public final class RecordComponentPresentationProvider implements ItemPresentationProvider<PsiRecordComponent> {
  @Override
  public ItemPresentation getPresentation(@NotNull PsiRecordComponent item) {
    return JavaPresentationUtil.getRecordComponentPresentation(item);
  }
}
