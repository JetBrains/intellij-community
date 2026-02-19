// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * A template optional processor that can be executed inside {@link com.intellij.modcommand.ModCommand#psiUpdate(PsiElement, Consumer)} 
 * session.
 */
public interface ModCommandAwareTemplateOptionalProcessor extends TemplateOptionalProcessor {
  /**
   * @param template template that was applied
   * @param navigator navigator to use
   * @param templateRange whole template range
   */
  void processText(@NotNull Template template,
                   @NotNull ModNavigator navigator,
                   @NotNull RangeMarker templateRange);

  @Override
  default void processText(final Project project,
                           final Template template,
                           final Document document,
                           final RangeMarker templateRange,
                           final Editor editor) {
    processText(template, editor.asModNavigator(), templateRange);
  }
}
