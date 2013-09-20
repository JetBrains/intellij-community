package com.intellij.lang.annotation;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to crate semantic annotations to highlight similar PSI elements differently depending on their actual meaning. For example,
 * static members vs. instance fields and etc. Semantic annotations have lower highlighting level than infos, weak warnings etc. but
 * higher than regular syntax.
 *
 * @author Rustam Vishnyakov
 */
public interface SemanticAnnotationHolder extends AnnotationHolder {

  /**
   * Creates annotation with a semantic highlighting level.
   *
   * @param range         The text range over which the annotation is created.
   * @param attributesKey The attributes key to use for the annotation.
   * @return the annotation (which can be modified to set additional annotation parameters).
   */
  Annotation createSemanticAnnotation(@NotNull TextRange range, @NotNull TextAttributesKey attributesKey);
}
