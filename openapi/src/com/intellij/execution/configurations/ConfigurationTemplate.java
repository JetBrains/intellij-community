/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.configurations;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.JDOMExternalizable;

/**
 * @author dyoma
 */
public interface ConfigurationTemplate extends JDOMExternalizable {
  /**
   * @return nver <code>null</code>
   */
  Configurable createTemplateConfigurable();

  /**
   * @return never <code></code>
   */
  RunConfiguration createConfiguration(String name);
}
