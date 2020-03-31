// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public abstract class HighlightErrorFilter {
  public static final ProjectExtensionPointName<HighlightErrorFilter> EP_NAME = new ProjectExtensionPointName<>("com.intellij.highlightErrorFilter");

  public abstract boolean shouldHighlightErrorElement(@NotNull final PsiErrorElement element);

}
