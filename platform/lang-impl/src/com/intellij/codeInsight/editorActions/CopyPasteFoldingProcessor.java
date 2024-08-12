// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.folding.impl.CodeFoldingManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.RangeMarker;
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


public final class CopyPasteFoldingProcessor extends CopyPastePostProcessor<FoldingTransferableData> {
  private static final Logger LOG = Logger.getInstance(CopyPasteFoldingProcessor.class);

  @Override
  public @NotNull List<FoldingTransferableData> collectTransferableData(final @NotNull PsiFile file, final @NotNull Editor editor, final int @NotNull [] startOffsets, final int @NotNull [] endOffsets) {
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

  @Override
  public @NotNull List<FoldingTransferableData> extractTransferableData(final @NotNull Transferable content) {
    DataFlavor flavor = FoldingData.getDataFlavor();
    if (flavor == null) {
      return Collections.emptyList();
    }

    Object data;
    try {
      data = content.getTransferData(flavor);
    }
    catch (UnsupportedFlavorException | IOException e) {
      //ignore exception
      return Collections.emptyList();
    }

    if (!(data instanceof FoldingTransferableData)) {
      LOG.error("Transferable content has returned invalid data\ncontent: " + content + "\ndata: " + data);
      return Collections.emptyList();
    }

    // copy to prevent changing of original by convertLineSeparators
    FoldingTransferableData foldingData = ((FoldingTransferableData)data).clone();
    return Collections.singletonList(foldingData);
  }

  @Override
  public void processTransferableData(final @NotNull Project project,
                                      final @NotNull Editor editor,
                                      final @NotNull RangeMarker bounds,
                                      int caretOffset,
                                      @NotNull Ref<? super Boolean> indented,
                                      final @NotNull List<? extends FoldingTransferableData> values) {
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
