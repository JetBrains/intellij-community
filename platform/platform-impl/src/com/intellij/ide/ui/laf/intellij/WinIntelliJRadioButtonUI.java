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

import com.intellij.ide.ui.laf.darcula.ui.DarculaRadioButtonUI;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJRadioButtonUI extends DarculaRadioButtonUI {
  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WinIntelliJRadioButtonUI();
  }

  @Override
  protected void paintIcon(JComponent c, Graphics2D g, Rectangle viewRect, Rectangle iconRect) {
    int rad = JBUI.scale(4);

    // Paint the radio button
    final int x = iconRect.x + (rad - (rad % 2 == 1?1:0))/2;
    final int y = iconRect.y + (rad - (rad % 2 == 1?1:0))/2;
    final int w = iconRect.width - rad;
    final int h = iconRect.height - rad;
    final boolean enabled = c.isEnabled();
    Color color = enabled ? Gray.x50 : Gray.xD3;
    g.translate(x, y);
    //setup AA for lines
    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    final boolean selected = ((AbstractButton)c).isSelected();
    g.setPaint(color);
    g.drawOval(0, 0, w , h );

    if (selected) {
      g.setColor(color);
      g.fillOval(JBUI.scale(3), JBUI.scale(3), w - 2*JBUI.scale(3), h - 2*JBUI.scale(3));
    }
    config.restore();
    g.translate(-x, -y);

  }
}
