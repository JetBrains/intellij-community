// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import javax.swing.*;

/**
 * A component that represents a fragment which wraps focusable component
 */
public interface FragmentWrapper {
  /**
   * @return child component to register listeners at
   */
  JComponent getComponentToRegister();
}
