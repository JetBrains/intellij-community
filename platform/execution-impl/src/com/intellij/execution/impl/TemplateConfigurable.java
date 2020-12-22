// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.options.ConfigurationException;

/**
 * @author Dmitry Avdeev
 */
class TemplateConfigurable extends BaseRCSettingsConfigurable {
  private final RunnerAndConfigurationSettings myTemplate;

  TemplateConfigurable(RunnerAndConfigurationSettings template) {
    super(ConfigurationSettingsEditorWrapper.createWrapper(template), template);
    myTemplate = template;
  }

  @Override
  public String getDisplayName() {
    return myTemplate.getConfiguration().getName();
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    RunManagerImpl.getInstanceImpl(myTemplate.getConfiguration().getProject()).addConfiguration(myTemplate);
  }
}
