// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.util;

import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Panel which occupies horizontal and vertical space even if it's content is invisible.
 */
public final class InsetsPanel extends JPanel {

  private final @NotNull JComponent myContent;

  public InsetsPanel(@NotNull JComponent content) {
    super(new GridBagLayout());
    setOpaque(false);
    myContent = content;
    add(myContent, new GridBag().fillCell().weightx(1).weighty(1));
  }

  @Override
  public Dimension getPreferredSize() {
    return myContent.getPreferredSize();
  }

  @Override
  public Dimension getMinimumSize() {
    return myContent.getMinimumSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return myContent.getMaximumSize();
  }
}
