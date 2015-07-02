/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.annotation;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by a custom language plugin to process the files in a language by an
 * external annotation tool. The external annotator is expected to be slow and is started
 * after the regular annotator has completed its work.
 * 
 * During indexing only {@link com.intellij.openapi.project.DumbAware} annotators are executed.
 *
 * @author ven
 * @see com.intellij.lang.ExternalLanguageAnnotators
 */
public abstract class ExternalAnnotator<InitialInfoType, AnnotationResultType> {
  /**
   * Collects initial information required for annotation. Expected to run within read action.
   * See {@link ExternalAnnotator#collectInformation(PsiFile, Editor, boolean)} for details.
   *
   * @param file file to annotate
   * @return see {@link ExternalAnnotator#collectInformation(PsiFile, Editor, boolean)}
   */
  @Nullable
  public InitialInfoType collectInformation(@NotNull PsiFile file) {
    return null;
  }

  /**
   * Collects initial information required for annotation. This method is called within read action during annotation pass.
   * Default implementation returns the result of {@link ExternalAnnotator#collectInformation(PsiFile)}
   * if file has no errors or {@code null} otherwise.
   * @param file      file to annotate
   * @param editor    editor in which file's document reside
   * @param hasErrors indicates if file has errors detected by preceding analyses
   * @return information to pass to {@link ExternalAnnotator#doAnnotate(InitialInfoType)} or {@code null} if annotation should be skipped
   */
  @Nullable
  public InitialInfoType collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    return hasErrors ? null : collectInformation(file);
  }

  /**
   * Collects full information required for annotation. This method is intended for long-running activities
   * and will be called outside read/write actions during annotation pass.
   * @param collectedInfo initial information gathered by {@link ExternalAnnotator#collectInformation(PsiFile, Editor, boolean)}
   * @return annotation result to pass to {@link ExternalAnnotator#apply(PsiFile, AnnotationResultType, AnnotationHolder)}
   */
  @Nullable
  public AnnotationResultType doAnnotate(InitialInfoType collectedInfo) {
    return null;
  }

  /**
   * Applies results of annotation. This method is called within read action during annotation pass.
   * @param file file to annotate
   * @param annotationResult annotation result acquired through {@link ExternalAnnotator#doAnnotate(InitialInfoType)}
   * @param holder container which receives annotations
   */
  public void apply(@NotNull PsiFile file, AnnotationResultType annotationResult, @NotNull AnnotationHolder holder) {
  }
}
