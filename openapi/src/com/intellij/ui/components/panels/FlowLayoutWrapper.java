/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.components.panels;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * @author kir
 */
public class FlowLayoutWrapper extends NonOpaquePanel {
  public FlowLayoutWrapper(JComponent wrapped) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0), wrapped);
  }

  public FlowLayoutWrapper(JComponent component, Border border) {
    this(component);
    setBorder(border);
  }
}
