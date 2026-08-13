// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

final class ExtractMethodIntentionServiceImpl implements ExtractMethodIntentionService {

  @Override
  public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement[] elements = ExtractMethodHandler.getElements(project, editor, file);
    if (elements.length == 0) return false;
    if (PsiTreeUtil.getParentOfType(elements[0], PsiClass.class) == null) return false;
    ExtractMethodProcessor processor = ExtractMethodHandler.getProcessor(project, elements, file, false);
    if (processor == null) return false;
    try {
      return processor.prepare(null);
    }
    catch (PrepareFailedException e) {
      return false;
    }
  }

  @Override
  public void extractMethod(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    new ExtractMethodHandler().invoke(project, editor, file, null);
  }
}
