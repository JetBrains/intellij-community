package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.javadoc.PsiDocComment;

public class CollapseExpandJavadocsHandler implements CodeInsightActionHandler {
  private final boolean myExpand;

  public CollapseExpandJavadocsHandler(boolean isExpand) {
    myExpand = isExpand;
  }

  public void invoke(Project project, final Editor editor, PsiFile file){
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(project);
    foldingManager.updateFoldRegions(editor);
    final FoldRegion[] allFoldRegions = editor.getFoldingModel().getAllFoldRegions();
    Runnable processor = new Runnable() {
      public void run() {
        for (FoldRegion region : allFoldRegions) {
          PsiElement element = EditorFoldingInfo.get(editor).getPsiElement(region);
          if (element instanceof PsiDocComment) {
            region.setExpanded(myExpand);
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
