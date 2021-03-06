// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThrowsUsageTargetProvider implements UsageTargetProvider {
  @Override
  public UsageTarget @Nullable [] getTargets(@NotNull Editor editor, @NotNull final PsiFile file) {
    PsiElement element = file.findElementAt(TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset()));
    if (element == null) return null;

    if (element instanceof PsiKeyword && PsiKeyword.THROWS.equals(element.getText())) {
      return new UsageTarget[]{new PsiElement2UsageTargetAdapter(element)};
    }

    final PsiElement parent = element.getParent();
    if (parent instanceof PsiThrowStatement) {
      return new UsageTarget[] {new PsiElement2UsageTargetAdapter(parent)};
    }

    return null;
  }
}
