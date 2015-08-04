/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ide.ui.laf.darcula.ui.DarculaRadioButtonUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJRadioButtonUI extends DarculaRadioButtonUI {
  private static final Icon RADIO = DarculaLaf.loadIcon("radio.png");
  private static final Icon RADIO_FOCUSED = DarculaLaf.loadIcon("radioFocused.png");
  private static final Icon RADIO_SELECTED = DarculaLaf.loadIcon("radioSelected.png");
  private static final Icon RADIO_SELECTED_FOCUSED = DarculaLaf.loadIcon("radioSelectedFocused.png");
  private static final Icon RADIO_DISABLED = DarculaLaf.loadIcon("radioDisabled.png");
  private static final Icon RADIO_DISABLED_SELECTED = DarculaLaf.loadIcon("radioDisabledSelected.png");

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJRadioButtonUI();
  }

  @Override
  protected void paintIcon(JComponent c, Graphics2D g, Rectangle viewRect, Rectangle iconRect) {
    boolean enabled = c.isEnabled();
    boolean focused = c.hasFocus();
    boolean selected = ((AbstractButton)c).isSelected();
    Icon icon;
    if (enabled) {
      if (selected) {
        icon = focused ? RADIO_SELECTED_FOCUSED : RADIO_SELECTED;
      } else {
        icon = focused ? RADIO_FOCUSED : RADIO;
      }
    } else {
      icon = selected ? RADIO_DISABLED_SELECTED : RADIO_DISABLED;
    }
    icon.paintIcon(c, g, iconRect.x, iconRect.y);
  }

  @Override
  public Icon getDefaultIcon() {
    return RADIO;
  }
}
