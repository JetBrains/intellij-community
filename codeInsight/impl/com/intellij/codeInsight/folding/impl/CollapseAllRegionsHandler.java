package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

public class CollapseAllRegionsHandler implements CodeInsightActionHandler {
  public void invoke(Project project, final Editor editor, PsiFile file){
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    CodeFoldingManager.getInstance(project).updateFoldRegions(editor);
    final FoldRegion[] allFoldRegions = editor.getFoldingModel().getAllFoldRegions();
    Runnable processor = new Runnable() {
      public void run() {
        for (int i = 0; i < allFoldRegions.length; i++) {
          FoldRegion region = allFoldRegions[i];
          region.setExpanded(false);
        }
      }
    };
    editor.getFoldingModel().runBatchFoldingOperation(processor);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
