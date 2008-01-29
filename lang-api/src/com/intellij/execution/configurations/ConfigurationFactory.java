/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
