// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.excludedFiles;

import com.intellij.CodeStyleBundle;
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Legacy panel for excluded scopes. Shown only if a user has already defined scope-based exclusions.
 */
public class ExcludedScopesPanel extends JPanel {

  private final ExcludedFilesList myExcludedFilesList;

  public ExcludedScopesPanel() {
    setLayout(new BorderLayout());
    setBorder(IdeBorderFactory.createTitledBorder(CodeStyleBundle.message("excluded.files.border.title.scopes")));
    myExcludedFilesList = new ExcludedFilesList();
    myExcludedFilesList.initModel();
    myExcludedFilesList.setBorder(JBUI.Borders.customLine(JBColor.border(),0,1,1,1));
    JPanel toolbarPanel = new JPanel();
    toolbarPanel.setLayout(new BorderLayout());
    JPanel decoratorPanel = myExcludedFilesList.getDecorator().createPanel();
    decoratorPanel.setBorder(JBUI.Borders.customLine(JBColor.border(),1,1,0,1));
    toolbarPanel.add(decoratorPanel, BorderLayout.CENTER);
    add(toolbarPanel, BorderLayout.NORTH);
    add(myExcludedFilesList, BorderLayout.CENTER);
    JLabel migrationLabel = new JLabel(CodeStyleBundle.message("excluded.files.deprecation.label.text"));
    migrationLabel.setBorder(JBUI.Borders.emptyTop(10));
    add(migrationLabel, BorderLayout.SOUTH);
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
