// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.annotation;

import com.intellij.codeInspection.GlobalSimpleInspectionTool;
import com.intellij.lang.ExternalAnnotatorsFilter;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by a custom language plugin to process the files in a language by an
 * external annotation tool ("linter"). External annotators are expected to be (relatively) slow and are started
 * after regular annotators have completed their work.</p>
 *
 * <p>Annotators work in three steps:
 * <ol>
 * <li>{@link #collectInformation(PsiFile, Editor, boolean)} is called to collect some data about a file needed for launching a tool</li>
 * <li>collected data is passed to {@link #doAnnotate} which executes a tool and collects highlighting data</li>
 * <li>highlighting data is applied to a file by {@link #apply}</li>
 * </ol>
 * </p>
 *
 * <p>
 * Implement {@link DumbAware} to allow running annotator during indexing.
 * </p>
 *
 * <p>
 * Use {@link ExternalAnnotatorsFilter} to skip running specific annotators for given file.
 * </p>
 *
 * @author ven
 * @see com.intellij.lang.ExternalLanguageAnnotators
 */
public abstract class ExternalAnnotator<InitialInfoType, AnnotationResultType> {
  /**
   * @see ExternalAnnotator#collectInformation(PsiFile, Editor, boolean)
   */
  @Nullable
  public InitialInfoType collectInformation(@NotNull PsiFile file) {
    return null;
  }

  /**
   * Collects initial information needed for launching a tool. This method is called within a read action;
   * non-{@link DumbAware} annotators are skipped during indexing.
   *
   * @param file      a file to annotate
   * @param editor    an editor in which file's document reside
   * @param hasErrors indicates if file has errors detected by preceding analyses
   * @return information to pass to {@link ExternalAnnotator#doAnnotate(InitialInfoType)}, or {@code null} if not applicable
   */
  @Nullable
  public InitialInfoType collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    return hasErrors ? null : collectInformation(file);
  }

  /**
   * Collects full information required for annotation. This method is intended for long-running activities
   * and will be called outside a read action; implementations should either avoid accessing indices and PSI or
   * perform needed checks and locks themselves.
   *
   * @param collectedInfo initial information gathered by {@link ExternalAnnotator#collectInformation}
   * @return annotations to pass to {@link ExternalAnnotator#apply(PsiFile, AnnotationResultType, AnnotationHolder)}
   */
  @Nullable
  public AnnotationResultType doAnnotate(InitialInfoType collectedInfo) {
    return null;
  }

  /**
   * Applies collected annotations to the given annotation holder. This method is called within a read action.
   *
   * @param file             a file to annotate
   * @param annotationResult annotations collected in {@link ExternalAnnotator#doAnnotate(InitialInfoType)}
   * @param holder           a container for receiving annotations
   */
  public void apply(@NotNull PsiFile file, AnnotationResultType annotationResult, @NotNull AnnotationHolder holder) { }

  /**
   * <p>Returns an inspection that should run in batch mode.</p>
   *
   * <p>When inspection with short name is disabled, then annotator won't run in the editor via {@link com.intellij.codeInsight.daemon.impl.ExternalToolPass}.
   * Implementing {@link com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection}
   * and extending {@link com.intellij.codeInspection.LocalInspectionTool} or {@link GlobalSimpleInspectionTool} would
   * provide implementation for a batch tool that would run without read action, according to the {@link #doAnnotate(Object)} documentation.</p>
   */
  public String getPairedBatchInspectionShortName() {
    return null;
  }
}