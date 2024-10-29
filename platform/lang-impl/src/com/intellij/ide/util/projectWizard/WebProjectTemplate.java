// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.module.WebModuleBuilder;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.WebProjectGenerator;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class WebProjectTemplate<T> extends WebProjectGenerator<T> implements ProjectTemplate {
  @Override
  public @NotNull ModuleBuilder createModuleBuilder() {
    return new WebModuleBuilder<>(this);
  }

  @Override
  public @Nullable ValidationInfo validateSettings() {
    return null;
  }

  @Override
  public Icon getIcon() {
    return WebModuleBuilder.ICON;
  }

  @Override
  public Icon getLogo() {
    return getIcon();
  }

  /**
   * Allows to postpone first start of validation
   *
   * @return {@code false} if start validation in {@link ProjectSettingsStepBase#registerValidators()} method
   */
  public boolean postponeValidation() {
    return true;
  }

  public static @NotNull JPanel createTitlePanel() {
    final JPanel titlePanel = new JPanel(new BorderLayout());
    final JLabel title = new JLabel(ProjectBundle.message("label.new.project"));
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    titlePanel.add(title, BorderLayout.WEST);
    titlePanel.setBorder(JBUI.Borders.emptyBottom(10));
    return titlePanel;
  }
}
