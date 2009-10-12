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

package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

import java.util.List;
import java.util.Set;

public interface Unwrapper {
  boolean isApplicableTo(PsiElement e);

  void collectElementsToIgnore(PsiElement element, Set<PsiElement> result);

  String getDescription(PsiElement e);

  /**
   * @param toExtract the elements that will be extracted
   * @return TextRange the whole affected code structure (the code that will be removed)
   */
  PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract);

  List<PsiElement> unwrap(Editor editor, PsiElement element) throws IncorrectOperationException;
}
