/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.diff.tools.util.FoldingModelSupport;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.impl.UsagePreviewPanel;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Dmitry Batkovich
 */
public class ProblemPreviewEditorPresentation {
  private final static int VIEW_ADDITIONAL_OFFSET = 4;

  private final EditorEx myEditor;
  private final InspectionResultsView myView;
  private final SortedSet<PreviewEditorFoldingRegion> myFoldedRegions = new TreeSet<>(Comparator.comparing(x -> x.startLine));
  private final DocumentEx myDocument;

  public ProblemPreviewEditorPresentation(EditorEx editor, InspectionResultsView view) {
    myEditor = editor;
    myView = view;
    myDocument = editor.getDocument();
    myFoldedRegions.add(new PreviewEditorFoldingRegion(0, myDocument.getLineCount()));
    appendFoldings(view.getTree().getAllValidSelectedDescriptors());
  }

  private static boolean inRegion(int position, PreviewEditorFoldingRegion range) {
    return range.startLine <= position && range.endLine > position;
  }

  private void appendFoldings(CommonProblemDescriptor[] descriptors) {
    final boolean[] isUpdated = new boolean[]{false};
    final boolean[] allValid = new boolean[]{true};
    final List<UsageInfo> elements = Arrays.stream(descriptors)
      .filter(ProblemDescriptorBase.class::isInstance)
      .map(ProblemDescriptorBase.class::cast)
      .map(ProblemDescriptorBase::getPsiElement)
      .filter(e -> {
        final boolean isValid = e != null && e.isValid();
        allValid[0] &= isValid;
        return isValid;
      })
      .peek((e) -> isUpdated[0] |= appendFoldings(e.getTextRange()))
      .map(UsageInfo::new)
      .collect(Collectors.toList());
    if (!allValid[0]) return;

    if (isUpdated[0] && descriptors.length > 1) {
      updateFoldings();
    }

    PsiDocumentManager.getInstance(myView.getProject()).performLaterWhenAllCommitted(() -> {
      if (!myEditor.isDisposed()) {
        myView.invalidate();
        myView.validate();
        UsagePreviewPanel.highlight(elements, myEditor, myView.getProject(), false, HighlighterLayer.SELECTION);
        if (elements.size() == 1) {
          final PsiElement element = elements.get(0).getElement();
          if (element != null) {
            final DocumentEx document = myEditor.getDocument();
            final int offset = Math.min(element.getTextRange().getEndOffset() + VIEW_ADDITIONAL_OFFSET,
                                        document.getLineEndOffset(document.getLineNumber(element.getTextRange().getEndOffset())));
            myEditor.getScrollingModel().scrollTo(myEditor.offsetToLogicalPosition(offset), ScrollType.CENTER);
            return;
          }
        }
        myEditor.getScrollingModel().scrollTo(myEditor.offsetToLogicalPosition(0), ScrollType.CENTER_UP);
      }
    });
  }

  private void updateFoldings() {
    myEditor.getFoldingModel().runBatchFoldingOperation(() -> {
      myEditor.getFoldingModel().clearFoldRegions();
      myEditor.getMarkupModel().removeAllHighlighters();
      for (PreviewEditorFoldingRegion region : myFoldedRegions) {
        if (region.endLine - region.startLine > 1) {
          FoldRegion currentRegion = FoldingModelSupport.addFolding(myEditor,
                                                                    region.startLine,
                                                                    region.endLine,
                                                                    false);
          if (currentRegion != null) {
            DiffDrawUtil.createLineSeparatorHighlighter(myEditor,
                                                        myDocument.getLineStartOffset(region.startLine),
                                                        myDocument.getLineEndOffset(region.endLine - 1),
                                                        () -> currentRegion.isValid() && !currentRegion.isExpanded());
          }
        }
      }
    });
  }

  private boolean appendFoldings(TextRange toShowRange) {
    boolean isUpdated = false;
    final int startLine = Math.max(0, myDocument.getLineNumber(toShowRange.getStartOffset()) - 1);
    final int endLine = Math.min(myDocument.getLineCount(), myDocument.getLineNumber(toShowRange.getEndOffset()) + 2);
    for (PreviewEditorFoldingRegion range : new ArrayList<>(myFoldedRegions)) {
      final boolean startInRegion = inRegion(startLine, range);
      final boolean endInRegion = inRegion(endLine, range);
      if (startInRegion && endInRegion) {
        myFoldedRegions.remove(range);
        if (range.startLine != startLine) {
          myFoldedRegions.add(new PreviewEditorFoldingRegion(range.startLine, startLine));
        }
        if (endLine != range.endLine) {
          myFoldedRegions.add(new PreviewEditorFoldingRegion(endLine, range.endLine));
        }
        return true;
      }
      if (startInRegion) {
        myFoldedRegions.remove(range);
        if (range.startLine != startLine) {
          myFoldedRegions.add(new PreviewEditorFoldingRegion(range.startLine, startLine));
        }
        isUpdated = true;
      }
      if (endInRegion) {
        myFoldedRegions.remove(range);
        if (endLine != range.endLine) {
          myFoldedRegions.add(new PreviewEditorFoldingRegion(endLine, range.endLine));
        }
        return true;
      }
    }
    return isUpdated;
  }

  private static class PreviewEditorFoldingRegion {
    public final int startLine;
    public final int endLine;

    private PreviewEditorFoldingRegion(int startLine, int endLine) {
      this.startLine = startLine;
      this.endLine = endLine;
    }
  }
}
