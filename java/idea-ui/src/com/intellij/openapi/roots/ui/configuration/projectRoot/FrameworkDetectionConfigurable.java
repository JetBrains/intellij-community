// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.framework.detection.impl.exclude.DetectionExcludesConfigurable;
import com.intellij.framework.detection.impl.exclude.DetectionExcludesConfigurationImpl;
import com.intellij.ide.JavaUiBundle;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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
    return LangBundle.message("dialog.title.framework.detection");
  }

  @Override
  public JComponent createOptionsPanel() {
    return myConfigurable.createComponent();
  }

  @Override
  public @Nls String getDisplayName() {
    return JavaUiBundle.message("configurable.FrameworkDetectionConfigurable.display.name");
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
