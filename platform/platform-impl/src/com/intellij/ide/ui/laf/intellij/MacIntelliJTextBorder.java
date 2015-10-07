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

import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJTextBorder extends DarculaTextBorder {
  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(3, 6).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  @Override
  public void paintBorder(Component c, Graphics g2d, int x, int y, int width, int height) {
    if (TextFieldWithPopupHandlerUI.isSearchField(c)) {
      return;
    }
    Graphics2D g = (Graphics2D)g2d;
    //todo[kb]: make a better solution
    if (c.getParent() instanceof JComboBox) return;
    if (c.hasFocus()) {
      MacIntelliJBorderPainter.paintBorder(c, g, 0, 0, c.getWidth(), c.getHeight());
    }
    if (!IntelliJLaf.isGraphite() || !c.hasFocus()) {
      g.setColor(Gray.xB4);
      g.drawRect(3, 3, c.getWidth() - 6, c.getHeight() - 6);
    }
  }
}
