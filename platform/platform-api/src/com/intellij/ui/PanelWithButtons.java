/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

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

    String labelText = getLabelText();
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

  @Nullable
  protected abstract String getLabelText();

  protected abstract JButton[] createButtons();

  protected abstract JComponent createMainComponent();
}
