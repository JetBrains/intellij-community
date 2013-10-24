/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;

import javax.swing.border.Border;
import javax.swing.plaf.InsetsUIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaEditorTextFieldBorder implements Border {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    final EditorTextField textField = UIUtil.getParentOfType(EditorTextField.class, c);
    if (textField == null) return;

    final int x1 = x + 3;
    final int y1 = y + 3;
    final int width1 = width - 8;
    final int height1 = height - 6;

    if (c.isOpaque()) {
      g.setColor(UIUtil.getPanelBackground());
      g.fillRect(x, y, width, height);
    }

    g.setColor(c.getBackground());
    g.fillRect(x1, y1, width1, height1);

    if (!textField.isEnabled()) {
      ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
    }

    if (textField.isEnabled() && textField.isVisible() && textField.getFocusTarget().hasFocus()) {
      DarculaUIUtil.paintFocusRing(g, x1, y1, width1, height1);
    } else {
      g.setColor(new JBColor(Gray._150, Gray._100));
      g.drawRect(x1, y1, width1, height1);
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return new InsetsUIResource(6, 7, 6, 7);
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }
}
