/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.options;

import javax.swing.*;

/**
 * author: lesya
 */
public interface UnnamedConfigurable {
  JComponent createComponent();

  boolean isModified();

  /**
   * Store the settings from configurable to other components.
   */
  void apply() throws ConfigurationException;

  /**
   * Load settings from other components to configurable.
   */
  void reset();

  void disposeUIResources();
}
