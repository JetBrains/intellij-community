/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxUI;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class IntelliJCheckBoxUI extends DarculaCheckBoxUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new IntelliJCheckBoxUI();
  }

  @Override
  protected void paintCheckSign(Graphics2D g, boolean enabled, int w, int h) {
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g.setStroke(new BasicStroke(JBUI.scale(1) *2.0f, BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));

    final int x1 = JBUI.scale(5);
    final int y1 = JBUI.scale(9);
    final int x2 = JBUI.scale(7);
    final int y2 = (int)JBUI.scale(11.2f);
    if (enabled) {
      g.setPaint(getShadowColor(true, true));
      g.drawLine(x1, y1, x2, y2);
      g.drawLine(x2, y2, w - JBUI.scale(2) - 1, JBUI.scale(5));
    }
    g.setPaint(getCheckSignColor(enabled, true));
    g.drawLine(x1, y1 - 2, x2, y2 - 2);
    g.drawLine(x2, y2 - 2, w - JBUI.scale(2) - 1, JBUI.scale(5) - 2);
  }

  @Override
  protected boolean fillBackgroundForIndeterminateSameAsForSelected() {
    return true;
  }
}
