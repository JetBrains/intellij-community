// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiEditorUtil {
  static PsiEditorUtil getInstance() {
    return ApplicationManager.getApplication().getService(PsiEditorUtil.class);
  }

  @Nullable
  Editor findEditorByPsiElement(@NotNull PsiElement element);

  static @Nullable Editor findEditor(@NotNull PsiElement element) {
    return getInstance().findEditorByPsiElement(element);
  }

  static @NotNull PsiFile getPsiFile(Editor editor) {
    Project project = editor.getProject();
    assert project != null;
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    assert psiFile != null;
    return psiFile;
  }

  /**
   * @deprecated use {@link PsiEditorUtil#getInstance()} instead
   */
  @Deprecated(forRemoval = true)
  final class Service {
    public static PsiEditorUtil getInstance() {
      return PsiEditorUtil.getInstance();
    }
  }
}
