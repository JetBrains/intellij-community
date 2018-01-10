/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.folding.impl.EditorFoldingInfo;
import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class CollapseBlockHandler implements CodeInsightActionHandler {
  private static final String ourPlaceHolderText = "{...}";
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.CollapseBlockHandler");

  @Override
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    int[] targetCaretOffset = {-1};
    editor.getFoldingModel().runBatchFoldingOperation(() -> {
      final EditorFoldingInfo info = EditorFoldingInfo.get(editor);
      FoldingModelEx model = (FoldingModelEx) editor.getFoldingModel();
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset() - 1);
      if (!(element instanceof PsiJavaToken) || ((PsiJavaToken) element).getTokenType() != JavaTokenType.RBRACE) {
        element = file.findElementAt(editor.getCaretModel().getOffset());
      }
      if (element == null) return;
      PsiElement block = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
      FoldRegion previous = null;
      FoldRegion myPrevious = null;
      while (block != null) {
        int start = block.getTextRange().getStartOffset();
        int end = block.getTextRange().getEndOffset();
        FoldRegion existing = FoldingUtil.findFoldRegion(editor, start, end);
        if (existing != null) {
          if (existing.isExpanded()) {
            existing.setExpanded(false);
            targetCaretOffset[0] = existing.getEndOffset();
            return;
          }
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
          targetCaretOffset[0] = block.getTextRange().getEndOffset() < editor.getCaretModel().getOffset() ? start : end;
          return;
        } else break;
      }
      if (previous != null) {
        previous.setExpanded(false);
        if (myPrevious != null) {
          info.removeRegion(myPrevious);
          model.removeFoldRegion(myPrevious);
        }
        targetCaretOffset[0] = previous.getEndOffset();
      }
    });
    if (targetCaretOffset[0] >= 0) editor.getCaretModel().moveToOffset(targetCaretOffset[0]);
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
