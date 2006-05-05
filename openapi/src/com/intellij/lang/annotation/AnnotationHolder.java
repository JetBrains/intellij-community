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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Allows a custom language plugin to define annotations for files in that language.
 *
 * @author max
 * @see Annotator#annotate(com.intellij.psi.PsiElement, AnnotationHolder)
 */

public interface AnnotationHolder {
  /**
   * Creates an error annotation with the specified message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the error message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createErrorAnnotation(PsiElement elt, @Nullable String message);

  /**
   * Creates an error annotation with the specified message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the error message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createErrorAnnotation(ASTNode node, @Nullable String message);

  /**
   * Creates an error annotation with the specified message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the error message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createErrorAnnotation(TextRange range, @Nullable String message);

  /**
   * Creates a warning annotation with the specified message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the warning message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createWarningAnnotation(PsiElement elt, @Nullable String message);

  /**
   * Creates a warning annotation with the specified message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the warning message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createWarningAnnotation(ASTNode node, @Nullable String message);

  /**
   * Creates a warning annotation with the specified message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the warning message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createWarningAnnotation(TextRange range, @Nullable String message);

  /**
    * Creates an info annotation with the specified message over the specified PSI element.
    *
    * @param elt     the element over which the annotation is created.
    * @param message the info message.
    * @return the annotation (which can be modified to set additional annotation parameters)
    */
   Annotation createInformationAnnotation(PsiElement elt, @Nullable String message);

   /**
    * Creates an info annotation with the specified message over the specified AST node.
    *
    * @param node    the node over which the annotation is created.
    * @param message the info message.
    * @return the annotation (which can be modified to set additional annotation parameters)
    */
   Annotation createInformationAnnotation(ASTNode node, @Nullable String message);

   /**
    * Creates an info annotation with the specified message over the specified text range.
    *
    * @param range   the text range over which the annotation is created.
    * @param message the info message.
    * @return the annotation (which can be modified to set additional annotation parameters)
    */
   Annotation createInformationAnnotation(TextRange range, String message);


  /**
   * Creates an information annotation with the specified message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createInfoAnnotation(PsiElement elt, @Nullable String message);

  /**
   * Creates an information annotation with the specified message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createInfoAnnotation(ASTNode node, @Nullable String message);

  /**
   * Creates an information annotation with the specified message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createInfoAnnotation(TextRange range, String message);

}
