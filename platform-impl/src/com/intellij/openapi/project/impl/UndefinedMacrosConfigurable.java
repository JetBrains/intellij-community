/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project.impl;

import com.intellij.application.options.pathMacros.PathMacroConfigurable;
import com.intellij.application.options.pathMacros.PathMacroListEditor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 4, 2004
 */
public class UndefinedMacrosConfigurable implements Configurable{
  private PathMacroListEditor myEditor;
  private final String myText;
  private final String[] myUndefinedMacroNames;

  public UndefinedMacrosConfigurable(String text, String[] undefinedMacroNames) {
    myText = text;
    myUndefinedMacroNames = undefinedMacroNames;
  }

  public String getHelpTopic() {
    return PathMacroConfigurable.HELP_ID;
  }

  public Icon getIcon() {
    return PathMacroConfigurable.ICON;
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.configure.path.variables.title");
  }

  public JComponent createComponent() {
    final JPanel mainPanel = new JPanel(new BorderLayout());
    // important: do not allow to remove or change macro name for already defined macros befor project is loaded
    myEditor = new PathMacroListEditor(myUndefinedMacroNames, true);
    final JComponent editorPanel = myEditor.getPanel();

    mainPanel.add(editorPanel, BorderLayout.CENTER);

    final JLabel textLabel = new JLabel(myText);
    textLabel.setUI(new MultiLineLabelUI());
    textLabel.setBorder(IdeBorderFactory.createEmptyBorder(6, 6, 6, 6));
    mainPanel.add(textLabel, BorderLayout.NORTH);

    return mainPanel;
  }

  public boolean isModified() {
    return myEditor.isModified();
  }

  public void apply() throws ConfigurationException {
    myEditor.commit();
  }

  public void reset() {
    myEditor.reset();
  }

  public void disposeUIResources() {
    myEditor = null;
  }
}
