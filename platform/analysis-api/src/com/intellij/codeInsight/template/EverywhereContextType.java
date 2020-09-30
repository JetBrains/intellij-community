// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class EverywhereContextType extends TemplateContextType {
  public EverywhereContextType() {
    super("OTHER", AnalysisBundle.message("template.context.everywhere"), null);
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return true;
  }

}
