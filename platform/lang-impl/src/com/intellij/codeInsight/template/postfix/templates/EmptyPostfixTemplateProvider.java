// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public final class EmptyPostfixTemplateProvider implements PostfixTemplateProvider {

  private final HashSet<PostfixTemplate> myTemplates = new HashSet<>();

  @Override
  public @NotNull String getId() {
    return "builtin.empty";
  }

  @Override
  public @NotNull Set<PostfixTemplate> getTemplates() {
    return myTemplates;
  }

  @Override
  public boolean isTerminalSymbol(char currentChar) {
    return false;
  }

  @Override
  public void preExpand(@NotNull PsiFile file, @NotNull Editor editor) {

  }

  @Override
  public void afterExpand(@NotNull PsiFile file, @NotNull Editor editor) {

  }

  @Override
  public @NotNull PsiFile preCheck(@NotNull PsiFile copyFile, @NotNull Editor realEditor, int currentOffset) {
    return copyFile;
  }
}
