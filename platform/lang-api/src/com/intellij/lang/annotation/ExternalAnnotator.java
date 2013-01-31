/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * @author ven
 * @see com.intellij.lang.ExternalLanguageAnnotators
 */
public abstract class ExternalAnnotator<InitialInfoType, AnnotationResultType> {
  // Invoked initially in read action
  @Nullable
  public InitialInfoType collectionInformation(@NotNull PsiFile file) {
    return null;
  }
  
  @Nullable
  public InitialInfoType collectInformation(@NotNull PsiFile file, @NotNull Editor editor) {
    return collectionInformation(file);
  }

  // Lengthy annotation goes here
  @Nullable
  public AnnotationResultType doAnnotate(InitialInfoType collectedInfo) {
    return null;
  }

  // Result of annotation is applied in read action
  public void apply(@NotNull PsiFile file, AnnotationResultType annotationResult, @NotNull AnnotationHolder holder) {
  }

  /**
   * Annotates the specified file.
   *
   * @param file   the file to annotate.
   * @param holder the container which receives annotations created by the plugin.
   */
  // todo: adapt existing annotators to a new API
  @Deprecated
  public void annotate(PsiFile file, AnnotationHolder holder) {
  }
}
