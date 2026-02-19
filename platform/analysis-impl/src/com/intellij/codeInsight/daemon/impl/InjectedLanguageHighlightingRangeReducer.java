// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Experimental
public interface InjectedLanguageHighlightingRangeReducer {
  ExtensionPointName<InjectedLanguageHighlightingRangeReducer> EP_NAME = new ExtensionPointName<>("com.intellij.codeInsight.daemon.impl.injectedLanguageHighlightingRangeReducer");

  /**
   * Provides a way to select particular ranges to be highlighted during the pass instead of the whole file.
   * It may be useful in documents with lots of editable injectedFiles to process only changed ones on document change event,
   * like in Kotlin Notebooks.
   * NB: if several reduced ranges are passed, then restricted analysis area will be {topLeft; topRight} across ranges offsets.
   *
   * @return collection of ranges to highlight or null if this extension doesn't supply any reduced ranges
   * @see InjectedGeneralHighlightingPassFactory
   */
  List<@NotNull TextRange> reduceRange(@NotNull PsiFile psiFile, @NotNull Editor editor);
}
