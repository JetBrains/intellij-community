// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class TagComponent extends LinkComponent {
  private static final Color BACKGROUND = JBColor.namedColor("Plugins.tagBackground", new JBColor(0xEAEAEC, 0x4D4D4D));
  private static final Color EAP_BACKGROUND = JBColor.namedColor("Plugins.eapTagBackground", new JBColor(0xF2D2CF, 0xF2D2CF));
  private static final Color FOREGROUND = JBColor.namedColor("Plugins.tagForeground", new JBColor(0x787878, 0x999999));

  private final Color myColor;

  public TagComponent(@NotNull String name) {
    boolean eap = "EAP".equals(name);
    myColor = eap ? EAP_BACKGROUND : BACKGROUND;
    setText(name);
    if (eap) {
      setToolTipText("The EAP version does not guarantee the stability\nand availability of the plugin.");
    }
    setForeground(FOREGROUND);
    setPaintUnderline(false);
    setOpaque(false);
    setBorder(JBUI.Borders.empty(1, 8));
  }

  @Override
  protected void paintComponent(Graphics g) {
    //noinspection UseJBColor
    g.setColor(myUnderline ? new Color(myColor.getRed(), myColor.getGreen(), myColor.getBlue(), 178) : myColor);
    g.fillRect(0, 0, getWidth(), getHeight());
    super.paintComponent(g);
  }

  @Override
  protected boolean isInClickableArea(Point pt) {
    return true;
  }
}