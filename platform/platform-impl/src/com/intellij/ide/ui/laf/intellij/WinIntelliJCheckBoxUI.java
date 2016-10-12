/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJCheckBoxUI extends IntelliJCheckBoxUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WinIntelliJCheckBoxUI();
  }

  @Override
  protected void drawCheckIcon(JComponent c, Graphics2D g, JCheckBox b, Rectangle iconRect, boolean selected, boolean enabled) {
    final Color color = enabled ? b.getForeground() : getBorderColor1(false, false);
    g.setColor(color);

    Rectangle r = new Rectangle(iconRect.x + JBUI.scale(2), iconRect.y + JBUI.scale(2), iconRect.width - JBUI.scale(4), iconRect.height - JBUI.scale(4));
    g.drawRect(r.x, r.y, r.width, r.height);

    if (selected) {
      final int x1 = r.x + JBUI.scale(3);
      final int y1 = r.y + r.height / 2 + JBUI.scale(1);
      final int x2 = r.x + r.height / 2 - JBUI.scale(1);
      final int y2 = r.y + r.height - JBUI.scale(4);

      final Graphics2D iconGraphics = (Graphics2D)g.create(0, 0, c.getWidth(), c.getHeight());
      iconGraphics.setColor(color);
      iconGraphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      iconGraphics.setStroke(new BasicStroke(JBUI.scale(1.5f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

      if (enabled) {
        iconGraphics.drawLine(x1, y1, x2, y2);
        iconGraphics.drawLine(x2, y2, r.x + r.width - JBUI.scale(2), r.y + JBUI.scale(4));
      }
      iconGraphics.dispose();
    }
  }

  @Override
  protected void drawText(JComponent c, Graphics2D g, JCheckBox b, FontMetrics fm, Rectangle textRect, String text) {
    super.drawText(c, g, b, fm, textRect, text);
    if (b.hasFocus()) {
      g.setColor(b.getForeground());
      UIUtil.drawDottedRectangle(g, textRect.x - 2, textRect.y - 1, textRect.width + textRect.x + 1, textRect.height + 3);
    }
  }

  @Override
  public Icon getDefaultIcon() {
    return JBUI.emptyIcon(18).asUIResource();
  }
}
