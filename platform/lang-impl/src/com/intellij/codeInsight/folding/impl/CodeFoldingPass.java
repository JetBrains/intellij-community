// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.folding.impl;

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;

final class CodeFoldingPass extends EditorBoundHighlightingPass implements PossiblyDumbAware {
  private static final Key<Boolean> THE_FIRST_TIME = Key.create("FirstFoldingPass");

  private volatile Runnable myRunnable;

  CodeFoldingPass(@NotNull Editor editor, @NotNull PsiFile file) {
    super(editor, file, false);
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    boolean firstTime = isFirstTime(myFile, myEditor, THE_FIRST_TIME);
    try (var ignored = runPass()) {
      myRunnable = CodeFoldingManager.getInstance(myProject).updateFoldRegionsAsync(myEditor, firstTime);
    }
  }

  static boolean isFirstTime(PsiFile file, Editor editor, Key<Boolean> key) {
    return file.getUserData(key) == null || editor.getUserData(key) == null;
  }

  static void clearFirstTimeFlag(PsiFile file, Editor editor, Key<? super Boolean> key) {
    file.putUserData(key, Boolean.FALSE);
    editor.putUserData(key, Boolean.FALSE);
  }

  @Override
  public void doApplyInformationToEditor() {
    Runnable runnable = myRunnable;
    if (runnable != null){
      try (AccessToken ignore = SlowOperations.knownIssue("IDEA-333911, EA-840750")) {
        runnable.run();
      }
      catch (IndexNotReadyException ignored) {
      }
    }

    if (InjectedLanguageManager.getInstance(myFile.getProject()).getTopLevelFile(myFile) == myFile) {
      clearFirstTimeFlag(myFile, myEditor, THE_FIRST_TIME);
    }
  }

  /**
   * Checks the ability to update folding in the Dumb Mode. True by default.
   * @return true if the language implementation can update folding ranges
   */
  @Override
  public boolean isDumbAware() {
    return FoldingUpdate.supportsDumbModeFolding(myFile);
  }
}
