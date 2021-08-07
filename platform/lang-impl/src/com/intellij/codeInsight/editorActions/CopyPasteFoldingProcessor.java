/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.folding.impl.CodeFoldingManagerImpl;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class CopyPasteFoldingProcessor extends CopyPastePostProcessor<FoldingTransferableData> {
  @NotNull
  @Override
  public List<FoldingTransferableData> collectTransferableData(final PsiFile file, final Editor editor, final int[] startOffsets, final int[] endOffsets) {
    // might be slow
    //CodeFoldingManager.getInstance(file.getManager().getProject()).updateFoldRegions(editor);

    final ArrayList<FoldingData> list = new ArrayList<>();
    final FoldRegion[] regions = editor.getFoldingModel().getAllFoldRegions();
    for (final FoldRegion region : regions) {
      if (!region.isValid()) continue;
      int refOffset = 0;
      for (int j = 0; j < startOffsets.length; j++) {
        refOffset += startOffsets[j];
        if (startOffsets[j] <= region.getStartOffset() && region.getEndOffset() <= endOffsets[j]) {
          list.add(
            new FoldingData(
              region.getStartOffset() - refOffset, // offsets should be relative to clipboard contents start
              region.getEndOffset() - refOffset,
              region.isExpanded(),
              region.getPlaceholderText()
            )
          );
          break;
        }
        refOffset -= endOffsets[j] + 1; // 1 accounts for line break inserted between contents corresponding to different carets
      }
    }

    return Collections.singletonList(new FoldingTransferableData(list.toArray(new FoldingData[0])));
  }

  @NotNull
  @Override
  public List<FoldingTransferableData> extractTransferableData(final Transferable content) {
    FoldingTransferableData foldingData = null;
    try {
      final DataFlavor flavor = FoldingData.getDataFlavor();
      if (flavor != null) {
        foldingData = (FoldingTransferableData)content.getTransferData(flavor);
      }
    }
    catch (UnsupportedFlavorException | IOException e) {
      // do nothing
    }

    if (foldingData != null) { // copy to prevent changing of original by convertLineSeparators
      return Collections.singletonList(foldingData.clone());
    }
    return Collections.emptyList();
  }

  @Override
  public void processTransferableData(final Project project,
                                      final Editor editor,
                                      final RangeMarker bounds,
                                      int caretOffset,
                                      Ref<? super Boolean> indented,
                                      final List<? extends FoldingTransferableData> values) {
    assert values.size() == 1;
    final FoldingTransferableData value = values.get(0);
    if (value.getData().length == 0 || indented.get() != null) {
      // if `indented` is TRUE or FALSE, the pasted text was changes and folding offsets are not valid
      return;
    }

    final CodeFoldingManagerImpl foldingManager = (CodeFoldingManagerImpl)CodeFoldingManager.getInstance(project);
    if (foldingManager == null) return; // default project

    Runnable operation = () -> {
      final FoldingModel model = editor.getFoldingModel();
      final int docLength = editor.getDocument().getTextLength();
      for (FoldingData data : value.getData()) {
        final int start = data.startOffset + bounds.getStartOffset();
        final int end = data.endOffset + bounds.getStartOffset();
        if (start >= 0 && end <= docLength && start <= end) {
          final FoldRegion region = model.addFoldRegion(start, end, data.placeholderText);
          if (region != null) {
            foldingManager.markForUpdate(region);
            region.setExpanded(data.isExpanded);
          }
        }
      }
    };
    int verticalPositionBefore = editor.getScrollingModel().getVisibleAreaOnScrollingFinished().y;
    editor.getFoldingModel().runBatchFoldingOperation(operation);
    EditorUtil.runWithAnimationDisabled(editor, () -> editor.getScrollingModel().scrollVertically(verticalPositionBefore));
  }

  @Override
  public boolean requiresAllDocumentsToBeCommitted(@NotNull Editor editor, @NotNull Project project) {
    return false;
  }
}
