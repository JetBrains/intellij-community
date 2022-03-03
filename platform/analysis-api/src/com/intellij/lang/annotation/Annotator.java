// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.annotation;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Implemented by a custom language plugin to add annotations to files in the language.
 * <p>
 * DO NOT STORE any state inside annotator.
 * If you absolutely must, clear the state upon exit from the {@link #annotate(PsiElement, AnnotationHolder)} method.
 * <p/>
 * See <a href="https://www.jetbrains.org/intellij/sdk/docs/reference_guide/custom_language_support/syntax_highlighting_and_error_highlighting.html#annotator">Syntax Highlighting and Error Highlighting</a> for an overview.
 *
 * Please note: annotators are executed in arbitrary order.
 *
 * @see com.intellij.lang.LanguageAnnotators
 */
public interface Annotator {
  /**
   * Annotates the specified PSI element.
   * It is guaranteed to be executed in non-reentrant fashion.
   * I.e, there will be no call of this method for this instance before previous call get completed.
   * Multiple instances of the annotator might exist simultaneously, though.
   *
   * @param element to annotate.
   * @param holder  the container which receives annotations created by the plugin.
   */
  void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder);
}
