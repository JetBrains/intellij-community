package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;

class CodeFoldingPass extends TextEditorHighlightingPass {
  private Runnable myRunnable;
  private Editor myEditor;

  public CodeFoldingPass(Project project, Editor editor) {
    super(project, editor.getDocument());
    myEditor = editor;
  }

  public void doCollectInformation(ProgressIndicator progress) {
    myRunnable = CodeFoldingManager.getInstance(myProject).updateFoldRegionsAsync(myEditor);
  }

  public void doApplyInformationToEditor() {
    if (myRunnable != null){
      myRunnable.run();
    }
  }
}
