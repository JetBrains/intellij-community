/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class CollapseSelectionHandler implements CodeInsightActionHandler {
  private static final String ourPlaceHolderText = "...";
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.CollapseSelectionHandler");

  @Override
  public void invoke(@NotNull Project project, @NotNull final Editor editor, @NotNull PsiFile file) {
    editor.getFoldingModel().runBatchFoldingOperation(
            new Runnable() {
              @Override
              public void run() {
                final EditorFoldingInfo info = EditorFoldingInfo.get(editor);
                FoldingModelEx foldingModel = (FoldingModelEx) editor.getFoldingModel();
                if (editor.getSelectionModel().hasSelection()) {
                  int start = editor.getSelectionModel().getSelectionStart();
                  int end = editor.getSelectionModel().getSelectionEnd();
                  if (start + 1 >= end) {
                    return;
                  }
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
                    LOG.assertTrue(region != null, "Fold region is not created. Folding model: " + foldingModel);
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
  
  public static boolean isEnabled(@NotNull Editor editor) {
    if (editor.getSelectionModel().hasSelection()) {
      int start = editor.getSelectionModel().getSelectionStart();
      int end = editor.getSelectionModel().getSelectionEnd();
      if (start + 1 >= end) {
        return false;
      }
      if (start < end && editor.getDocument().getCharsSequence().charAt(end - 1) == '\n') end--;
      FoldRegion region = FoldingUtil.findFoldRegion(editor, start, end);
      if (region == null) {
        return !((FoldingModelEx) editor.getFoldingModel()).intersectsRegion(start, end); 
      } else {
        return EditorFoldingInfo.get(editor).getPsiElement(region) == null;
      }
    } else {
      return FoldingUtil.getFoldRegionsAtOffset(editor, editor.getCaretModel().getOffset()).length > 0;
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
