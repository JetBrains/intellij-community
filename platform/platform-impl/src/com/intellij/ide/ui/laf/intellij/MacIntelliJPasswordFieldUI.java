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

import com.intellij.ide.ui.laf.darcula.ui.DarculaPasswordFieldUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJPasswordFieldUI extends DarculaPasswordFieldUI {

  public MacIntelliJPasswordFieldUI(JPasswordField c) {
    super(c);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJPasswordFieldUI((JPasswordField)c);
  }

  @Override
  protected void paintSafely(Graphics g) {
    super.paintSafely(g);
  }

  @Override
  protected void paintBackground(Graphics graphics) {
    Graphics2D g = (Graphics2D)graphics;
    final JTextComponent c = getComponent();
    final Container parent = c.getParent();
    if (c.isOpaque() && parent != null) {
      g.setColor(parent.getBackground());
      g.fillRect(0, 0, c.getWidth(), c.getHeight());
    }

    if (c.getBorder() instanceof MacIntelliJTextBorder) {
      g.setColor(c.getBackground());
      g.fillRect(3, 3, c.getWidth() - 6, c.getHeight() - 6);
    } else {
      super.paintBackground(g);
    }
  }

  @NotNull
  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    return new Dimension(size.width, Math.max(26, size.height));
  }
}
