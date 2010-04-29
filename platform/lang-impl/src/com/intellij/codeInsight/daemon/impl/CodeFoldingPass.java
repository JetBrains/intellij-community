/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;

class CodeFoldingPass extends TextEditorHighlightingPass implements DumbAware {
  private static final Key<Boolean> THE_FIRST_TIME = Key.create("FirstFoldingPass");
  private Runnable myRunnable;
  private final Editor myEditor;
  private final PsiFile myFile;

  CodeFoldingPass(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    super(project, editor.getDocument(), false);
    myEditor = editor;
    myFile = file;
  }

  public void doCollectInformation(ProgressIndicator progress) {
    final boolean firstTime = isFirstTime(myFile, myEditor, THE_FIRST_TIME);
    Runnable runnable = CodeFoldingManager.getInstance(myProject).updateFoldRegionsAsync(myEditor, firstTime);
    synchronized (this) {
      myRunnable = runnable;
    }
  }

  static boolean isFirstTime(PsiFile file, Editor editor, Key<Boolean> key) {
    return file.getUserData(key) == null || editor.getUserData(key) == null;
  }

  static void clearFirstTimeFlag(PsiFile file, Editor editor, Key<Boolean> key) {
    file.putUserData(key, Boolean.FALSE);
    editor.putUserData(key, Boolean.FALSE);
  }

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
    }

    if (InjectedLanguageUtil.getTopLevelFile(myFile) == myFile) {
      clearFirstTimeFlag(myFile, myEditor, THE_FIRST_TIME);
    }
  }
}
