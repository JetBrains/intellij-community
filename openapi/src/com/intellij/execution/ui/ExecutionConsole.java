/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.ui;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 20, 2004
 */
public interface ExecutionConsole {
  JComponent getComponent();

  void dispose();

}
