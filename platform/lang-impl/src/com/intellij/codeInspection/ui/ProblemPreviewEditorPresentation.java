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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Dmitry Batkovich
 */
public class ProblemPreviewEditorPresentation {
  private final static Logger LOG = Logger.getInstance(ProblemPreviewEditorPresentation.class);

  private final EditorEx myEditor;
  private final Project myProject;
  private final Set<CommonProblemDescriptor> myDescriptors = new HashSet<>();
  private final SortedSet<PreviewEditorFoldingRegion> myFoldedRegions = new TreeSet<>(new Comparator<PreviewEditorFoldingRegion>() {
    @Override
    public int compare(PreviewEditorFoldingRegion r1, PreviewEditorFoldingRegion r2) {
      if (r1 == r2) return 0;
      final int diff = r1.startLine - r2.startLine;
      LOG.assertTrue(diff != 0);
      return diff;
    }
  });
  private final DocumentEx myDocument;

  public ProblemPreviewEditorPresentation(EditorEx editor, Project project) {
    myEditor = editor;
    myProject = project;
    myDocument = editor.getDocument();
    myFoldedRegions.add(new PreviewEditorFoldingRegion(0, myDocument.getLineCount()));
  }

  void appendFoldings(CommonProblemDescriptor[] descriptors) {
    final boolean[] isUpdated = new boolean[]{false};
    final List<UsageInfo> elements = Arrays.stream(descriptors)
      .filter(myDescriptors::add)
      .filter(d -> d instanceof ProblemDescriptorBase)
      .map(d -> ((ProblemDescriptorBase)d).getPsiElement())
      .filter(e -> e != null && e.isValid())
      .map(ProblemPreviewEditorPresentation::getWholeElement)
      .map((e) -> {
        isUpdated[0] |= appendFoldings(e.getTextRange());
        return e;
      })
      .map(UsageInfo::new)
      .collect(Collectors.toList());
    if (isUpdated[0]) {
      updateFoldings();
    }
    UsagePreviewPanel.highlight(elements, myEditor, myProject, false, HighlighterLayer.SELECTION);
  }

  /**
   * Usually we don't highlight whole the element: only its name identifier.
   * For ex: "Declaration access can be weaker" inspection.
   */
  @NotNull
  private static PsiElement getWholeElement(@NotNull PsiElement element) {
    PsiNameIdentifierOwner nameIdentifierOwner = PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner.class);
    if (nameIdentifierOwner != null && nameIdentifierOwner.getNameIdentifier() == element) {
      return nameIdentifierOwner;
    }
    return element;
  }

  private void updateFoldings() {
    myEditor.getFoldingModel().runBatchFoldingOperation(() -> {
      myEditor.getFoldingModel().clearFoldRegions();
      myEditor.getMarkupModel().removeAllHighlighters();
      if (myDescriptors.size() > 1) {
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

  private static boolean inRegion(int position, PreviewEditorFoldingRegion range) {
    return range.startLine <= position && range.endLine > position;
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
