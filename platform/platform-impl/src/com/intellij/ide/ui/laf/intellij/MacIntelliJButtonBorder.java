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

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;

import static com.intellij.ide.ui.laf.intellij.MacIntelliJButtonUI.ARC_SIZE;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJButtonBorder implements Border, UIResource {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (!c.hasFocus() ||
        c instanceof JComponent && DarculaButtonUI.isHelpButton((JComponent)c)) return;

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.translate(x, y);
      DarculaUIUtil.paintFocusBorder(g2, width, height, ARC_SIZE, true);
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(3).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
