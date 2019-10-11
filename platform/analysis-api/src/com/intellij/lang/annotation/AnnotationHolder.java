// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.annotation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows a custom language plugin to define annotations for files in that language.
 *
 * @author max
 * @see Annotator#annotate(PsiElement, AnnotationHolder)
 */
@ApiStatus.NonExtendable
public interface AnnotationHolder {
  /**
   * Creates an error annotation with the specified message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the error message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createErrorAnnotation(@NotNull PsiElement elt, @Nullable String message);

  /**
   * Creates an error annotation with the specified message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the error message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createErrorAnnotation(@NotNull ASTNode node, @Nullable String message);

  /**
   * Creates an error annotation with the specified message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the error message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createErrorAnnotation(@NotNull TextRange range, @Nullable String message);

  /**
   * Creates a warning annotation with the specified message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the warning message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createWarningAnnotation(@NotNull PsiElement elt, @Nullable String message);

  /**
   * Creates a warning annotation with the specified message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the warning message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createWarningAnnotation(@NotNull ASTNode node, @Nullable String message);

  /**
   * Creates a warning annotation with the specified message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the warning message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createWarningAnnotation(@NotNull TextRange range, @Nullable String message);

  /**
   * Creates an annotation with severity {@link HighlightSeverity#WEAK_WARNING} ('weak warning') with the specified
   * message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the info message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createWeakWarningAnnotation(@NotNull PsiElement elt, @Nullable String message);

  /**
   * Creates an annotation with severity {@link HighlightSeverity#WEAK_WARNING} ('weak warning') with the specified
   * message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the info message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createWeakWarningAnnotation(@NotNull ASTNode node, @Nullable String message);

  /**
   * Creates an annotation with severity {@link HighlightSeverity#WEAK_WARNING} ('weak warning') with the specified
   * message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the info message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createWeakWarningAnnotation(@NotNull TextRange range, @Nullable String message);

  /**
   * Creates an information annotation (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation) with the specified message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createInfoAnnotation(@NotNull PsiElement elt, @Nullable String message);

  /**
   * Creates an information annotation (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation) with the specified message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createInfoAnnotation(@NotNull ASTNode node, @Nullable String message);

  /**
   * Creates an information annotation (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation)with the specified message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createInfoAnnotation(@NotNull TextRange range, @Nullable String message);

  /**
   * Creates an annotation with the given severity (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation) with the specified message over the specified text range.
   *
   * @param severity the severity.
   * @param range    the text range over which the annotation is created.
   * @param message  the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createAnnotation(@NotNull HighlightSeverity severity, @NotNull TextRange range, @Nullable String message);

  /**
   * Creates an annotation with the given severity (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation) with the specified message and tooltip markup over the specified text range.
   *
   * @param severity the severity.
   * @param range    the text range over which the annotation is created.
   * @param message  the information message.
   * @param htmlTooltip  the tooltip to show (usually the message, but escaped as HTML and surrounded by a {@code <html>} tag
   * @return the annotation (which can be modified to set additional annotation parameters)
   */
  Annotation createAnnotation(@NotNull HighlightSeverity severity,
                              @NotNull TextRange range,
                              @Nullable String message,
                              @Nullable String htmlTooltip);

  @NotNull
  AnnotationSession getCurrentAnnotationSession();

  boolean isBatchMode();
}