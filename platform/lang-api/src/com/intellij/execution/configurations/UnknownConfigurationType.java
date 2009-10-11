/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author spleaner
 */
public class UnknownConfigurationType implements ConfigurationType {

  public static final UnknownConfigurationType INSTANCE = new UnknownConfigurationType();

  private static final Icon ICON = IconLoader.getIcon("/runConfigurations/unknown.png");
  public static final String NAME = "Unknown";

  public String getDisplayName() {
    return getId();
  }

  public String getConfigurationTypeDescription() {
    return "Configuration which cannot be loaded due to some resons";
  }

  public Icon getIcon() {
    return ICON;
  }

  @NotNull
  public String getId() {
    return NAME;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] {new ConfigurationFactory(new UnknownConfigurationType()) {
      @Override
      public RunConfiguration createTemplateConfiguration(final Project project) {
        return new UnknownRunConfiguration(this, project);
      }
    }};
  }
}
