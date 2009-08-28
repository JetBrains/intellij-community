package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Created by IntelliJ IDEA.
 * User: ven
 * Date: Apr 10, 2003
 * Time: 8:52:53 PM
 * To change this template use Options | File Templates.
 */
public class CollapseSelectionHandler implements CodeInsightActionHandler {
  private static final String ourPlaceHolderText = "...";
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.CollapseSelectionHandler");

  public void invoke(Project project, final Editor editor, PsiFile file) {
    editor.getFoldingModel().runBatchFoldingOperation(
            new Runnable() {
              public void run() {
                final EditorFoldingInfo info = EditorFoldingInfo.get(editor);
                FoldingModelEx foldingModel = (FoldingModelEx) editor.getFoldingModel();
                if (editor.getSelectionModel().hasSelection()) {
                  int start = editor.getSelectionModel().getSelectionStart();
                  int end = editor.getSelectionModel().getSelectionEnd();
                  Document doc = editor.getDocument();
                  if (start < end && doc.getCharsSequence().charAt(end-1) == '\n') end--;
                  FoldRegion region;
                  if ((region = FoldingUtil.findFoldRegion(editor, start, end)) != null) {
                    if (info.getPsiElement(region) == null) {
                      editor.getFoldingModel().removeFoldRegion(region);
                      info.removeRegion(region);
                    }
                  } else if (!foldingModel.intersectsRegion(start, end)) {
                    region = foldingModel.addFoldRegion(start, end, ourPlaceHolderText);
                    LOG.assertTrue(region != null);
                    region.setExpanded(false);
                    int offset = Math.min(start + ourPlaceHolderText.length(), doc.getTextLength());
                    editor.getCaretModel().moveToOffset(offset);
                  }
                } else {
                  FoldRegion[] regions = FoldingUtil.getFoldRegionsAtOffset(editor, editor.getCaretModel().getOffset());
                  if (regions.length > 0) {
                    FoldRegion region = regions[0];
                    if (info.getPsiElement(region) == null) {
                      editor.getFoldingModel().removeFoldRegion(region);
                      info.removeRegion(region);
                    } else {
                      region.setExpanded(!region.isExpanded());
                    }
                  }
                }
              }
            }
    );
  }

  public boolean startInWriteAction() {
    return false;
  }
}
