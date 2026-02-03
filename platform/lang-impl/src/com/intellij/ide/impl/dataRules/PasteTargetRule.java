// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.actionSystem.CommonDataKeys.PSI_ELEMENT;

final class PasteTargetRule {
  static @Nullable PsiElement getData(@NotNull DataMap dataProvider) {
    return dataProvider.get(PSI_ELEMENT);
  }
}
