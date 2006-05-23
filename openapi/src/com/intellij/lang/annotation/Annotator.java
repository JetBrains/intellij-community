/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import java.util.Collections;
import java.util.List;

/**
 * Implemented by a custom language plugin to add annotations to files in the language.
 *
 * @author max
 * @see com.intellij.lang.Language#getAnnotator()
 */
public interface Annotator {
  List<Annotator> EMPTY_LIST = Collections.emptyList();

  /**
   * Annotates the specified PSI element.
   *
   * @param psiElement the element to annotate.
   * @param holder     the container which receives annotations created by the plugin.
   */
  void annotate(PsiElement psiElement, AnnotationHolder holder);
}
