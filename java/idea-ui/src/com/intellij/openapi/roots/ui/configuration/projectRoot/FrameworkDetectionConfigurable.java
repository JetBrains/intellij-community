/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.framework.detection.impl.exclude.DetectionExcludesConfigurable;
import com.intellij.framework.detection.impl.exclude.DetectionExcludesConfigurationImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class FrameworkDetectionConfigurable extends NamedConfigurable<DetectionExcludesConfiguration> {
  private final DetectionExcludesConfiguration myExcludesConfiguration;
  private final DetectionExcludesConfigurable myConfigurable;

  public FrameworkDetectionConfigurable(@NotNull Project project) {
    myExcludesConfiguration = DetectionExcludesConfiguration.getInstance(project);
    myConfigurable = new DetectionExcludesConfigurable(project, (DetectionExcludesConfigurationImpl)myExcludesConfiguration);
  }

  @Override
  public void setDisplayName(String name) {
  }

  @Override
  public DetectionExcludesConfiguration getEditableObject() {
    return myExcludesConfiguration;
  }

  @Override
  public String getBannerSlogan() {
    return "Framework Detection";
  }

  @Override
  public JComponent createOptionsPanel() {
    return myConfigurable.createComponent();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Detection";
  }

  @Override
  public String getHelpTopic() {
    return myConfigurable.getHelpTopic();
  }

  @Override
  public boolean isModified() {
    return myConfigurable.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfigurable.apply();
  }

  @Override
  public void reset() {
    myConfigurable.reset();
  }

  @Override
  public void disposeUIResources() {
    myConfigurable.disposeUIResources();
  }
}
