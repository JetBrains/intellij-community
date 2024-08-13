// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DaemonCodeAnalyzerEx extends DaemonCodeAnalyzer {
  private static final Logger LOG = Logger.getInstance(DaemonCodeAnalyzerEx.class);

  public static DaemonCodeAnalyzerEx getInstanceEx(Project project) {
    return (DaemonCodeAnalyzerEx)getInstance(project);
  }

  public static boolean processHighlights(@NotNull Document document,
                                          @NotNull Project project,
                                          @Nullable("null means all") HighlightSeverity minSeverity,
                                          int startOffset,
                                          int endOffset,
                                          @NotNull Processor<? super HighlightInfo> processor) {
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    return processHighlights(model, project, minSeverity, startOffset, endOffset, processor);
  }

  public static boolean processHighlights(@NotNull MarkupModelEx model,
                                          @NotNull Project project,
                                          @Nullable("null means all") HighlightSeverity minSeverity,
                                          int startOffset,
                                          int endOffset,
                                          @NotNull Processor<? super HighlightInfo> processor) {
    LOG.assertTrue(ApplicationManager.getApplication().isReadAccessAllowed());
    SeverityRegistrar severityRegistrar = SeverityRegistrar.getSeverityRegistrar(project);
    return model.processRangeHighlightersOverlappingWith(startOffset, endOffset, marker -> {
      ProgressManager.checkCanceled();
      HighlightInfo info = HighlightInfo.fromRangeHighlighter(marker);
      if (info == null) return true;
      return minSeverity != null && severityRegistrar.compare(info.getSeverity(), minSeverity) < 0
             || info.getHighlighter() == null
             || processor.process(info);
    });
  }

  static boolean processHighlightsOverlappingOutside(@NotNull Document document,
                                                     @NotNull Project project,
                                                     int startOffset,
                                                     int endOffset,
                                                     @NotNull Processor<? super HighlightInfo> processor) {
    LOG.assertTrue(ApplicationManager.getApplication().isReadAccessAllowed());
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    return model.processRangeHighlightersOutside(startOffset, endOffset, marker -> {
      HighlightInfo info = HighlightInfo.fromRangeHighlighter(marker);
      return info == null || info.getHighlighter() == null || processor.process(info);
    });
  }

  public abstract boolean hasVisibleLightBulbOrPopup();

  public abstract @NotNull List<HighlightInfo> runMainPasses(@NotNull PsiFile psiFile,
                                                             @NotNull Document document,
                                                             @NotNull ProgressIndicator progress);

  public abstract boolean isErrorAnalyzingFinished(@NotNull PsiFile file);

  public abstract @NotNull FileStatusMap getFileStatusMap();

  public abstract void cleanFileLevelHighlights(int group, @NotNull PsiFile psiFile);

  public abstract boolean hasFileLevelHighlights(int group, @NotNull PsiFile psiFile);

  public abstract void addFileLevelHighlight(int group, @NotNull HighlightInfo info, @NotNull PsiFile psiFile, @Nullable RangeHighlighter toReuse);
  @ApiStatus.Internal
  public abstract void replaceFileLevelHighlight(@NotNull HighlightInfo oldInfo, @NotNull HighlightInfo newInfo, @NotNull PsiFile psiFile, @Nullable RangeHighlighter toReuse);

  abstract void removeFileLevelHighlight(@NotNull PsiFile psiFile, @NotNull HighlightInfo info);

  public void markDocumentDirty(@NotNull Document document, @NotNull Object reason) {
    getFileStatusMap().markFileScopeDirty(document, new TextRange(0, document.getTextLength()), document.getTextLength(), reason);
  }

  public static boolean isHighlightingCompleted(@NotNull FileEditor fileEditor, @NotNull Project project) {
    return fileEditor instanceof TextEditor textEditor
           && getInstanceEx(project).getFileStatusMap().allDirtyScopesAreNull(textEditor.getEditor().getDocument());
  }

  abstract boolean cutOperationJustHappened();

  abstract boolean isEscapeJustPressed();

  abstract protected void progressIsAdvanced(@NotNull HighlightingSession session, Editor editor, double progress);
  static final int ANY_GROUP = -409423948;
}
