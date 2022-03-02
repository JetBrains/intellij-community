// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.NotNull;

/**
 * Allows disabling syntax errors highlighting for {@link PsiErrorElement}s.
 * <p>
 * It can be used in situations when custom error annotation provides better explanation,
 * or when syntax error can be ignored or annotated on different level (e.g. warning, info, etc.).
 *
 * @see com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/syntax-errors.html">Syntax Errors (IntelliJ Platform Docs)</a>
 */
public abstract class HighlightErrorFilter {
  public static final ProjectExtensionPointName<HighlightErrorFilter> EP_NAME = new ProjectExtensionPointName<>("com.intellij.highlightErrorFilter");

  public abstract boolean shouldHighlightErrorElement(@NotNull final PsiErrorElement element);
}
