// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * Provider for a filter of injected files found in a host.
 * One InjectionHost could have several injection {@code PsiFile}, which
 * not always contain visible part for highlighting.
 */
@ApiStatus.Experimental
public interface InjectedLanguageHighlightingFilesFilterProvider {
  ExtensionPointName<InjectedLanguageHighlightingFilesFilterProvider> EP_NAME = new ExtensionPointName<>("com.intellij.codeInsight.daemon.impl.injectedLanguageHighlightingFilesFilterProvider");

  Predicate<PsiFile> provideFilterForInjectedFiles(@NotNull PsiFile file, @NotNull Editor editor);
}
