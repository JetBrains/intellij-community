// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;


public interface HighlightRangeExtension {
  ExtensionPointName<HighlightRangeExtension> EP_NAME = ExtensionPointName.create("com.intellij.highlightRangeExtension");

  /**
   * @return true if this file structure is so peculiar and irregular that it's needed to highlight the parents of the PSI element with an error inside.
   * In particular, {@link com.intellij.lang.annotation.Annotator}s will be called for all PSI elements irrespective of children with errors.
   * (Regular highlighting doesn't analyze parents of PSI elements with an error).
   * Please be aware that returning true may decrease highlighting performance/increase latency.
   */
  boolean isForceHighlightParents(@NotNull PsiFile psiFile);
}
