// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;

/**
 * @author Konstantin Bulenkov
 */
public class ScratchFileTypeIcon extends LayeredIcon {
  public ScratchFileTypeIcon(Icon fileTypeIcon) {
    super(2);
    setIcon(fileTypeIcon, 0);
    setIcon(AllIcons.Actions.Scratch, 1);
  }

  @SuppressWarnings("GraphicsSetClipInspection")
  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    if (!ExperimentalUI.isNewUI()) {
      super.paintIcon(c, g, x, y);
      return;
    }
    float scale = getScale();
    double r = scale * 4.5;
    Icon badge = getIcon(1);
    assert badge != null;
    double w = badge.getIconWidth();
    Shape clip = g.getClip();
    Area newClip = new Area(clip);
    newClip.subtract(new Area(new Ellipse2D.Double(x + w - 2 * r, y, 2 * r, 2 * r)));
    ((Graphics2D)g).setClip(newClip);
    super.paintIcon(c, g, x, y);
    ((Graphics2D)g).setClip(clip);
    if (scale != 1f) {
      badge = IconUtil.scale(badge, null, scale);
    }
    badge.paintIcon(c, g, x, y);
  }
}
