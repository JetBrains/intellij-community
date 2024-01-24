// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * @author Konstantin Bulenkov
 * @deprecated unused in ide.navBar.v2. If you do a change here, please also update v2 implementation
 */
@Deprecated(forRemoval = true)
public abstract class AbstractNavBarUI {

  @Internal
  public static void paintHighlight(@NotNull Graphics2D g, @NotNull Rectangle rectangle, @NotNull Color color) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

      g2.setColor(color);

      float arc = JBUIScale.scale(4);
      RoundRectangle2D shape = new RoundRectangle2D.Float(rectangle.x, rectangle.y, rectangle.width, rectangle.height, arc, arc);
      g2.fill(shape);
    }
    finally {
      g2.dispose();
    }
  }

  @Internal
  public static BufferedImage drawToBuffer(
    @NotNull Component item,
    ScaleContext ctx,
    boolean floating,
    boolean toolbarVisible,
    boolean selected,
    boolean nextSelected,
    boolean isLastElement
  ) {
    int w = item.getWidth();
    int h = item.getHeight();
    int offset = (w - getDecorationOffset());
    int h2 = h / 2;

    BufferedImage result = ImageUtil.createImage(ctx, w, h, BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.FLOOR);

    Color defaultBg = StartupUiUtil.isUnderDarcula() ? Gray._100 : JBColor.WHITE;
    final Paint bg = floating ? defaultBg : null;
    final Color selection = UIUtil.getListSelectionBackground(true);

    Graphics2D g2 = result.createGraphics();
    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);


    Path2D.Double shape = new Path2D.Double();
    shape.moveTo(0, 0);

    shape.lineTo(offset, 0);
    shape.lineTo(w, h2);
    shape.lineTo(offset, h);
    shape.lineTo(0, h);
    shape.closePath();

    Path2D.Double endShape = new Path2D.Double();
    endShape.moveTo(offset, 0);
    endShape.lineTo(w, 0);
    endShape.lineTo(w, h);
    endShape.lineTo(offset, h);
    endShape.lineTo(w, h2);
    endShape.closePath();

    if (bg != null && toolbarVisible) {
      g2.setPaint(bg);
      g2.fill(shape);
      g2.fill(endShape);
    }

    if (selected) {
      Path2D.Double focusShape = new Path2D.Double();
      if (toolbarVisible || floating) {
        focusShape.moveTo(offset, 0);
      } else {
        focusShape.moveTo(0, 0);
        focusShape.lineTo(offset, 0);
      }
      focusShape.lineTo(w - 1, h2);
      focusShape.lineTo(offset, h - 1);
      if (!toolbarVisible && !floating) {
        focusShape.lineTo(0, h - 1);

      }

      g2.setColor(selection);
      if (floating && isLastElement) {
        g2.fillRect(0, 0, w, h);
      } else {
        g2.fill(shape);
      }
    }

    if (nextSelected) {
      g2.setColor(selection);
      g2.fill(endShape);
    }

    if (!isLastElement) {
      if (!selected && !nextSelected) {
        Icon icon = AllIcons.Ide.NavBarSeparator;
        icon.paintIcon(item, g2, w - icon.getIconWidth() - JBUIScale.scale(1), h2 - icon.getIconHeight() / 2);
      }
    }

    g2.dispose();
    return result;
  }

  @Internal
  public static int getDecorationOffset() {
    return JBUIScale.scale(8);
  }

  @Internal
  public static int getFirstElementLeftOffset() {
    return JBUIScale.scale(6);
  }
}
