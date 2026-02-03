// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.documentation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
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
   * and {@code 'null'} as the existing doc comment then.
   * 
   * @param startPoint  start traversal point
   * @return            comment anchor which is a given element or its anchor if the one is found and its doc comment (if existing).
   *                    This method may return {@code 'null'} as an indication that no doc comment anchor and existing comment
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

  /**
   * Works like {@link #generateDocumentationContentStub(PsiComment)}, but inserts generated content into the document.
   * @param contextComment context psi comment
   * @param document document to insert generated content into
   * @param offset offset in the document to insert generated content at
   * @return true if documentation content was generated and inserted into the document, false otherwise
   */
  @ApiStatus.Internal
  default boolean insertDocumentationContentStub(@NotNull PsiComment contextComment, @NotNull Document document, int offset) {
    return false;
  }
}
