// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.actionSystem.CommonDataKeys.*;

final class UsageTargetsRule {
  static UsageTarget @Nullable [] getData(@NotNull DataMap dataProvider) {
    Editor editor = dataProvider.get(EDITOR);
    PsiFile file = dataProvider.get(PSI_FILE);
    PsiElement psiElement = dataProvider.get(PSI_ELEMENT);
    return UsageTargetUtil.findUsageTargets(editor, file, psiElement);
  }
}
