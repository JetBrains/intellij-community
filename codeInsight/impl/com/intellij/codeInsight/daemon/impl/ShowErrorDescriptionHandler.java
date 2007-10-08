package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public class ShowErrorDescriptionHandler implements CodeInsightActionHandler {
  private final int myWidth;

  public ShowErrorDescriptionHandler(final int width) {
    myWidth = width;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    HighlightInfo info = ((DaemonCodeAnalyzerImpl)codeAnalyzer).findHighlightByOffset(editor.getDocument(), offset, false);
    if (info != null) {
      DaemonTooltipUtil.showInfoTooltip(info, editor, editor.getCaretModel().getOffset(), myWidth);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}
