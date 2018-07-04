// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.module.WebModuleBuilder;
import com.intellij.openapi.module.WebModuleType;
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
  @NotNull
  @Override
  public ModuleBuilder createModuleBuilder() {
    return WebModuleType.getInstance().createModuleBuilder(this);
  }

  @Nullable
  @Override
  public ValidationInfo validateSettings() {
    return null;
  }

  public Icon getIcon() {
    return WebModuleBuilder.ICON;
  }

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

  @NotNull
  public static JPanel createTitlePanel() {
    final JPanel titlePanel = new JPanel(new BorderLayout());
    final JLabel title = new JLabel("New project");
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    titlePanel.add(title, BorderLayout.WEST);
    titlePanel.setBorder(JBUI.Borders.emptyBottom(10));
    return titlePanel;
  }
}
