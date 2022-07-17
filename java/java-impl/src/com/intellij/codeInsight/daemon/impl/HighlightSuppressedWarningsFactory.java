// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


class HighlightSuppressedWarningsFactory extends HighlightUsagesHandlerFactoryBase {

  @Nullable
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor,
                                                                 @NotNull PsiFile file,
                                                                 @NotNull PsiElement target) {
    throw new UnsupportedOperationException("Use createHighlightUsagesHandler(editor, file, target, visibleRange)");
  }

  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target,
                                                                 @NotNull ProperTextRange visibleRange) {
    final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(target, PsiAnnotation.class);
    if (annotation != null && Comparing.strEqual(SuppressWarnings.class.getName(), annotation.getQualifiedName())) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null && !virtualFile.getFileType().isBinary()) {
        return new HighlightSuppressedWarningsHandler(editor, file, annotation,
                                                      PsiTreeUtil.getParentOfType(target, PsiLiteralExpression.class), visibleRange);
      }
    }
    return null;
  }
}