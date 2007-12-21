package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author ven
 */
public class CollapseBlockHandler implements CodeInsightActionHandler {
  public static final String ourPlaceHolderText = "{...}";
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.CollapseBlockHandler");

  public void invoke(final Project project, final Editor editor, final PsiFile file) {
    editor.getFoldingModel().runBatchFoldingOperation(new Runnable() {
      public void run() {
        final EditorFoldingInfo info = EditorFoldingInfo.get(editor);
        FoldingModelEx model = (FoldingModelEx) editor.getFoldingModel();
        PsiElement element = file.findElementAt(editor.getCaretModel().getOffset() - 1);
        if (!(element instanceof PsiJavaToken) || ((PsiJavaToken) element).getTokenType() != JavaTokenType.RBRACE) {
          element = file.findElementAt(editor.getCaretModel().getOffset());
        }
        if (element == null) return;
        PsiCodeBlock block = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
        FoldRegion previous = null;
        FoldRegion myPrevious = null;
        while (block != null) {
          int start = block.getTextRange().getStartOffset();
          int end = block.getTextRange().getEndOffset();
          FoldRegion existing = FoldingUtil.findFoldRegion(editor, start, end);
          if (existing != null) {
            previous = existing;
            if (info.getPsiElement(existing) == null) myPrevious = existing;
            block = PsiTreeUtil.getParentOfType(block, PsiCodeBlock.class);
            continue;
          }
          if (!model.intersectsRegion(start, end)) {
            FoldRegion region = model.addFoldRegion(start, end, ourPlaceHolderText);
            LOG.assertTrue(region != null);
            region.setExpanded(false);
            if (myPrevious != null && info.getPsiElement(region) == null) {
              info.removeRegion(myPrevious);
              model.removeFoldRegion(myPrevious);
            }
            int offset = block.getTextRange().getEndOffset() < editor.getCaretModel().getOffset() ?
                start : end;
            editor.getCaretModel().moveToOffset(offset);
            return;
          } else break;
        }
        if (previous != null) {
          previous.setExpanded(false);
          if (myPrevious != null) {
            info.removeRegion(myPrevious);
            model.removeFoldRegion(myPrevious);
          }
          editor.getCaretModel().moveToOffset(previous.getEndOffset());
        }
      }
    });
  }

  public boolean startInWriteAction() {
    return true;
  }
}
