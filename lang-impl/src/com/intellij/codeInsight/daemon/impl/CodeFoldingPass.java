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
    final boolean firstTime = myFile.getUserData(THE_FIRST_TIME) == null || myEditor.getUserData(THE_FIRST_TIME) == null;
    Runnable runnable = CodeFoldingManager.getInstance(myProject).updateFoldRegionsAsync(myEditor, firstTime);
    synchronized (this) {
      myRunnable = runnable;
    }
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
      catch (IndexNotReadyException e) {
      }
    }

    if (InjectedLanguageUtil.getTopLevelFile(myFile) == myFile) {
      myFile.putUserData(THE_FIRST_TIME, Boolean.FALSE);
      myEditor.putUserData(THE_FIRST_TIME, Boolean.FALSE);
    }
  }
}
