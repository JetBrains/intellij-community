package com.intellij.lang.documentation;

import com.intellij.psi.PsiComment;
import org.jetbrains.annotations.Nullable;

/**
 * Defines support for JavaDoc-like documentation stub generation when invoked on "Enter within comment" actions in a custom language.
 * @author Maxim.Mossienko
 * @see com.intellij.lang.Language#getCommenter()
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
   * Generate documentation comment content for given context.
   * @param contextComment context psi comment
   * @return documentation content for given context if any
   */
  @Nullable
  String generateDocumentationContentStub(PsiComment contextComment);
}
