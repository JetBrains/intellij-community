/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diff;

import javax.swing.*;

/**
 * @author dyoma
 */
public interface DiffViewer {
  void setDiffRequest(DiffRequest request);

  JComponent getComponent();

  JComponent getPreferredFocusedComponent();
}
