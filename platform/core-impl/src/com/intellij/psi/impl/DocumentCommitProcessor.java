// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface DocumentCommitProcessor {
  void commitSynchronously(@NotNull Document document, @NotNull Project project, @NotNull PsiFile psiFile);

  void commitAsynchronously(@NotNull Project project,
                            @NotNull PsiDocumentManagerBase documentManager,
                            @NotNull Document document,
                            @NonNls @NotNull Object reason,
                            @NotNull ModalityState modality,
                            @NotNull FileViewProvider cachedViewProvider);
}
