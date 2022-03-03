// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleType;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.html.ImageView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;

final class DocumentationScalingImageView extends ImageView {

  private final @NotNull Supplier<? extends @NotNull ScaleContext> myScaleContextSupplier;

  DocumentationScalingImageView(Element elem, @NotNull Supplier<? extends @NotNull ScaleContext> scaleContextSupplier) {
    super(elem);

    myScaleContextSupplier = scaleContextSupplier;
  }

  @Override
  public float getMaximumSpan(int axis) {
    return super.getMaximumSpan(axis) / getSysScale();
  }

  @Override
  public float getMinimumSpan(int axis) {
    return super.getMinimumSpan(axis) / getSysScale();
  }

  @Override
  public float getPreferredSpan(int axis) {
    return super.getPreferredSpan(axis) / getSysScale();
  }

  @Override
  public void paint(Graphics g, Shape a) {
    Rectangle bounds = a.getBounds();
    int width = (int)super.getPreferredSpan(View.X_AXIS);
    int height = (int)super.getPreferredSpan(View.Y_AXIS);
    if (width <= 0 || height <= 0) return;
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    super.paint(graphics, new Rectangle(image.getWidth(), image.getHeight()));
    StartupUiUtil.drawImage(g, ImageUtil.ensureHiDPI(image, getScaleContext()), bounds.x, bounds.y, null);
  }

  //TODO: check if we can use getContainer to acquire context
  @NotNull
  private ScaleContext getScaleContext() {
    return myScaleContextSupplier.get();
  }

  private float getSysScale() {
    return (float)getScaleContext().getScale(ScaleType.SYS_SCALE);
  }
}
