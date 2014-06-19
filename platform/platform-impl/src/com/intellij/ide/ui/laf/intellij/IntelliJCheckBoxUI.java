/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
    g.setStroke(new BasicStroke(1 *2.0f, BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));

    g.setPaint(getShadowColor(enabled, true));
    g.drawLine(5, 9, 7, 11);
    g.drawLine(7, 11, w-3, 5);
    g.setPaint(getCheckSignColor(enabled, true));
    g.drawLine(5, 7, 7, 9);
    g.drawLine(7, 9, w-3, 3);
  }
}
