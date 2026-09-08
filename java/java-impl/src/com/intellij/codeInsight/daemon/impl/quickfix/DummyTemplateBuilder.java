// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Drops every template field. The code which creates a class writes the default value of each field
 * into the PSI, so the result is already correct without the template.
 * <p>
 * A {@link com.intellij.modcommand.ModCommandAction} which creates a new file uses it. The template of
 * such a file cannot run, because the platform reads the text of the new file at the end of the action.
 */
final class DummyTemplateBuilder implements TemplateBuilder {
  static final DummyTemplateBuilder INSTANCE = new DummyTemplateBuilder();

  private DummyTemplateBuilder() {
  }

  @Override
  public void replaceElement(@NotNull PsiElement element, @NlsSafe String replacementText) {
  }

  @Override
  public void replaceElement(@NotNull PsiElement element, TextRange rangeWithinElement, String replacementText) {
  }

  @Override
  public void replaceElement(@NotNull PsiElement element, Expression expression) {
  }

  @Override
  public void replaceElement(@NotNull PsiElement element, TextRange rangeWithinElement, Expression expression) {
  }

  @Override
  public void replaceElement(PsiElement element, @NlsSafe String varName, Expression expression, boolean alwaysStopAt) {
  }

  @Override
  public void replaceElement(PsiElement element, @NlsSafe String varName, String dependantVariableName, boolean alwaysStopAt) {
  }

  @Override
  public void replaceRange(TextRange rangeWithinElement, String replacementText) {
  }

  @Override
  public void replaceRange(TextRange rangeWithinElement, Expression expression) {
  }

  @Override
  public void runNonInteractively(boolean inline) {
  }

  @Override
  public void run(@NotNull Editor editor, boolean inline) {
  }

  @Override
  public TemplateBuilder setScrollToTemplate(boolean scrollToTemplate) {
    return this;
  }
}
