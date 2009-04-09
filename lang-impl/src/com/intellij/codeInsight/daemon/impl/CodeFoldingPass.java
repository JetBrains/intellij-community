package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

class CodeFoldingPass extends TextEditorHighlightingPass {
  private Runnable myRunnable;
  private final Editor myEditor;

  CodeFoldingPass(@NotNull Project project, @NotNull Editor editor) {
    super(project, editor.getDocument());
    myEditor = editor;
  }

  public void doCollectInformation(ProgressIndicator progress) {
    Runnable runnable = CodeFoldingManager.getInstance(myProject).updateFoldRegionsAsync(myEditor);
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
      runnable.run();
    }
  }
}
