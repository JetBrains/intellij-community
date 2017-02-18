/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Implemented by a custom language plugin to add annotations to files in the language.
 *
 * DO NOT STORE any state inside annotator.
 * If you absolutely must, clear the state upon exit from the {@link #annotate(PsiElement, AnnotationHolder)} method.
 *
 * @author max
 * @see com.intellij.lang.LanguageAnnotators
 */
public interface Annotator {
  /**
   * Annotates the specified PSI element.
   * It is guaranteed to be executed in non-reentrant fashion.
   * I.e there will be no call of this method for this instance before previous call get completed.
   * Multiple instances of the annotator might exist simultaneously, though.
   *
   * @param element to annotate.
   * @param holder  the container which receives annotations created by the plugin.
   */
  void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder);
}
