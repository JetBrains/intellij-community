// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This instance is created at the highlighting start and discarded when the highlighting is finished.
 * Intended to store highlighting-related data to make it accessible in the background, during the highlighting process,
 * e.g., inside {@link Annotator#annotate(PsiElement, AnnotationHolder)} or {@link com.intellij.codeInspection.LocalInspectionTool#checkFile(PsiFile, InspectionManager, boolean)} methods.
 */
@ApiStatus.NonExtendable
public interface HighlightingSession {
  @NotNull
  Project getProject();

  @NotNull
  PsiFile getPsiFile();

  @NotNull
  Document getDocument();

  @NotNull
  ProgressIndicator getProgressIndicator();

  EditorColorsScheme getColorsScheme();

  @NotNull
  ProperTextRange getVisibleRange();

  boolean isEssentialHighlightingOnly();

  /**
   * @return true if the daemon-cancel event (see {@link DaemonCodeAnalyzer.DaemonListener#daemonCancelEventOccurred(String)}) happened
   * since the {@link HighlightingSession} creation.
   */
  boolean isCanceled();

  @ApiStatus.Experimental
  @NotNull CodeInsightContext getCodeInsightContext();
}