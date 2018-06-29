// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonPainter;
import com.intellij.ui.JBColor;
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
    boolean defButton = isDefaultButton(b);

    return button.isEnabled() ? borderColor != null ? borderColor :
      button.hasFocus() ?
        JBColor.namedColor(defButton ? "Button.darcula.defaultFocusedOutlineColor" : "Button.darcula.focusedOutlineColor", 0x87afda) :
        new GradientPaint(0, 0,
                          JBColor.namedColor(defButton ? "Button.darcula.outlineDefaultStartColor" : "Button.darcula.outlineStartColor", 0xbfbfbf),
                          0, r.height,
                          JBColor.namedColor(defButton ? "Button.darcula.outlineDefaultEndColor" : "Button.darcula.outlineEndColor", 0xb8b8b8))
      : JBColor.namedColor("Button.darcula.disabledOutlineColor", 0xcfcfcf);
  }
}
