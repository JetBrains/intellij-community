// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JavaIntroduceEmptyVariableHandlerBase {

  /**
   * The implementation is registered in {@code intellij.java.impl.refactorings}, so {@code null} is returned
   * when that module is not loaded.
   */
  static @Nullable JavaIntroduceEmptyVariableHandlerBase getInstance() {
    return ApplicationManager.getApplication().getService(JavaIntroduceEmptyVariableHandlerBase.class);
  }

  IntentionPreviewInfo generatePreview(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiType type);

  void invoke(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiType type);
}
