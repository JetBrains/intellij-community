// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTargetUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UsageTargetsRule implements GetDataRule {
  @Override
  public @Nullable Object getData(@NotNull DataProvider dataProvider) {
    Editor editor = CommonDataKeys.EDITOR.getData(dataProvider);
    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataProvider);
    PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataProvider);
    return UsageTargetUtil.findUsageTargets(editor, file, psiElement);
  }
}
