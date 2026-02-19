// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.annotation;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows a custom language plugin to define annotations for files in that language.
 * To get hold of {@link AnnotationHolder} in your code, you should
 * <ol>
 * <li>create and register your own {@link Annotator}
 * (See <a href="https://plugins.jetbrains.com/docs/intellij/annotator.html">Annotator Guide</a> on how to create your own {@link Annotator}), and then</li>
 * <li>use the argument passed to its {@link Annotator#annotate(PsiElement, AnnotationHolder)} method.</li>
 * </ol>
 *
 * @author max
 * @see Annotator#annotate(PsiElement, AnnotationHolder)
 */
@ApiStatus.NonExtendable
public interface AnnotationHolder {
  /**
   * Begin constructing a new annotation.
   * To finish construction and show the annotation on screen {@link AnnotationBuilder#create()} must be called.
   * For example: <p>{@code holder.newAnnotation(HighlightSeverity.WARNING, "My warning message").create();}</p>
   *
   * @param severity The severity of the annotation.
   * @param message  The message this annotation will show in the status bar and the tooltip.
   * @apiNote The builder created by this method is already initialized by the current element, i.e., the psiElement being visited by the current annotator.
   * You need to call {@link AnnotationBuilder#range(TextRange)} or similar method explicitly only if target element differs from the current element.
   * Please note, that the range in {@link AnnotationBuilder#range(TextRange)} must be inside the range of the current element.
   * @return builder instance you can use to further customize your annotation
   */
  @Contract(pure = true)
  default @NotNull AnnotationBuilder newAnnotation(@NotNull HighlightSeverity severity, @NotNull @InspectionMessage String message) {
    throw new IllegalStateException("Please do not override AnnotationHolder, use the standard one instead");
  }

  /**
   * Begin constructing a new annotation with no message and no tooltip.
   * Such annotations could be useful for coloring a text range with some decorations
   * (see e.g. {@link AnnotationBuilder#textAttributes(TextAttributesKey)} or {@link AnnotationBuilder#gutterIconRenderer(GutterIconRenderer)} as examples of decorating methods).
   * To finish construction and show the annotation on screen {@link AnnotationBuilder#create()} must be called.
   * For example: <p>{@code holder.newSilentAnnotation(HighlightSeverity.WARNING).textAttributes(MY_ATTRIBUTES_KEY).create();}</p>
   *
   * @param severity The severity of the annotation.
   * @apiNote The builder created by this method is already initialized by the current element, i.e., the psiElement being visited by the current annotator.
   * You need to call {@link AnnotationBuilder#range(TextRange)} or similar method explicitly only if target element differs from the current element.
   * Please note that the range in {@link AnnotationBuilder#range(TextRange)} must be inside the range of the current element.
   * @return builder instance you can use to further customize your annotation
   */
  @Contract(pure = true)
  default @NotNull AnnotationBuilder newSilentAnnotation(@NotNull HighlightSeverity severity) {
    throw new IllegalStateException("Please do not override AnnotationHolder, use the standard one instead");
  }

  //<editor-fold desc="Deprecated stuff.">

  /**
   * Creates an error annotation with the specified message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the error message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   * @deprecated Use {@link #newAnnotation(HighlightSeverity, String)} instead
   */
  @Deprecated
  Annotation createErrorAnnotation(@NotNull PsiElement elt, @Nullable @InspectionMessage String message);

  /**
   * Creates an error annotation with the specified message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the error message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   * @deprecated Use {@link #newAnnotation(HighlightSeverity, String)} instead
   */
  @Deprecated
  Annotation createErrorAnnotation(@NotNull ASTNode node, @Nullable @InspectionMessage String message);

  /**
   * Creates an error annotation with the specified message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the error message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   * @deprecated Use {@link #newAnnotation(HighlightSeverity, String)} instead
   */
  @Deprecated
  Annotation createErrorAnnotation(@NotNull TextRange range, @Nullable @InspectionMessage String message);

  /**
   * Creates a warning annotation with the specified message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the warning message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   * @deprecated Use {@link #newAnnotation(HighlightSeverity, String)} instead
   */
  @Deprecated
  Annotation createWarningAnnotation(@NotNull PsiElement elt, @Nullable @InspectionMessage String message);

  /**
   * Creates a warning annotation with the specified message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the warning message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   * @deprecated Use {@link #newAnnotation(HighlightSeverity, String)} instead
   */
  @Deprecated(forRemoval = true)
  Annotation createWarningAnnotation(@NotNull ASTNode node, @Nullable @InspectionMessage String message);

  /**
   * Creates a warning annotation with the specified message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the warning message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   * @deprecated Use {@link #newAnnotation(HighlightSeverity, String)} instead
   */
  @Deprecated
  Annotation createWarningAnnotation(@NotNull TextRange range, @Nullable @InspectionMessage String message);

  /**
   * Creates an annotation with severity {@link HighlightSeverity#WEAK_WARNING} ('weak warning') with the specified
   * message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the info message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   * @deprecated Use {@link #newAnnotation(HighlightSeverity, String)} instead
   */
  @Deprecated
  Annotation createWeakWarningAnnotation(@NotNull PsiElement elt,
                                         @Nullable @InspectionMessage String message);

  /**
   * Creates an annotation with severity {@link HighlightSeverity#WEAK_WARNING} ('weak warning') with the specified
   * message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the info message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   * @deprecated Use {@link #newAnnotation(HighlightSeverity, String)} instead
   */
  @Deprecated
  Annotation createWeakWarningAnnotation(@NotNull TextRange range,
                                         @Nullable @InspectionMessage String message);

  /**
   * Creates an information annotation (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation) with the specified message over the specified PSI element.
   *
   * @param elt     the element over which the annotation is created.
   * @param message the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   * @deprecated Use {@link #newAnnotation(HighlightSeverity, String)} instead
   */
  @Deprecated
  Annotation createInfoAnnotation(@NotNull PsiElement elt, @Nullable @InspectionMessage String message);

  /**
   * Creates an information annotation (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation) with the specified message over the specified AST node.
   *
   * @param node    the node over which the annotation is created.
   * @param message the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   * @deprecated Use {@link #newAnnotation(HighlightSeverity, String)} instead
   */
  @Deprecated
  Annotation createInfoAnnotation(@NotNull ASTNode node, @Nullable @InspectionMessage String message);

  /**
   * Creates an information annotation (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation) with the specified message over the specified text range.
   *
   * @param range   the text range over which the annotation is created.
   * @param message the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   * @deprecated Use {@link #newAnnotation(HighlightSeverity, String)} instead
   */
  @Deprecated
  Annotation createInfoAnnotation(@NotNull TextRange range, @Nullable @InspectionMessage String message);

  /**
   * Creates an annotation with the given severity (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation) with the specified message over the specified text range.
   *
   * @param severity the severity.
   * @param range    the text range over which the annotation is created.
   * @param message  the information message.
   * @return the annotation (which can be modified to set additional annotation parameters)
   * @deprecated Use {@link #newAnnotation(HighlightSeverity, String)} instead
   */
  @Deprecated
  Annotation createAnnotation(@NotNull HighlightSeverity severity,
                              @NotNull TextRange range,
                              @Nullable @InspectionMessage String message);

  /**
   * Creates an annotation with the given severity (colored highlighting only, with no gutter mark and not participating in
   * "Next Error/Warning" navigation) with the specified message and tooltip markup over the specified text range.
   *
   * @param severity    the severity.
   * @param range       the text range over which the annotation is created.
   * @param message     the information message.
   * @param htmlTooltip the tooltip to show (usually the message, but escaped as HTML and surrounded by a {@code <html>} tag
   * @return the annotation (which can be modified to set additional annotation parameters)
   * @deprecated Use {@link #newAnnotation(HighlightSeverity, String)} instead
   */
  @Deprecated
  Annotation createAnnotation(@NotNull HighlightSeverity severity,
                              @NotNull TextRange range,
                              @Nullable @InspectionMessage String message,
                              @Nullable String htmlTooltip);

  //</editor-fold>

  /**
   * @return the session in which the annotators are running.
   * It's guaranteed that during this session {@link Annotator#annotate(PsiElement, AnnotationHolder)} is called at most once for each PSI element in the file,
   * then found annotations are displayed on the screen, then the session is disposed.
   */
  @NotNull
  AnnotationSession getCurrentAnnotationSession();

  /**
   * @return true if the inspections are running in batch mode (see "Code|Inspect Code..."), false if the inspections are in the on-the-fly mode (i.e., they are run when the editor opened the file in the window).
   * The difference is in the desired latency level, which may require reducing the power of analysis in the on-the-fly mode to improve responsiveness.
   */
  boolean isBatchMode();
}