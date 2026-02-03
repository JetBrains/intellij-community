// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class PanelWithButtons extends JPanel {
  public PanelWithButtons() {
    super(new GridBagLayout());
  }

  protected void initPanel() {
    JComponent mainComponent = createMainComponent();
    JButton[] buttons = createButtons();

    @Nls String labelText = getLabelText();
    if (labelText != null) {
      setBorder(IdeBorderFactory.createTitledBorder(labelText, false));
    }

    add(
        mainComponent,
        new GridBagConstraints(0, 1, 1, buttons.length == 0 ? 1 : buttons.length, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, buttons.length == 0 ? 0 : 4), 0, 0)
    );

    for (int i = 0; i < buttons.length; i++) {
      JButton button = buttons[i];
      add(
          button,
          new GridBagConstraints(1, 1 + i, 1, 1, 0, (i == buttons.length - 1 ? 1 : 0), GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 4, 0), 0, 0)
      );
    }

  }

  protected abstract @Nullable @NlsContexts.Label String getLabelText();

  protected abstract JButton[] createButtons();

  protected abstract JComponent createMainComponent();
}
