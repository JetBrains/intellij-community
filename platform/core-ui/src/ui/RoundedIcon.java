// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.icons.DarkIconProvider;
import com.intellij.util.IconUtil;
import com.intellij.util.MathUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBScalableIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

@ApiStatus.Experimental
public final class RoundedIcon extends JBScalableIcon implements DarkIconProvider, IconWithToolTip {
  private final Icon mySourceIcon;
  private final double myArcRatio;
  private final boolean mySuperEllipse;
  private Shape myLastShape = null;
  private int myLastShapeHash = 0;

  public RoundedIcon(@NotNull Image source, double arcRatio) {
    this(source, arcRatio, false);
  }

  public RoundedIcon(@NotNull Icon source, double arcRatio) {
    this(source, arcRatio, false);
  }

  public RoundedIcon(@NotNull Image source, double arcRatio, boolean superEllipse) {
    this(IconUtil.createImageIcon(source), arcRatio, superEllipse);
  }

  public RoundedIcon(@NotNull Icon source, double arcRatio, boolean superEllipse) {
    mySourceIcon = source;
    myArcRatio = MathUtil.clamp(arcRatio, 0, 1);
    mySuperEllipse = superEllipse;
  }

  @Override
  public @Nullable String getToolTip(boolean composite) {
    return ObjectUtils.doIfCast(mySourceIcon, IconWithToolTip.class, icon -> {
      return icon.getToolTip(composite);
    });
  }

  @Override
  public @NotNull Icon getDarkIcon(boolean isDark) {
    return new RoundedIcon(IconLoader.getDarkIcon(mySourceIcon, isDark), myArcRatio, mySuperEllipse);
  }

  @Override
  public void paintIcon(Component c, Graphics graphics, int x, int y) {
    Graphics2D g = (Graphics2D)graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int width = mySourceIcon.getIconWidth();
      int height = mySourceIcon.getIconHeight();
      Image image = IconLoader.toImage(mySourceIcon);
      if (image == null) return;
      BufferedImage bufferedImage = ImageUtil.toBufferedImage(image);
      double scale = (double)bufferedImage.getWidth() / width;
      AffineTransform transform = g.getTransform();
      transform.concatenate(AffineTransform.getScaleInstance(1d/scale, 1d/scale));
      transform.concatenate(AffineTransform.getTranslateInstance(x * scale, y * scale));
      g.setTransform(transform);
      g.setPaint(new TexturePaint(bufferedImage, new Rectangle(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight())));
      int hash = Objects.hash(x, y, getIconWidth(), getIconHeight(), scale, myArcRatio);
      if (myLastShapeHash != hash) {
        if (mySuperEllipse) {
          myLastShape = getSuperEllipse(0, 0, getIconWidth() * scale, getIconHeight() * scale, myArcRatio);
        } else {
          double actualArc = myArcRatio * Math.min(width, height) * scale;
          myLastShape = new RoundRectangle2D.Double(0, 0, getIconWidth() * scale, getIconHeight() * scale, actualArc, actualArc);
        }
        myLastShapeHash = hash;
      }
      g.fill(myLastShape);
      if (mySuperEllipse && Boolean.getBoolean("idea.debug.rounded.icon.mode")) {
        printDebugMessage(g, x, y, "n=" + String.format("%.2f", getPower(myArcRatio)), scale);
      }
    } finally {
      g.dispose();
    }
  }

  private void printDebugMessage(Graphics2D g, int x, int y, String s, double scale) {
    g.setFont(JBUI.Fonts.label().biggerOn(20));
    Rectangle2D bounds = g.getFontMetrics().getStringBounds(s, g);
    g.setColor(Color.WHITE);
    float xx = (float)(getIconWidth() - bounds.getWidth()) / 2;
    float yy = (float)(getIconHeight() - bounds.getHeight());
    for (int dx = -1; dx<=1; dx++) {
      for (int dy = -1; dy<=1; dy++) {
        g.drawString(s, x + (float)scale * xx + dx, y + (float)scale * yy + dy);
      }
    }
    g.setColor(Color.BLACK);
    g.drawString(s, x + (float)scale * xx, y + (float)scale * yy);
  }

  private static Shape getSuperEllipse(double x, double y, double width, double height, double arcRatio) {
    GeneralPath path = new GeneralPath();
    path.moveTo(1, 0);
    double step = Math.PI / 360;
    double n = getPower(arcRatio);
    for (double theta = step; theta <= 2 * Math.PI; theta += step) {
      path.lineTo(
        Math.pow(Math.abs(Math.cos(theta)), 2d/n) * Math.signum(Math.cos(theta)),
        Math.pow(Math.abs(Math.sin(theta)), 2d/n) * Math.signum(Math.sin(theta)));
    }
    path.lineTo(1, 0);
    path.closePath();
    AffineTransform transform = AffineTransform.getScaleInstance(width /2 , height / 2);
    transform.preConcatenate(AffineTransform.getTranslateInstance(x + width/2, y + height/2));
    path.transform(transform);
    return path;
  }

  private static double getPower(double arcRatio) {
    return 100d - 98d * Math.pow(arcRatio, .1);//1->2, 0->100
  }

  @Override
  public int getIconWidth() {
    return mySourceIcon.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return mySourceIcon.getIconHeight();
  }


  @Override
  public int hashCode() {
    return Objects.hash(mySourceIcon, myArcRatio);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RoundedIcon icon = (RoundedIcon)o;
    return myArcRatio == icon.myArcRatio && mySourceIcon.equals(icon.mySourceIcon);
  }

  @Override
  public String toString() {
    return "RoundedIcon(" + mySourceIcon + ", r=" + myArcRatio + ")";
  }
}
