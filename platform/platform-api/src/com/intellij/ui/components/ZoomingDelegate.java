// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public class ZoomingDelegate {
  private final JComponent myContentComponent;
  private final JComponent myViewportComponent;

  private BufferedImage myCachedImage;
  private Point myMagnificationPoint;
  private double myMagnification;

  public ZoomingDelegate(JComponent contentComponent, JComponent viewportComponent) {
    myContentComponent = contentComponent;
    myViewportComponent = viewportComponent;
  }

  public void paint(Graphics g) {
    if (myCachedImage != null && myMagnificationPoint != null) {
      double scale = magnificationToScale(myMagnification);
      int xOffset = (int)(myMagnificationPoint.x - myMagnificationPoint.x * scale);
      int yOffset = (int)(myMagnificationPoint.y - myMagnificationPoint.y * scale);

      Rectangle clip = g.getClipBounds();

      g.setColor(myContentComponent.getBackground());
      g.fillRect(clip.x, clip.y, clip.width, clip.height);

      Graphics2D translated = (Graphics2D)g.create();
      translated.translate(xOffset, yOffset);
      translated.scale(scale, scale);

      UIUtil.drawImage(translated, myCachedImage, 0, 0, null);
    }
  }

  public void magnificationStarted(@NotNull Point at) {
    myMagnificationPoint = at;
  }

  public void magnificationFinished(double magnification) {
    Magnificator magnificator = ((ZoomableViewport)myViewportComponent).getMagnificator();

    if (magnificator != null && Double.compare(magnification, 0) != 0) {
      Point inContent = convertToContentCoordinates(myMagnificationPoint);

      final Point inContentScaled = magnificator.magnify(magnificationToScale(magnification), inContent);

      int vOffset = inContentScaled.y - myMagnificationPoint.y;
      int hOffset = inContentScaled.x - myMagnificationPoint.x;
      myViewportComponent.repaint();
      myViewportComponent.validate();

      scrollTo(vOffset, hOffset);
    }

    myMagnificationPoint = null;
    myMagnification = 0;
    myCachedImage = null;
  }

  protected void scrollTo(int vOffset, int hOffset) {
    JScrollPane pane = ComponentUtil.getScrollPane(myViewportComponent);
    JScrollBar vsb = pane == null ? null : pane.getVerticalScrollBar();
    if (vsb != null) vsb.setValue(vOffset);
    JScrollBar hsb = pane == null ? null : pane.getHorizontalScrollBar();
    if (hsb != null) hsb.setValue(hOffset);
  }

  protected Point convertToContentCoordinates(Point point) {
    return SwingUtilities.convertPoint(myViewportComponent, point, myContentComponent);
  }

  public boolean isActive() {
    return myCachedImage != null;
  }

  protected static double magnificationToScale(double magnification) {
    return magnification < 0 ? 1f / (1 - magnification) : (1 + magnification);
  }

  public void magnify(double magnification) {
    double prev = myMagnification;
    myMagnification = magnification;

    if (myCachedImage == null) {
      Rectangle bounds = myViewportComponent.getBounds();
      if (bounds.width <= 0 || bounds.height <= 0) return;

      BufferedImage image =
        ImageUtil.createImage(GraphicsUtil.safelyGetGraphics(myViewportComponent), bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB);

      Graphics graphics = image.getGraphics();
      graphics.setClip(0, 0, bounds.width, bounds.height);
      myViewportComponent.paint(graphics);

      myCachedImage = image;
    }
    if (Double.compare(prev, magnification) != 0) {
      myViewportComponent.repaint();
    }
  }
}
