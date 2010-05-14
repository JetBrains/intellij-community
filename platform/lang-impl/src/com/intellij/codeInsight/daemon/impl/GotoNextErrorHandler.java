
/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GotoNextErrorHandler implements CodeInsightActionHandler {
  private final boolean myGoForward;

  public GotoNextErrorHandler(boolean goForward) {
    myGoForward = goForward;
  }

  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    int caretOffset = editor.getCaretModel().getOffset();
    gotoNextError(project, editor, file, caretOffset);
  }

  public boolean startInWriteAction() {
    return false;
  }

  private void gotoNextError(Project project, Editor editor, PsiFile file, int caretOffset) {
    final SeverityRegistrar severityRegistrar = SeverityRegistrar.getInstance(project);
    List<HighlightInfo> highlights = DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), severityRegistrar.getSeverityByIndex(0), project);
    if (highlights.isEmpty()){
      showMessageWhenNoHighlights(project, file, editor);
      return;
    }
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    if (settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST) {
      for (int idx = severityRegistrar.getSeveritiesCount() - 1; idx >= 0; idx--) {
        final HighlightSeverity minSeverity = severityRegistrar.getSeverityByIndex(idx);
        List<HighlightInfo> errors = DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), minSeverity, project);
        if (!errors.isEmpty()) {
          highlights = errors;
          break;
        }
      }
    }

    HighlightInfo infoToGo = findInfoToGo(editor, caretOffset, highlights, true);
    if (infoToGo == null) {
      infoToGo = findInfoToGo(editor, caretOffset, highlights, false);
    }
    if (infoToGo != null) {
      navigateToError(project, editor, infoToGo);
    }
  }

  @Nullable
  private HighlightInfo findInfoToGo(Editor editor, int caretOffset, List<HighlightInfo> highlights, boolean skip) {
    int offsetToGo = myGoForward ? Integer.MAX_VALUE : Integer.MIN_VALUE;
    int offsetToGoIfNoLuck = offsetToGo;
    HighlightInfo infoToGo = null;
    HighlightInfo infoToGoIfNoLuck = null;
    int caretOffsetIfNoLuck = myGoForward ? -1 : editor.getDocument().getTextLength();
    for (HighlightInfo info : highlights) {
      if (skip) {
        if (SeverityRegistrar.skipSeverity(info.getSeverity())) continue;
      }
      int startOffset = getNavigationPositionFor(info, editor.getDocument());
      if (isBetter(caretOffset, offsetToGo, startOffset)) {
        offsetToGo = startOffset;
        infoToGo = info;
      }
      if (isBetter(caretOffsetIfNoLuck, offsetToGoIfNoLuck, startOffset)) {
        offsetToGoIfNoLuck = startOffset;
        infoToGoIfNoLuck = info;
      }
    }
    if (infoToGo == null) infoToGo = infoToGoIfNoLuck;
    return infoToGo;
  }

  private boolean isBetter(int caretOffset, int offsetToGo, int startOffset) {
    return myGoForward ? startOffset > caretOffset && startOffset < offsetToGo
                         : startOffset < caretOffset && startOffset > offsetToGo;
  }

  static void showMessageWhenNoHighlights(Project project, PsiFile file, Editor editor) {
    DaemonCodeAnalyzerImpl codeHighlighter = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    String message = codeHighlighter.isErrorAnalyzingFinished(file)
                     ? InspectionsBundle.message("no.errors.found.in.this.file")
                     : InspectionsBundle.message("error.analysis.is.in.progress");
    HintManager.getInstance().showInformationHint(editor, message);
  }

  static void navigateToError(Project project, final Editor editor, HighlightInfo info) {
    int oldOffset = editor.getCaretModel().getOffset();

    final int offset = getNavigationPositionFor(info, editor.getDocument());
    final int endOffset = info.highlighter.getEndOffset();

    final ScrollingModel scrollingModel = editor.getScrollingModel();
    if (offset != oldOffset) {
      ScrollType scrollType = offset > oldOffset ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
      editor.getSelectionModel().removeSelection();
      editor.getCaretModel().moveToOffset(offset);
      scrollingModel.scrollToCaret(scrollType);
    }

    scrollingModel.runActionOnScrollingFinished(
      new Runnable(){
        public void run() {
          int maxOffset = editor.getDocument().getTextLength() - 1;
          if (maxOffset == -1) return;
          scrollingModel.scrollTo(editor.offsetToLogicalPosition(Math.min(maxOffset, endOffset)), ScrollType.MAKE_VISIBLE);
          scrollingModel.scrollTo(editor.offsetToLogicalPosition(Math.min(maxOffset, offset)), ScrollType.MAKE_VISIBLE);
        }
      }
    );

    IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
  }

  private static int getNavigationPositionFor(HighlightInfo info, Document document) {
    int start = info.highlighter.getStartOffset();
    if (start >= document.getTextLength()) return document.getTextLength();
    char c = document.getCharsSequence().charAt(start);
    int shift = info.isAfterEndOfLine && c != '\n' ? 1 : info.navigationShift;

    int offset = info.highlighter.getStartOffset() + shift;
    return Math.min(offset, document.getTextLength());
  }
}
