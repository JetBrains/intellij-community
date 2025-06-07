// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContextHighlightingUtil;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.codeInsight.multiverse.EditorContextManager;
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
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DaemonCodeAnalyzerEx extends DaemonCodeAnalyzer {
  private static final Logger LOG = Logger.getInstance(DaemonCodeAnalyzerEx.class);

  public static DaemonCodeAnalyzerEx getInstanceEx(Project project) {
    return (DaemonCodeAnalyzerEx)getInstance(project);
  }

  @ApiStatus.Internal
  public abstract void restart(@NotNull Object reason);

  // todo IJPL-339 mark deprecated
  public static boolean processHighlights(@NotNull Document document,
                                          @NotNull Project project,
                                          @Nullable("null means all") HighlightSeverity minSeverity,
                                          int startOffset,
                                          int endOffset,
                                          @NotNull Processor<? super HighlightInfo> processor) {
    return processHighlights(document, project, minSeverity, startOffset, endOffset, CodeInsightContexts.anyContext(), processor);
  }

  // todo IJPL-339 mark experimental
  @ApiStatus.Internal
  public static boolean processHighlights(@NotNull Document document,
                                          @NotNull Project project,
                                          @Nullable("null means all") HighlightSeverity minSeverity,
                                          int startOffset,
                                          int endOffset,
                                          @NotNull CodeInsightContext context,
                                          @NotNull Processor<? super HighlightInfo> processor) {
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    return processHighlights(model, project, minSeverity, startOffset, endOffset, context, processor);
  }

  // todo IJPL-339 mark experimental
  @ApiStatus.Internal
  public static boolean processHighlights(@NotNull MarkupModelEx model,
                                          @NotNull Project project,
                                          @Nullable("null means all") HighlightSeverity minSeverity,
                                          int startOffset,
                                          int endOffset,
                                          @NotNull CodeInsightContext context,
                                          @NotNull Processor<? super HighlightInfo> processor) {
    LOG.assertTrue(ApplicationManager.getApplication().isReadAccessAllowed());
    SeverityRegistrar severityRegistrar = SeverityRegistrar.getSeverityRegistrar(project);
    return model.processRangeHighlightersOverlappingWith(startOffset, endOffset, marker -> {
      ProgressManager.checkCanceled();
      HighlightInfo info = HighlightInfo.fromRangeHighlighter(marker);
      if (info == null) return true;
      return minSeverity != null && severityRegistrar.compare(info.getSeverity(), minSeverity) < 0
             || info.getHighlighter() == null
             || !CodeInsightContextHighlightingUtil.acceptRangeHighlighter(context, marker)
             || processor.process(info);
    });
  }

  // todo IJPL-339 mark deprecated
  public static boolean processHighlights(@NotNull MarkupModelEx model,
                                          @NotNull Project project,
                                          @Nullable("null means all") HighlightSeverity minSeverity,
                                          int startOffset,
                                          int endOffset,
                                          @NotNull Processor<? super HighlightInfo> processor) {
    return processHighlights(model, project, minSeverity, startOffset, endOffset, CodeInsightContexts.anyContext(), processor);
  }

  static boolean processHighlightsOverlappingOutside(MarkupModelEx model,
                                                     int startOffset,
                                                     int endOffset,
                                                     @NotNull CodeInsightContext context,
                                                     @NotNull Processor<? super HighlightInfo> processor) {
    LOG.assertTrue(ApplicationManager.getApplication().isReadAccessAllowed());
    return model.processRangeHighlightersOutside(startOffset, endOffset, marker -> {
      HighlightInfo info = HighlightInfo.fromRangeHighlighter(marker);
      return info == null ||
             info.getHighlighter() == null ||
             !CodeInsightContextHighlightingUtil.acceptRangeHighlighter(context, marker) ||
             processor.process(info);
    });
  }

  public abstract boolean hasVisibleLightBulbOrPopup();

  @ApiStatus.Internal
  public abstract @NotNull List<HighlightInfo> runMainPasses(@NotNull PsiFile psiFile,
                                                             @NotNull Document document,
                                                             @NotNull ProgressIndicator progress);

  public abstract boolean isErrorAnalyzingFinished(@NotNull PsiFile psiFile);

  public abstract @NotNull FileStatusMap getFileStatusMap();

  /**
   * Do not use because manual management of highlights is dangerous and may lead to unexpected flicking/disappearing/stuck highlighters.
   * Instead, generate file-level infos in your inspection/annotator, and they will be removed automatically when outdated
   */
  @ApiStatus.Internal
  public abstract void cleanFileLevelHighlights(int group, @NotNull PsiFile psiFile);

  @ApiStatus.Internal
  public abstract boolean hasFileLevelHighlights(int group, @NotNull PsiFile psiFile);

  /**
   * Do not use because manual management of highlights is dangerous and may lead to unexpected flicking/disappearing/stuck highlighters.
   * Instead, generate file-level infos in your inspection/annotator, and they will be removed automatically when outdated
   */
  @ApiStatus.Internal
  public abstract void addFileLevelHighlight(int group, @NotNull HighlightInfo info, @NotNull PsiFile psiFile, @Nullable RangeHighlighter toReuse);
  @ApiStatus.Internal
  public abstract void replaceFileLevelHighlight(@NotNull HighlightInfo oldInfo, @NotNull HighlightInfo newInfo, @NotNull PsiFile psiFile, @Nullable RangeHighlighter toReuse);
  /**
   * Do not use because manual management of highlights is dangerous and may lead to unexpected flicking/disappearing/stuck highlighters.
   * Instead, generate file-level infos in your inspection/annotator, and they will be removed automatically when outdated
   */
  @ApiStatus.Internal
  public abstract void removeFileLevelHighlight(@NotNull PsiFile psiFile, @NotNull HighlightInfo info);

  public void markDocumentDirty(@NotNull Document document, @NotNull Object reason) {
    getFileStatusMap().markWholeFileScopeDirty(document, reason);
  }

  public static boolean isHighlightingCompleted(@NotNull FileEditor fileEditor, @NotNull Project project) {
    if (!(fileEditor instanceof TextEditor textEditor)) {
      return false;
    }

    Document document = textEditor.getEditor().getDocument();
    CodeInsightContext context = EditorContextManager.getEditorContext(textEditor.getEditor(), project);
    return getInstanceEx(project).getFileStatusMap().allDirtyScopesAreNull(document, context);
  }

  @ApiStatus.Internal
  public abstract boolean cutOperationJustHappened();

  @ApiStatus.Internal
  public abstract boolean isEscapeJustPressed();

  protected abstract void progressIsAdvanced(@NotNull HighlightingSession session, Editor editor, double progress);
  @ApiStatus.Internal
  protected static final int ANY_GROUP = -409423948;
  @ApiStatus.Internal
  protected static final int FILE_LEVEL_FAKE_LAYER = -4094; // the layer the (fake) RangeHighlighter is created for file-level HighlightInfo in
  @ApiStatus.Internal
  @RequiresBackgroundThread
  public void rescheduleShowIntentionsPass(@NotNull PsiFile psiFile, @NotNull HighlightInfo.Builder builder) {
    rescheduleShowIntentionsPass(psiFile, ((HighlightInfoB)builder).getRangeSoFar());
  }
  @ApiStatus.Internal
  @RequiresBackgroundThread
  protected abstract void rescheduleShowIntentionsPass(@NotNull PsiFile psiFile, @NotNull TextRange visibleRange);
}
