// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LanguageImportStatements extends LanguageExtension<ImportOptimizer> {
  public static final LanguageImportStatements INSTANCE = new LanguageImportStatements();

  private LanguageImportStatements() {
    super("com.intellij.lang.importOptimizer");
  }

  @NotNull
  public Set<ImportOptimizer> forFile(@NotNull PsiFile file) {
    CodeStyleSettings settings = CodeStyle.getSettings(file);
    if (settings.getExcludedFiles().contains(file)) {
      return Collections.emptySet();
    }
    Set<ImportOptimizer> optimizers = new HashSet<>();
    for (PsiFile psiFile : file.getViewProvider().getAllFiles()) {
      List<ImportOptimizer> langOptimizers = allForLanguage(psiFile.getLanguage());
      for (ImportOptimizer optimizer : langOptimizers) {
        if (optimizer != null && optimizer.supports(psiFile)) {
          optimizers.add(optimizer);
          break;
        }
      }
    }
    return optimizers;
  }
}