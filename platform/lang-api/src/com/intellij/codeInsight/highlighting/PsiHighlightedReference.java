// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolReferenceProviderBean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this interface to highlight the reference in the editor.
 * Specify {@linkplain PsiSymbolReferenceProviderBean#referenceClass the reference class}
 * for the corresponding reference provider in order for the reference to be discovered by the platform.
 */
@ApiStatus.Experimental
public interface PsiHighlightedReference extends PsiSymbolReference {

  default @NotNull HighlightSeverity highlightSeverity() {
    return HighlightSeverity.INFORMATION;
  }

  default @Nls(capitalization = Nls.Capitalization.Sentence) @Nullable String highlightMessage() {
    return null;
  }

  /**
   * Implement this method to set various attributes of the highlight.
   */
  default @NotNull AnnotationBuilder highlightReference(@NotNull AnnotationBuilder annotationBuilder) {
    return annotationBuilder;
  }
}
