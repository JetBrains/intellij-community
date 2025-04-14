// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaUsageTargetProvider implements UsageTargetProvider, DumbAware {
  @Override
  public UsageTarget @Nullable [] getTargets(@NotNull Editor editor, final @NotNull PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(TargetElementUtil.adjustOffset(file, editor.getDocument(), offset));
    if (element == null) return null;

    if (element instanceof PsiKeyword && JavaKeywords.THROWS.equals(element.getText())) {
      return new UsageTarget[]{new PsiElement2UsageTargetAdapter(element)};
    }

    final PsiElement parent = element.getParent();
    if (parent instanceof PsiThrowStatement) {
      return new UsageTarget[] {new PsiElement2UsageTargetAdapter(parent)};
    }

    PsiMethod constructor = getRecordCanonicalConstructor(element, offset);
    if (constructor instanceof SyntheticElement) {
      return new UsageTarget[] {new PsiElement2UsageTargetAdapter(constructor)};
    }

    return null;
  }

  private static @Nullable PsiMethod getRecordCanonicalConstructor(PsiElement element, int offset) {
    PsiElement parent = element.getParent();
    if ((PsiUtil.isJavaToken(element, JavaTokenType.RPARENTH) || PsiUtil.isJavaToken(element, JavaTokenType.LPARENTH)) &&
        parent instanceof PsiRecordHeader) {
      PsiClass recordClass = ObjectUtils.tryCast(parent.getParent(), PsiClass.class);
      if (recordClass != null) {
        return JavaPsiRecordUtil.findCanonicalConstructor(recordClass);
      }
    }

    if (element instanceof PsiIdentifier &&
        parent instanceof PsiClass &&
        ((PsiClass)parent).isRecord() &&
        offset == element.getTextRange().getEndOffset()) {
      return JavaPsiRecordUtil.findCanonicalConstructor((PsiClass)parent);
    }
    return null;
  }
}
