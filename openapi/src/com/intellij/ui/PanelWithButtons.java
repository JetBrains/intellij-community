/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.*;

public abstract class PanelWithButtons extends JPanel {
  public PanelWithButtons() {
    super(new GridBagLayout());
  }

  protected void initPanel() {
    JComponent mainComponent = createMainComponent();
    JButton[] buttons = createButtons();

    String labelText = getLabelText();
    if (labelText != null) {
      setBorder(BorderFactory.createTitledBorder(new EtchedBorder(), labelText));
    }

    add(
        mainComponent,
        new GridBagConstraints(0, 1, 1, buttons.length, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 4), 0, 0)
    );

    for (int i = 0; i < buttons.length; i++) {
      JButton button = buttons[i];
      add(
          button,
          new GridBagConstraints(1, 1 + i, 1, 1, 0, (i == buttons.length - 1 ? 1 : 0), GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 4, 0), 0, 0)
      );
    }

  }

  protected abstract String getLabelText();

  protected abstract JButton[] createButtons();

  protected abstract JComponent createMainComponent();
}
