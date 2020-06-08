// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiEditorUtil {
  @Nullable
  Editor findEditorByPsiElement(@NotNull PsiElement element);

  @Nullable
  static Editor findEditor(@NotNull PsiElement element) {
    return Service.getInstance().findEditorByPsiElement(element);
  }

  final class Service {
    public static PsiEditorUtil getInstance() {
      return ServiceManager.getService(PsiEditorUtil.class);
    }
  }

  @NotNull
  static PsiFile getPsiFile(Editor editor) {
    Project project = editor.getProject();
    assert project != null;
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    assert psiFile != null;
    return psiFile;
  }
}
