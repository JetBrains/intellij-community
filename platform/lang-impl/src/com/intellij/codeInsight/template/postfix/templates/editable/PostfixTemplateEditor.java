// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Represents the postfix template editor used for creating and editing editable templates.
 *
 * @see com.intellij.codeInsight.template.postfix.settings.PostfixEditTemplateDialog
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/advanced-postfix-templates.html">Advanced Postfix Templates (IntelliJ Platform Docs)</a>
 */
public interface PostfixTemplateEditor extends Disposable {

  /**
   * Creates a template from settings defined in the UI form.
   *
   * @param templateId unique template ID
   * @return created template
   */
  @NotNull
  PostfixTemplate createTemplate(@NotNull String templateId, @NotNull String templateName);

  /**
   * @return template settings form component
   */
  @NotNull
  JComponent getComponent();

  default @NonNls String getHelpId() {
    return null;
  }
}
