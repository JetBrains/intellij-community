// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.icons.DarkIconProvider;
import com.intellij.util.IconUtil;
import com.intellij.util.MathUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBScalableIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

@ApiStatus.Experimental
public class RoundedIcon extends JBScalableIcon implements DarkIconProvider, IconWithToolTip {
  private final Icon mySourceIcon;
  private final double myArcRatio;

  public RoundedIcon(@NotNull Image source, double arcRatio) {
    this(IconUtil.createImageIcon(source), arcRatio);
  }

  public RoundedIcon(@NotNull Icon source, double arcRatio) {
    mySourceIcon = source;
    myArcRatio = MathUtil.clamp(arcRatio, 0, 1);
  }

  @Override
  public @Nullable String getToolTip(boolean composite) {
    return ObjectUtils.doIfCast(mySourceIcon, IconWithToolTip.class, icon -> {
      return icon.getToolTip(composite);
    });
  }

  @Override
  public @NotNull Icon getDarkIcon(boolean isDark) {
    return new RoundedIcon(IconLoader.getDarkIcon(mySourceIcon, isDark), myArcRatio);
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
      g.setTransform(transform);
      g.setPaint(new TexturePaint(bufferedImage, new Rectangle(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight())));
      double actualArc = myArcRatio * Math.min(width, height) * scale;
      g.fill(new RoundRectangle2D.Double(0, 0, getIconWidth() * scale, getIconHeight()* scale, actualArc, actualArc));
    } finally {
      g.dispose();
    }
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
