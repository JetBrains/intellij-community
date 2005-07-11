/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
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
