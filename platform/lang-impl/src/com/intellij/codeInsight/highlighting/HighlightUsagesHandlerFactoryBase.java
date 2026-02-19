// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class HighlightUsagesHandlerFactoryBase implements HighlightUsagesHandlerFactory {
  @Override
  public final @Nullable HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiElement target = findTarget(editor, psiFile);
    if (target == null) return null;
    return createHighlightUsagesHandler(editor, psiFile, target);
  }

  @Override
  public final @Nullable HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile psiFile,
                                                                                 @NotNull ProperTextRange visibleRange) {
    PsiElement target = findTarget(editor, psiFile);
    if (target == null) return null;
    return createHighlightUsagesHandler(editor, psiFile, target, visibleRange);
  }

  public abstract @Nullable HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile psiFile, @NotNull PsiElement target);

  public @Nullable HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile psiFile, @NotNull PsiElement target,
                                                                           @NotNull ProperTextRange visibleRange) {
    return createHighlightUsagesHandler(editor, psiFile, target);
  }

  private static PsiElement findTarget(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    int offset = TargetElementUtil.adjustOffset(psiFile, editor.getDocument(), editor.getCaretModel().getOffset());
    return psiFile.findElementAt(offset);
  }
}
