// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows filtering specific {@link HighlightInfo}s.
 * <p>
 * It can be used in situations when highlight infos are false-positives or unnecessary in a given file context.
 *
 * @see com.intellij.codeInsight.highlighting.HighlightErrorFilter
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/controlling-highlighting.html">Controlling Highlighting (IntelliJ Platform Docs)</a>
 */
public interface HighlightInfoFilter {
  HighlightInfoFilter[] EMPTY_ARRAY = new HighlightInfoFilter[0];
  ExtensionPointName<HighlightInfoFilter> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.daemon.highlightInfoFilter");

  /**
   * @param file - might (and will be) null. Return true in this case if you'd like to switch this kind of highlighting in ANY file
   */
  boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file);
}
