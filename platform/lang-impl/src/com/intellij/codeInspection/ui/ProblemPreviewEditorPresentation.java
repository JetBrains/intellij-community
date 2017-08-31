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
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.diff.tools.util.FoldingModelSupport;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.impl.UsagePreviewPanel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ProblemPreviewEditorPresentation {
  private final static int VIEW_ADDITIONAL_OFFSET = 4;

  static void setupFoldingsForNonProblemRanges(@NotNull EditorEx editor, @NotNull InspectionResultsView view) {
    List<UsageInfo> usages = Arrays.stream(view.getTree().getAllValidSelectedDescriptors())
      .filter(ProblemDescriptorBase.class::isInstance)
      .map(ProblemDescriptorBase.class::cast)
      .map(d -> {
        final PsiElement psi = d.getPsiElement();
        if (psi == null) {
          return null;
        }
        final TextRange range = d.getTextRangeInElement();
        return range == null ? new UsageInfo(psi) : new UsageInfo(psi, range.getStartOffset(), range.getEndOffset());
      })
      .collect(Collectors.toList());
    setupFoldingsForNonProblemRanges(editor, view, usages, view.getProject());
  }


  public static void setupFoldingsForNonProblemRanges(@NotNull EditorEx editor, @NotNull Container editorContainer,
                                                      @NotNull List<UsageInfo> usages, @NotNull Project project) {
    final Document doc = editor.getDocument();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    if (documentManager.isUncommited(doc)) {
      WriteAction.run(() -> documentManager.commitDocument(doc));
    }
    final SortedSet<PreviewEditorFoldingRegion> foldingRegions = new TreeSet<>(Comparator.comparing(x -> x.startLine));
    foldingRegions.add(new PreviewEditorFoldingRegion(0, doc.getLineCount()));
    boolean isUpdated = false;
    for (UsageInfo usage : usages) {
      if (usage == null) {
        return;
      }
      isUpdated |= makeVisible(foldingRegions, usage.getSegment(), doc);
    }
    if (isUpdated) {
      setupFoldings(editor, foldingRegions);
    }

    highlightProblems(editor, editorContainer, usages, project);
  }

  private static void highlightProblems(EditorEx editor, Container editorContainer, List<UsageInfo> usages, @NotNull Project project) {
    List<UsageInfo> validUsages = usages.stream().filter(Objects::nonNull).collect(Collectors.toList());
    PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(() -> {
      if (!editor.isDisposed()) {
        editorContainer.invalidate();
        editorContainer.validate();
        UsagePreviewPanel.highlight(validUsages, editor, project, false, HighlighterLayer.SELECTION);
        if (validUsages.size() == 1) {
          final PsiElement element = validUsages.get(0).getElement();
          if (element != null) {
            final Document document = editor.getDocument();
            final int offset = Math.min(element.getTextRange().getEndOffset() + VIEW_ADDITIONAL_OFFSET,
                                        document.getLineEndOffset(document.getLineNumber(element.getTextRange().getEndOffset())));
            editor.getScrollingModel().scrollTo(editor.offsetToLogicalPosition(offset), ScrollType.CENTER);
            return;
          }
        }
        editor.getScrollingModel().scrollTo(editor.offsetToLogicalPosition(0), ScrollType.CENTER_UP);
      }
    });
  }

  private static boolean inRegion(int position, PreviewEditorFoldingRegion range) {
    return range.startLine <= position && range.endLine > position;
  }

  private static void setupFoldings(EditorEx editor, SortedSet<PreviewEditorFoldingRegion> foldedRegions) {
    editor.getFoldingModel().runBatchFoldingOperation(() -> {
      editor.getFoldingModel().clearFoldRegions();
      editor.getMarkupModel().removeAllHighlighters();
      for (PreviewEditorFoldingRegion region : foldedRegions) {
        if (region.endLine - region.startLine > 1) {
          FoldRegion currentRegion = FoldingModelSupport.addFolding(editor,
                                                                    region.startLine,
                                                                    region.endLine,
                                                                    false);
          if (currentRegion != null) {
            DiffDrawUtil.createLineSeparatorHighlighter(editor,
                                                        editor.getDocument().getLineStartOffset(region.startLine),
                                                        editor.getDocument().getLineEndOffset(region.endLine - 1),
                                                        () -> currentRegion.isValid() && !currentRegion.isExpanded());
          }
        }
      }
    });
  }

  private static boolean makeVisible(SortedSet<PreviewEditorFoldingRegion> foldingRegions, Segment toShowRange, Document document) {
    if (toShowRange == null) return false;
    boolean isUpdated = false;
    final int startLine = Math.max(0, document.getLineNumber(toShowRange.getStartOffset()) - 1);
    final int endLine = Math.min(document.getLineCount(), document.getLineNumber(toShowRange.getEndOffset()) + 2);
    for (PreviewEditorFoldingRegion range : new ArrayList<>(foldingRegions)) {
      final boolean startInRegion = inRegion(startLine, range);
      final boolean endInRegion = inRegion(endLine, range);
      if (startInRegion && endInRegion) {
        foldingRegions.remove(range);
        if (range.startLine != startLine) {
          foldingRegions.add(new PreviewEditorFoldingRegion(range.startLine, startLine));
        }
        if (endLine != range.endLine) {
          foldingRegions.add(new PreviewEditorFoldingRegion(endLine, range.endLine));
        }
        return true;
      }
      if (startInRegion) {
        foldingRegions.remove(range);
        if (range.startLine != startLine) {
          foldingRegions.add(new PreviewEditorFoldingRegion(range.startLine, startLine));
        }
        isUpdated = true;
      }
      if (endInRegion) {
        foldingRegions.remove(range);
        if (endLine != range.endLine) {
          foldingRegions.add(new PreviewEditorFoldingRegion(endLine, range.endLine));
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
