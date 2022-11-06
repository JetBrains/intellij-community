// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface InjectedLanguageHighlightingRangeReducer {
  ExtensionPointName<InjectedLanguageHighlightingRangeReducer> EP_NAME = new ExtensionPointName<>("com.intellij.codeInsight.daemon.impl.injectedLanguageHighlightingRangeReducer");
  TextRange reduceRange(@NotNull PsiFile file, @NotNull Editor editor);
}
