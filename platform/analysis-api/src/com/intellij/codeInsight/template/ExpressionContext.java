// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public interface ExpressionContext {
  @NonNls Key<String> SELECTION = Key.create("SELECTION");

  @Contract(pure = true)
  Project getProject();

  /**
   * @return the editor associated with the template. Avoid using this method, as sometimes there could be no editor.
   * If you need {@link PsiFile}, use {@link #getPsiFile()}. If you need {@link Document}, use {@code getPsiFile().getFileDocument()}.
   */
  @Nullable
  @Contract(pure = true)
  Editor getEditor();

  /**
   * @return PsiFile where the template is executed; null if not applicable
   */
  @Contract(pure = true)
  @Nullable
  default PsiFile getPsiFile() {
    Editor editor = getEditor();
    if (editor == null) return null;
    return PsiDocumentManager.getInstance(getProject()).getPsiFile(editor.getDocument());
  }

  @Contract(pure = true)
  int getStartOffset();

  @Contract(pure = true)
  int getTemplateStartOffset();

  @Contract(pure = true)
  int getTemplateEndOffset();

  <T> T getProperty(Key<T> key);

  @Nullable
  PsiElement getPsiElementAtStartOffset();

  @Nullable
  TextResult getVariableValue(String variableName);
}

