// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class EverywhereContextType extends TemplateContextType {
  public EverywhereContextType() {
    super(AnalysisBundle.message("template.context.everywhere"));
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return true;
  }
}
