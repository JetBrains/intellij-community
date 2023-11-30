// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.annotation;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Implemented by a plugin to add annotations to references inside {@link PsiLanguageInjectionHost}, {@link HintedReferenceHost}
 * and {@link ContributedReferenceHost} elements.
 * <p>
 * Typical usage is highlighting URL references inside String literals so that they are discoverable by users,
 * e.g. <code>URI.create("/api/example")</code> where `/api/example` is underlined by such annotator to show references in unusual place.
 */
public interface ContributedReferencesAnnotator {
  /**
   * Annotates the specified PSI element of one of types: {@link PsiLanguageInjectionHost}, {@link HintedReferenceHost} or {@link ContributedReferenceHost}.
   *
   * @param element    to annotate.
   * @param references references of the element.
   * @param holder     the container which receives annotations created by the plugin.
   */
  void annotate(@NotNull PsiElement element,
                @NotNull List<PsiReference> references,
                @NotNull AnnotationHolder holder);
}
