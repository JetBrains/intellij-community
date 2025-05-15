// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.analysis.problemsView.toolWindow.ProblemsView;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.EditorContextManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class GotoNextErrorHandler implements CodeInsightActionHandler {
  private final boolean myGoForward;
  private final HighlightSeverity mySeverity;

  public GotoNextErrorHandler(boolean goForward) {
    this (goForward, null);
  }

  public GotoNextErrorHandler(boolean goForward, @Nullable HighlightSeverity severity) {
    myGoForward = goForward;
    mySeverity = severity;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    int caretOffset = editor.getCaretModel().getOffset();
    gotoNextError(project, editor, psiFile, caretOffset);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private void gotoNextError(Project project, Editor editor, PsiFile psiFile, int caretOffset) {
    SeverityRegistrar severityRegistrar = SeverityRegistrar.getSeverityRegistrar(project);
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    int maxSeverity = settings.isNextErrorActionGoesToErrorsFirst() ? severityRegistrar.getSeveritiesCount() - 1
                                                                    : SeverityRegistrar.SHOWN_SEVERITIES_OFFSET;

    EditorContextManager editorContextManager = EditorContextManager.getInstance(project);
    CodeInsightContext context = editorContextManager.getEditorContexts(editor).getMainContext();
    for (int idx = maxSeverity; idx >= SeverityRegistrar.SHOWN_SEVERITIES_OFFSET; idx--) {
      HighlightSeverity minSeverity = severityRegistrar.getSeverityByIndex(idx);
      if (minSeverity == null) continue;
      HighlightInfo infoToGo = findInfo(project, editor, caretOffset, minSeverity);
      if (infoToGo != null) {
        navigateToError(project, editor, infoToGo, () -> {
          if (Registry.is("error.navigation.show.tooltip")) {
            // When there are multiple warnings at the same offset, this will return the HighlightInfo
            // containing all of them, not just the first one as found by findInfo()
            HighlightInfo fullInfo = ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project))
              .findHighlightByOffset(editor.getDocument(), editor.getCaretModel().getOffset(), false, context);
            HighlightInfo info = fullInfo != null ? fullInfo : infoToGo;
            EditorMouseHoverPopupManager.getInstance().showInfoTooltip(editor, info, editor.getCaretModel().getOffset(), false, true, false, true);
          }
        });
        return;
      }
    }
    showMessageWhenNoHighlights(project, psiFile, editor, caretOffset);
  }

  private HighlightInfo findInfo(@NotNull Project project, @NotNull Editor editor, int caretOffset, @NotNull HighlightSeverity minSeverity) {
    Document document = editor.getDocument();
    HighlightInfo[][] infoToGo = new HighlightInfo[2][2]; //HighlightInfo[luck-noluck][skip-noskip]
    CodeInsightContext context = EditorContextManager.getEditorContext(editor, project);
    int caretOffsetIfNoLuck = myGoForward ? -1 : document.getTextLength();
    DaemonCodeAnalyzerEx.processHighlights(document, project, minSeverity, 0, document.getTextLength(), context, info -> {
      if (mySeverity != null && info.getSeverity() != mySeverity) return true;
      int startOffset = getNavigationPositionFor(info, document);
      if (SeverityRegistrar.isGotoBySeverityEnabled(info.getSeverity())) {
        infoToGo[0][0] = getBetterInfoThan(infoToGo[0][0], caretOffset, startOffset, info);
        infoToGo[1][0] = getBetterInfoThan(infoToGo[1][0], caretOffsetIfNoLuck, startOffset, info);
      }
      infoToGo[0][1] = getBetterInfoThan(infoToGo[0][1], caretOffset, startOffset, info);
      infoToGo[1][1] = getBetterInfoThan(infoToGo[1][1], caretOffsetIfNoLuck, startOffset, info);
      return true;
    });
    if (infoToGo[0][0] == null) infoToGo[0][0] = infoToGo[1][0];
    if (infoToGo[0][1] == null) infoToGo[0][1] = infoToGo[1][1];
    if (infoToGo[0][0] == null) infoToGo[0][0] = infoToGo[0][1];
    return infoToGo[0][0];
  }

  private HighlightInfo getBetterInfoThan(HighlightInfo infoToGo, int caretOffset, int startOffset, HighlightInfo info) {
    if (isBetterThan(infoToGo, caretOffset, startOffset)) {
      infoToGo = info;
    }
    return infoToGo;
  }

  private boolean isBetterThan(HighlightInfo oldInfo, int caretOffset, int newOffset) {
    if (oldInfo == null) return true;
    int oldOffset = getNavigationPositionFor(oldInfo, oldInfo.getHighlighter().getDocument());
    if (myGoForward) {
      return caretOffset < oldOffset != caretOffset < newOffset ? caretOffset < newOffset : newOffset < oldOffset;
    }
    else {
      return caretOffset <= oldOffset != caretOffset <= newOffset ? caretOffset > newOffset : newOffset > oldOffset;
    }
  }

  private void showMessageWhenNoHighlights(Project project, PsiFile psiFile, Editor editor, int caretOffset) {
    DaemonCodeAnalyzerImpl codeHighlighter = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    if (codeHighlighter.isErrorAnalyzingFinished(psiFile)) {
      hintManager.showInformationHint(editor, InspectionsBundle.message("no.errors.found.in.this.file"));
      return;
    }

    JComponent component = HintUtil.createInformationLabel(InspectionsBundle.message("error.analysis.is.in.progress"), null, null, null);
    AccessibleContextUtil.setName(component, IdeBundle.message("information.hint.accessible.context.name"));
    LightweightHint hint = new LightweightHint(component);
    Point p = hintManager.getHintPosition(hint, editor, HintManager.ABOVE);

    Disposable hintDisposable = Disposer.newDisposable("GotoNextErrorHandler.showMessageWhenNoHighlights");
    Disposer.register(project, hintDisposable);
    hint.addHintListener((eventObject) -> {
      Disposer.dispose(hintDisposable);
    });

    MessageBusConnection busConnection = project.getMessageBus().connect(hintDisposable);
    busConnection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
      @Override
      public void daemonFinished() {
        hint.hide();
        gotoNextError(project, editor, psiFile, caretOffset);
      }
    });

    hintManager.showEditorHint(hint, editor, p,
                               HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING,
                               0, false, HintManager.ABOVE);
  }

  static void navigateToError(@NotNull Project project, @NotNull Editor editor, @NotNull HighlightInfo info, @Nullable Runnable postNavigateRunnable) {
    int oldOffset = editor.getCaretModel().getOffset();

    int offset = getNavigationPositionFor(info, editor.getDocument());
    int endOffset = info.getActualEndOffset();

    ScrollingModel scrollingModel = editor.getScrollingModel();
    if (offset != oldOffset) {
      ScrollType scrollType = offset > oldOffset ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
      editor.getSelectionModel().removeSelection();
      editor.getCaretModel().removeSecondaryCarets();
      editor.getCaretModel().moveToOffset(offset);
      scrollingModel.scrollToCaret(scrollType);
      FoldRegion regionAtOffset = editor.getFoldingModel().getCollapsedRegionAtOffset(offset);
      if (regionAtOffset != null) editor.getFoldingModel().runBatchFoldingOperation(() -> regionAtOffset.setExpanded(true));
    }

    scrollingModel.runActionOnScrollingFinished(
      () -> {
        int maxOffset = editor.getDocument().getTextLength() - 1;
        if (maxOffset == -1) return;
        scrollingModel.scrollTo(editor.offsetToLogicalPosition(Math.min(maxOffset, endOffset)), ScrollType.MAKE_VISIBLE);
        scrollingModel.scrollTo(editor.offsetToLogicalPosition(Math.min(maxOffset, offset)), ScrollType.MAKE_VISIBLE);

        if (postNavigateRunnable != null) {
          postNavigateRunnable.run();
        }
      }
    );

    IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
    RangeHighlighterEx highlighter = info.getHighlighter();
    if (highlighter != null) ProblemsView.selectHighlighterIfVisible(project, highlighter);
  }

  private static int getNavigationPositionFor(HighlightInfo info, Document document) {
    int start = info.getActualStartOffset();
    if (start >= document.getTextLength()) return document.getTextLength();
    char c = document.getCharsSequence().charAt(start);
    int shift = info.isAfterEndOfLine() && c != '\n' ? 1 : info.navigationShift;

    int offset = info.getActualStartOffset() + shift;
    return Math.min(offset, document.getTextLength());
  }
}
