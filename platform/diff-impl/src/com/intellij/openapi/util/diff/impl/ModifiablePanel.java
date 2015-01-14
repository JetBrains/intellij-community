package com.intellij.openapi.util.diff.impl;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ModifiablePanel extends JPanel {
  @Nullable private JComponent myContent;

  public ModifiablePanel() {
    super(new BorderLayout());
  }

  public void setContent(@Nullable JComponent content) {
    myContent = content;
    removeAll();
    if (content != null) add(content, BorderLayout.CENTER);
    invalidate();
  }

  @Nullable
  public JComponent getContent() {
    return myContent;
  }
}
