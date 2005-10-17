package com.intellij.codeInsight.folding.impl;

import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

public class ExpandAllRegionsHandler implements CodeInsightActionHandler {
  public void invoke(Project project, final Editor editor, PsiFile file){
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(project);
    foldingManager.updateFoldRegions(editor);

    final FoldRegion[] regions = editor.getFoldingModel().getAllFoldRegions();
    Runnable processor = new Runnable() {
        public void run() {
          boolean anythingDone = false;
          for (int i = 0; i < regions.length; i++) { // try to restore to default state at first
            FoldRegion region = regions[i];
            PsiElement element = EditorFoldingInfo.get(editor).getPsiElement(region);
            if (!region.isExpanded() && (element == null || !FoldingPolicy.isCollapseByDefault(element))){
              region.setExpanded(true);
              anythingDone = true;
            }
          }

          if (!anythingDone){
            for(int i = 0; i < regions.length; i++){
              FoldRegion region = regions[i];
              region.setExpanded(true);
            }
          }
        }
      };
    editor.getFoldingModel().runBatchFoldingOperation(processor);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
