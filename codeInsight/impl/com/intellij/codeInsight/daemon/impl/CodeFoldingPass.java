package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;

class CodeFoldingPass extends TextEditorHighlightingPass {
  private Runnable myRunnable;
  private Project myProject;
  private Editor myEditor;

  public CodeFoldingPass(Project project, Editor editor) {
    super(project, editor.getDocument());
    myProject = project;
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

  public int getPassId() {
    return Pass.UPDATE_FOLDING;
  }
}
