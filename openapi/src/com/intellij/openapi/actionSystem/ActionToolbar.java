/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

import javax.swing.*;

/**
 * Represents a toolbar with a visual presentation.
 */
public interface ActionToolbar {

  /**
   * @return component which represents the tool bar on UI
   */
  JComponent getComponent();
}
