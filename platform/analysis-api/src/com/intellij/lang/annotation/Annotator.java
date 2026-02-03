// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.annotation;

import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Implemented by a custom language plugin to add annotations to files in the language.
 * <p>
 * DO NOT STORE any state inside annotator.
 * If you absolutely must, clear the state upon exit from the {@link #annotate(PsiElement, AnnotationHolder)} method.
 * <p/>
 * See <a href="https://plugins.jetbrains.com/docs/intellij/syntax-highlighting-and-error-highlighting.html#annotator">Syntax Highlighting and Error Highlighting</a> for an overview.
 * <p>
 * Please note: annotators are executed in arbitrary order.
 * <p>
 * Implement {@link com.intellij.openapi.project.DumbAware} to allow running during index updates.
 *
 * @see com.intellij.lang.LanguageAnnotators
 */
public interface Annotator extends PossiblyDumbAware {
  /**
   * Annotates the specified PSI element.
   * <p>
   * It is guaranteed to be executed in a non-reentrant fashion, i.e.,
   * there will be no call of this method for this instance before the previous call is completed.
   * Multiple instances of the annotator might exist simultaneously, though.
   * <p>
   * Please make sure the implementation of this method is creating annotations with the text range lying within the current PSI {@code element}
   * to minimize annoying flickering and inconsistencies.
   *
   * @param element to annotate.
   * @param holder  the container which receives annotations created by the plugin.
   */
  void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder);
}
