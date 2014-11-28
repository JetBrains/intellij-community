package com.intellij.openapi.util.diff.impl;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ModifiablePanel extends JPanel {
  public ModifiablePanel() {
    super(new BorderLayout());
  }

  public void setContent(@Nullable JComponent content) {
    removeAll();
    if (content != null) add(content, BorderLayout.CENTER);
    invalidate();
  }
}
