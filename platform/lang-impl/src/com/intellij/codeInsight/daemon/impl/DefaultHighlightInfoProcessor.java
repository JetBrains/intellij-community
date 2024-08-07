// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ClientEditorManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated use {@link BackgroundUpdateHighlightersUtil} instead
 */
@Deprecated
@ApiStatus.Internal
public final class DefaultHighlightInfoProcessor extends HighlightInfoProcessor {
  private volatile TextEditorHighlightingPass myCachedShowAutoImportPass; // cache to avoid re-creating it multiple times
  @Override
  public void highlightsInsideVisiblePartAreProduced(@NotNull HighlightingSession session,
                                                     @Nullable Editor editor,
                                                     @NotNull List<? extends HighlightInfo> infos,
                                                     @NotNull TextRange priorityRange,
                                                     @NotNull TextRange restrictRange,
                                                     int groupId) {
    PsiFile psiFile = session.getPsiFile();
    Project project = session.getProject();
    Document document = session.getDocument();
    long modificationStamp = document.getModificationStamp();
    TextRange priorityIntersection = priorityRange.intersection(restrictRange);
    if (priorityIntersection != null) {
      MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
      BackgroundUpdateHighlightersUtil.setHighlightersInRange(priorityIntersection, new ArrayList<HighlightInfo>(infos), markupModel, groupId, session);
    }
    TextEditorHighlightingPass showAutoImportPass = editor == null ? null : getOrCreateShowAutoImportPass(editor, psiFile, session.getProgressIndicator());
    ApplicationManager.getApplication().invokeLater(() -> {
      if (editor != null && !editor.isDisposed() && modificationStamp == document.getModificationStamp()) {
        // usability: show auto import popup as soon as possible
        if (!DumbService.isDumb(project)) {
          showAutoImportHints(session.getProgressIndicator(), showAutoImportPass);
        }

        DaemonCodeAnalyzerImpl.repaintErrorStripeAndIcon(editor, project, psiFile);
      }
    });
  }

  private static void showAutoImportHints(@NotNull ProgressIndicator progressIndicator,
                                          @Nullable TextEditorHighlightingPass showAutoImportPass) {
    ThreadingAssertions.assertEventDispatchThread();
    if (showAutoImportPass != null) {
      ProgressManager.getInstance().executeProcessUnderProgress(() -> showAutoImportPass.doApplyInformationToEditor(), progressIndicator);
    }
  }

  private TextEditorHighlightingPass getOrCreateShowAutoImportPass(@NotNull Editor editor,
                                                                   @NotNull PsiFile psiFile,
                                                                   @NotNull ProgressIndicator progressIndicator) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    TextEditorHighlightingPass pass = myCachedShowAutoImportPass;
    if (pass == null) {
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        ShowAutoImportPassFactory siFactory = TextEditorHighlightingPassRegistrarImpl.EP_NAME.findExtensionOrFail(ShowAutoImportPassFactory.class);
        try (AccessToken ignored = ClientId.withClientId(ClientEditorManager.getClientId(editor));
             AccessToken ignored2 = SlowOperations.knownIssue("IDEA-305557, EA-599727")) {
          TextEditorHighlightingPass highlightingPass = siFactory.createHighlightingPass(psiFile, editor);
          if (highlightingPass != null) {
            myCachedShowAutoImportPass = highlightingPass;
          }
        }
      }, progressIndicator);
    }
    return myCachedShowAutoImportPass;
  }

  @Override
  public void highlightsOutsideVisiblePartAreProduced(@NotNull HighlightingSession session,
                                                      @Nullable Editor editor,
                                                      @NotNull List<? extends HighlightInfo> infos,
                                                      @NotNull TextRange priorityRange,
                                                      @NotNull TextRange restrictedRange,
                                                      int groupId) {
    BackgroundUpdateHighlightersUtil.setHighlightersOutsideRange(infos, restrictedRange, priorityRange, groupId, session);
    if (editor != null) {
      PsiFile psiFile = session.getPsiFile();
      Project project = session.getProject();
      Document document = session.getDocument();
      long modificationStamp = document.getModificationStamp();
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!project.isDisposed() && !editor.isDisposed() && modificationStamp != document.getModificationStamp()) {
          DaemonCodeAnalyzerImpl.repaintErrorStripeAndIcon(editor, project, psiFile);
        }
      });
    }
  }

  @Override
  public void allHighlightsForRangeAreProduced(@NotNull HighlightingSession session,
                                               long elementRange,
                                               @Nullable List<? extends HighlightInfo> infos) {
    killAbandonedHighlightsUnder(session.getProject(), session.getDocument(), elementRange, infos, session);
  }

  private static void killAbandonedHighlightsUnder(@NotNull Project project,
                                                   @NotNull Document document,
                                                   long range,
                                                   @Nullable List<? extends HighlightInfo> infos,
                                                   @NotNull HighlightingSession session) {
    List<RangeHighlighterEx> toRemove = new ArrayList<>();
    DaemonCodeAnalyzerEx.processHighlights(document, project, null, TextRangeScalarUtil.startOffset(range), TextRangeScalarUtil.endOffset(range), existing -> {
      if (existing.getGroup() == Pass.UPDATE_ALL && !existing.isFromAnnotator() && TextRangeScalarUtil.startOffset(range) == existing.getVisitingTextRange().getStartOffset() && TextRangeScalarUtil.endOffset(range) == existing.getVisitingTextRange().getEndOffset()) {
        if (infos != null) {
          for (HighlightInfo created : infos) {
            if (existing.equalsByActualOffset(created)) return true;
          }
        }
        RangeHighlighterEx highlighter = existing.getHighlighter();
        if (highlighter != null && UpdateHighlightersUtil.shouldRemoveHighlighter(highlighter, session)) {
          // it seems that highlight info 'existing' is going to disappear; remove it earlier
          toRemove.add(highlighter);
        }
      }
      return true;
    });
    for (RangeHighlighterEx highlighter : toRemove) {
      highlighter.dispose();
    }
  }

  @Override
  public void infoIsAvailable(@NotNull HighlightingSession session,
                              @NotNull HighlightInfo info,
                              @NotNull TextRange priorityRange,
                              @NotNull TextRange restrictedRange,
                              int groupId) {
    ((HighlightingSessionImpl)session).addInfoIncrementally(info, restrictedRange, groupId);
  }

  @Override
  public void progressIsAdvanced(@NotNull HighlightingSession highlightingSession,
                                 @Nullable Editor editor,
                                 double progress) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    DaemonCodeAnalyzerEx.getInstanceEx(highlightingSession.getProject()).progressIsAdvanced(highlightingSession, editor, progress);
  }
}
