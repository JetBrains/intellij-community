/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.configurations;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author dyoma
 */
public abstract class ConfigurationFactory {
  public static final Icon ADD_ICON = IconLoader.getIcon("/general/add.png");

  private final ConfigurationType myType;

  protected ConfigurationFactory(final ConfigurationType type) {
    myType = type;
  }

  public RunConfiguration createConfiguration(String name, RunConfiguration template) {
    RunConfiguration newConfiguration = template.clone();
    newConfiguration.setName(name);
    return newConfiguration;
  }

  public abstract RunConfiguration createTemplateConfiguration(Project project);

  public String getName() {
    return myType.getDisplayName();
  }

  public Icon getAddIcon() {
    return ADD_ICON;
  }

  public Icon getIcon() {
    return myType.getIcon();
  }

  public ConfigurationType getType() {
    return myType;
  }
}
