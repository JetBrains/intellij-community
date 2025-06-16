// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.jigsaw.JigsawUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GenerateProviderMethodHandler implements CodeInsightActionHandler {
  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    if (!(psiFile instanceof PsiJavaFile)) return;

    int offset = editor.getCaretModel().getOffset();
    PsiElement context = getContext(psiFile.findElementAt(offset));
    if (context == null) return;

    PsiClass targetClass = PsiTreeUtil.getParentOfType(context, PsiClass.class, false);

    if (!JigsawUtil.checkProviderMethodAccessible(targetClass)) return;
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(targetClass)) return;

    WriteCommandAction.writeCommandAction(project, psiFile)
      .run(() -> JigsawUtil.addProviderMethod(targetClass, editor, getOffset(context, offset)));
  }

  /**
   * Retrieves the context of a given PsiElement.
   * The context refers to the nearest parent that is a PsiClass.
   *
   * @param context the initial PsiElement
   * @return the context PsiElement, or null if no context is found
   */
  private static @Nullable PsiElement getContext(@Nullable PsiElement context) {
    while (context != null && !(context.getParent() instanceof PsiClass)) {
      context = context.getParent();
    }
    return context;
  }

  private static int getOffset(@NotNull PsiElement context, int defaultOffset) {
    PsiElement child = context.getFirstChild();
    return child != null ? child.getTextOffset() : defaultOffset;
  }
}
