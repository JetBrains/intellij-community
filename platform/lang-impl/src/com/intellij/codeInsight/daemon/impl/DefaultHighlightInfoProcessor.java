// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DefaultHighlightInfoProcessor extends HighlightInfoProcessor {
  private volatile TextEditorHighlightingPass myCachedShowAutoImportPass; // cache to avoid re-creating it multiple times
  @Override
  public void highlightsInsideVisiblePartAreProduced(@NotNull HighlightingSession session,
                                                     @Nullable Editor editor,
                                                     @NotNull List<? extends HighlightInfo> infos,
                                                     @NotNull TextRange priorityRange,
                                                     @NotNull TextRange restrictRange,
                                                     int groupId) {
    PsiFile psiFile = session.getPsiFile();
    Project project = psiFile.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (document == null) return;
    long modificationStamp = document.getModificationStamp();
    TextRange priorityIntersection = priorityRange.intersection(restrictRange);
    List<? extends HighlightInfo> infoCopy = new ArrayList<>(infos);
    TextEditorHighlightingPass showAutoImportPass = editor == null ? null : getOrCreateShowAutoImportPass(editor, psiFile, session.getProgressIndicator());
    ((HighlightingSessionImpl)session).applyInEDT(() -> {
      if (modificationStamp != document.getModificationStamp()) return;
      if (priorityIntersection != null) {
        MarkupModel markupModel = DocumentMarkupModel.forDocument(document, project, true);

        UpdateHighlightersUtil.setHighlightersInRange(document, priorityIntersection, infoCopy, (MarkupModelEx)markupModel, groupId, session);
      }
      if (editor != null && !editor.isDisposed()) {
        // usability: show auto import popup as soon as possible
        if (!DumbService.isDumb(project)) {
          showAutoImportHints(session.getProgressIndicator(), showAutoImportPass);
        }

        repaintErrorStripeAndIcon(editor, project, psiFile);
      }
    });
  }

  void showAutoImportHints(@NotNull ProgressIndicator progressIndicator, @Nullable TextEditorHighlightingPass showAutoImportPass) {
    ApplicationManager.getApplication().assertIsDispatchThread();
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

  static void repaintErrorStripeAndIcon(@NotNull Editor editor, @NotNull Project project, @Nullable PsiFile file) {
    MarkupModel markup = editor.getMarkupModel();
    if (markup instanceof EditorMarkupModelImpl) {
      ((EditorMarkupModelImpl)markup).repaintTrafficLightIcon();
      ErrorStripeUpdateManager.getInstance(project).repaintErrorStripePanel(editor, file);
    }
  }

  @Override
  public void highlightsOutsideVisiblePartAreProduced(@NotNull HighlightingSession session,
                                                      @Nullable Editor editor,
                                                      @NotNull List<? extends HighlightInfo> infos,
                                                      @NotNull TextRange priorityRange,
                                                      @NotNull TextRange restrictedRange,
                                                      int groupId) {
    PsiFile psiFile = session.getPsiFile();
    Project project = psiFile.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (document == null) return;
    long modificationStamp = document.getModificationStamp();
    ((HighlightingSessionImpl)session).applyInEDT(() -> {
      if (project.isDisposed() || modificationStamp != document.getModificationStamp()) return;

      UpdateHighlightersUtil.setHighlightersOutsideRange(document, infos,
                                                         restrictedRange,
                                                         priorityRange,
                                                         groupId, session);
      if (editor != null) {
        repaintErrorStripeAndIcon(editor, project, psiFile);
      }
    });
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
    DaemonCodeAnalyzerEx.processHighlights(document, project, null, TextRangeScalarUtil.startOffset(range), TextRangeScalarUtil.endOffset(range), existing -> {
      if (existing.getGroup() == Pass.UPDATE_ALL && range == existing.getVisitingTextRange()) {
        if (infos != null) {
          for (HighlightInfo created : infos) {
            if (existing.equalsByActualOffset(created)) return true;
          }
        }
        RangeHighlighterEx highlighter = existing.highlighter;
        if (highlighter != null && UpdateHighlightersUtil.shouldRemoveHighlighter(highlighter, session)) {
          // seems that highlight info 'existing' is going to disappear; remove it earlier
          ((HighlightingSessionImpl)session).queueDisposeHighlighter(existing);
        }
      }
      return true;
    });
  }

  @Override
  public void infoIsAvailable(@NotNull HighlightingSession session,
                              @NotNull HighlightInfo info,
                              @NotNull TextRange priorityRange,
                              @NotNull TextRange restrictedRange,
                              int groupId) {
    ((HighlightingSessionImpl)session).queueHighlightInfo(info, restrictedRange, groupId);
  }

  @Override
  public void progressIsAdvanced(@NotNull HighlightingSession highlightingSession,
                                 @Nullable Editor editor,
                                 double progress) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    PsiFile file = highlightingSession.getPsiFile();
    repaintTrafficIcon(file, editor, progress);
  }

  private final Alarm repaintIconAlarm = new Alarm();
  private void repaintTrafficIcon(@NotNull PsiFile file, @Nullable Editor editor, double progress) {
    if (ApplicationManager.getApplication().isCommandLine()) return;
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (editor != null && (repaintIconAlarm.isEmpty() || progress >= 1)) {
      repaintIconAlarm.addRequest(() -> {
        Project myProject = file.getProject();
        if (!myProject.isDisposed() && !editor.isDisposed()) {
          repaintErrorStripeAndIcon(editor, myProject, file);
        }
      }, 50, null);
    }
  }
}
