// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.excludedFiles;

import com.intellij.CodeStyleBundle;
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Legacy panel for excluded scopes. Shown only if a user has already defined scope-based exclusions.
 */
public class ExcludedScopesPanel extends ExcludedFilesPanelBase {

  private final ExcludedFilesList myExcludedFilesList;

  public ExcludedScopesPanel() {
    setLayout(new GridBagLayout());
    setBorder(JBUI.Borders.empty());
    GridBagConstraints c = new GridBagConstraints();
    myExcludedFilesList = new ExcludedFilesList();
    myExcludedFilesList.initModel();
    myExcludedFilesList.setBorder(JBUI.Borders.customLine(JBColor.border(),0,1,1,1));
    JPanel toolbarPanel = new JPanel();
    toolbarPanel.setLayout(new BorderLayout());
    JPanel decoratorPanel = myExcludedFilesList.getDecorator().createPanel();
    decoratorPanel.setBorder(JBUI.Borders.customLine(JBColor.border(),1,1,0,1));
    toolbarPanel.add(decoratorPanel, BorderLayout.CENTER);
    c.weightx = 1;
    c.gridx = 0;
    c.gridy = 0;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = JBUI.insetsBottom(5);
    add(new JLabel(CodeStyleBundle.message("excluded.files.do.not.format.scope")), c);
    c.gridy ++;
    c.insets = JBInsets.emptyInsets();
    add(toolbarPanel, c);
    c.gridy ++;
    add(myExcludedFilesList, c);
    JComponent migrationMessage = createWarningMessage(CodeStyleBundle.message("excluded.files.deprecation.label.text"));
    migrationMessage.setBorder(JBUI.Borders.emptyTop(10));
    c.gridy ++;
    add(migrationMessage, c);
  }

  public void apply(@NotNull CodeStyleSettings settings) {
    myExcludedFilesList.apply(settings);
    if (myExcludedFilesList.isEmpty()) {
      setVisible(false);
    }
  }

  public void reset(@NotNull CodeStyleSettings settings) {
    myExcludedFilesList.reset(settings);
    if (myExcludedFilesList.isEmpty()) {
      setVisible(false);
    }
  }

  public boolean isModified(@NotNull CodeStyleSettings settings) {
    return myExcludedFilesList.isModified(settings);
  }

  public void setSchemesModel(@NotNull CodeStyleSchemesModel model) {
    myExcludedFilesList.setSchemesModel(model);
  }
}
