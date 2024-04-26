// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.folding.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

final class InjectedCodeFoldingPass extends TextEditorHighlightingPass {
  private static final Key<Boolean> THE_FIRST_TIME_KEY = Key.create("FirstInjectedFoldingPass");
  private Runnable myRunnable;
  private final Editor myEditor;
  private final PsiFile myFile;

  InjectedCodeFoldingPass(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    super(project, editor.getDocument(), false);
    myEditor = editor;
    myFile = file;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    boolean firstTime = CodeFoldingPass.isFirstTime(myFile, myEditor, THE_FIRST_TIME_KEY);
    Runnable runnable = FoldingUpdate.updateInjectedFoldRegions(myEditor, myFile, firstTime);
    synchronized (this) {
      myRunnable = runnable;
    }
  }

  @Override
  public void doApplyInformationToEditor() {
    Runnable runnable;
    synchronized (this) {
      runnable = myRunnable;
    }
    if (runnable != null){
      try {
        runnable.run();
      }
      catch (IndexNotReadyException ignored) {
      }
      CodeFoldingPass.clearFirstTimeFlag(myFile, myEditor, THE_FIRST_TIME_KEY);
    }
  }
}