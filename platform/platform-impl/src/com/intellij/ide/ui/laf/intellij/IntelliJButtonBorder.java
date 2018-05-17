// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonPainter;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBInsets;

import javax.swing.*;
import java.awt.*;

import static com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.isDefaultButton;

public class IntelliJButtonBorder extends DarculaButtonPainter {

  @SuppressWarnings("UseJBColor")
  @Override
  public Paint getBorderPaint(Component button) {
    AbstractButton b = (AbstractButton)button;
    Color borderColor = (Color)b.getClientProperty("JButton.borderColor");
    Rectangle r = new Rectangle(b.getSize());
    JBInsets.removeFrom(r, b.getInsets());

    return button.isEnabled() ? borderColor != null ? borderColor :
        button.hasFocus() ?
        UIManager.getColor(isDefaultButton(b) ? "Button.darcula.defaultFocusedOutlineColor" : "Button.darcula.focusedOutlineColor") :
        isDefaultButton(b) ?
          new GradientPaint(0, 0, new Color(0x4779ba), 0, r.height, new Color(0x3167ad)) :
          new GradientPaint(0, 0, Gray.xBF, 0, r.height, Gray.xB8)
      : UIManager.getColor("Button.darcula.disabledOutlineColor");
  }
}
