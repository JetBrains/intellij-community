package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.ex.ErrorStripeAdapter;
import com.intellij.openapi.editor.ex.ErrorStripeEvent;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;

public class ErrorStripeHandler extends ErrorStripeAdapter {
  private final Project myProject;

  public ErrorStripeHandler(Project project) {
    myProject = project;
  }

  public void errorMarkerClicked(ErrorStripeEvent e) {
    RangeHighlighter highlighter = e.getHighlighter();
    if (!highlighter.isValid()) return;
    HighlightInfo info = findInfo(highlighter);
    if (info != null) {
      GotoNextErrorHandler.navigateToError(myProject, e.getEditor(), info);
    }
  }

  private HighlightInfo findInfo(RangeHighlighter highlighter) {
    HighlightInfo[] highlights = DaemonCodeAnalyzerImpl.getHighlights(highlighter.getDocument(), myProject);
    if (highlights == null) return null;
    for (HighlightInfo info : highlights) {
      if (info.highlighter == highlighter) {
        return info;
      }
    }
    return null;
  }
}
