/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.configurations;

import com.intellij.openapi.components.ApplicationComponent;

import javax.swing.*;

public interface ConfigurationType extends ApplicationComponent {
  String getDisplayName();
  String getConfigurationTypeDescription();
  Icon getIcon();

  ConfigurationFactory[] getConfigurationFactories();
}