// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This instance is created at the highlighting start and discarded when the highlighting is finished.
 * Intended to store highlighting-related data to make it accessible in the background, during the highlighting process,
 * e.g., inside {@link Annotator#annotate(PsiElement, AnnotationHolder)} or {@link com.intellij.codeInspection.LocalInspectionTool#checkFile(PsiFile, InspectionManager, boolean)} methods.
 */
@ApiStatus.NonExtendable
public interface HighlightingSession {
  @NotNull
  Project getProject();

  @ApiStatus.Internal
  HighlightSeverity getMinimumSeverity();

  @NotNull
  PsiFile getPsiFile();

  @NotNull
  Document getDocument();

  @NotNull
  ProgressIndicator getProgressIndicator();

  EditorColorsScheme getColorsScheme();

  @NotNull
  ProperTextRange getVisibleRange();

  @ApiStatus.Internal
  boolean isEssentialHighlightingOnly();

  /**
   * @return true if the daemon-cancel event (see {@link DaemonCodeAnalyzer.DaemonListener#daemonCancelEventOccurred(String)}) happened
   * since the {@link HighlightingSession} creation.
   */
  boolean isCanceled();

  @ApiStatus.Experimental
  @NotNull CodeInsightContext getCodeInsightContext();

  @ApiStatus.Internal
  @RequiresEdt
  void applyFileLevelHighlightsRequests();

  @ApiStatus.Internal
  @Deprecated
  void updateFileLevelHighlights(@NotNull List<? extends HighlightInfo> fileLevelHighlights,
                                        @NotNull List<? extends RangeHighlighter> reusedHighlighters, int group,
                                        boolean cleanOldHighlights,
                                        @NotNull PsiFile psiFile);

  // removes the old HighlightInfo and adds the new one atomically, to avoid flicker
  @ApiStatus.Internal
  void replaceFileLevelHighlight(@NotNull HighlightInfo oldFileLevelInfo,
                                 @NotNull HighlightInfo newFileLevelInfo,
                                 @Nullable RangeHighlighterEx toReuse);

  @ApiStatus.Internal
  void removeFileLevelHighlight(@NotNull HighlightInfo fileLevelHighlightInfo);

  @ApiStatus.Internal
  void addFileLevelHighlight(@NotNull HighlightInfo fileLevelHighlightInfo, @Nullable RangeHighlighterEx toReuse);
}