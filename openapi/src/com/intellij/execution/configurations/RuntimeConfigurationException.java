/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.configurations;

import com.intellij.openapi.options.ConfigurationException;

public class RuntimeConfigurationException extends ConfigurationException {
  public RuntimeConfigurationException(final String message, final String title) {
    super(message, title);
  }

  public RuntimeConfigurationException(final String message) {
    super(message, "Run Configuration Error");
  }
}