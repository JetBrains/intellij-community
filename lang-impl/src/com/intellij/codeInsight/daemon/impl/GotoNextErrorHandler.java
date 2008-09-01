
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public class GotoNextErrorHandler implements CodeInsightActionHandler {
  private final boolean myGoForward;

  public GotoNextErrorHandler(boolean goForward) {
    myGoForward = goForward;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    int caretOffset = editor.getCaretModel().getOffset();
    gotoNextError(project, editor, file, caretOffset);
  }

  public boolean startInWriteAction() {
    return false;
  }

  private void gotoNextError(Project project, Editor editor, PsiFile file, int caretOffset) {
    final SeverityRegistrar severityRegistrar = SeverityRegistrar.getInstance(project);
    HighlightInfo[] highlights = DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), severityRegistrar.getSeverityByIndex(0), project);
    if (highlights.length == 0){
      showMessageWhenNoHighlights(project, file, editor);
      return;
    }
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    if (settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST) {
      for (int idx = severityRegistrar.getSeveritiesCount() - 1; idx >= 0; idx--) {
        HighlightInfo[] errors = DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), severityRegistrar.getSeverityByIndex(idx), project);
        if (errors.length != 0) {
          highlights = errors;
          break;
        }
      }
    }

    int offsetToGo = myGoForward ? Integer.MAX_VALUE : Integer.MIN_VALUE;
    int offsetToGoIfNoLuck = offsetToGo;
    HighlightInfo infoToGo = null;
    HighlightInfo infoToGoIfNoLuck = null;
    int caretOffsetIfNoLuck = myGoForward ? -1 : editor.getDocument().getTextLength();
    for (HighlightInfo info : highlights) {
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

    if (infoToGo != null) {
      navigateToError(project, editor, infoToGo);
    }
  }

  private boolean isBetter(int caretOffset, int offsetToGo, int startOffset) {
    return myGoForward ? startOffset > caretOffset && startOffset < offsetToGo
                         : startOffset < caretOffset && startOffset > offsetToGo;
  }

  static void showMessageWhenNoHighlights(Project project, PsiFile file, Editor editor) {
    DaemonCodeAnalyzerImpl codeHighlighter = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    String message;
    if (codeHighlighter.isErrorAnalyzingFinished(file)){
      message = InspectionsBundle.message("no.errors.found.in.this.file");
    }
    else{
      message = InspectionsBundle.message("error.analysis.is.in.progress");
    }
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
          scrollingModel.scrollTo(editor.offsetToLogicalPosition(endOffset), ScrollType.MAKE_VISIBLE);
          scrollingModel.scrollTo(editor.offsetToLogicalPosition(offset), ScrollType.MAKE_VISIBLE);
        }
      }
    );

    IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
  }

  private static int getNavigationPositionFor(HighlightInfo info, Document document) {
    int shift = info.isAfterEndOfLine ? +1 : info.navigationShift;
    int offset = info.highlighter.getStartOffset() + shift;
    return Math.min(offset, document.getTextLength());
  }
}