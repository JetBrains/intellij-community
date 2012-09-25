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

package com.intellij.lang.documentation;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines support for JavaDoc-like documentation stub generation when invoked on "Enter within comment" actions in a custom language.
 * @author Maxim.Mossienko
 * @see com.intellij.lang.LanguageDocumentation
 */
public interface CodeDocumentationProvider extends DocumentationProvider {
  /**
   * Finds primary documentation comment within given context.
   * @param contextElement candidate psi comment
   * @return contextElement if no other applicable
   */
  @Nullable
  PsiComment findExistingDocComment(PsiComment contextElement);

  /**
   * Examines PSI hierarchy identified by the given 'start' element trying to find element which can be documented
   * and it's doc comment (if any).
   * <p/>
   * Example:
   * <pre>
   *   int test() {
   *     return [caret] 1;
   *   }
   * </pre>
   * PSI element at the caret (return element) is an entry point. This method is expected to return PSI method element
   * and <code>'null'</code> as the existing doc comment then.
   * 
   * @param startPoint  start traversal point
   * @return            comment anchor which is a given element or its anchor if the one is found and its doc comment (if existing).
   *                    This method may return <code>'null'</code> as an indication that no doc comment anchor and existing comment
   *                    is available;
   *                    returned pair must have non-null PSI element and nullable existing comment references then
   */
  @Nullable
  Pair<PsiElement, PsiComment> parseContext(@NotNull PsiElement startPoint);

  /**
   * Generate documentation comment content for given context.
   * @param contextComment context psi comment
   * @return documentation content for given context if any
   */
  @Nullable
  String generateDocumentationContentStub(PsiComment contextComment);
}
