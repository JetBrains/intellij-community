/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl;

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
}