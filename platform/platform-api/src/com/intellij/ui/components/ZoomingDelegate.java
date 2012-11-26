/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.ui.components;

import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
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
    if (myCachedImage != null && myMagnificationPoint != null && myMagnification != 0) {
      double scale = magnificationToScale(myMagnification);
      int xoffset = (int)(myMagnificationPoint.x - myMagnificationPoint.x * scale);
      int yoffset = (int)(myMagnificationPoint.y - myMagnificationPoint.y * scale);

      Rectangle clip = g.getClipBounds();

      g.setColor(Gray._120);
      g.fillRect(clip.x, clip.y, clip.width, clip.height);

      Graphics2D translated = (Graphics2D)g.create();
      translated.translate(xoffset, yoffset);
      translated.scale(scale, scale);

      translated.drawImage(myCachedImage, 0, 0, null);
    }
  }

  public void magnificationStarted(Point at) {
    myMagnificationPoint = at;
  }

  public void magnificationFinished(double magnification) {
    if (myMagnification != 0) {
      Magnificator magnificator = ((ZoomableViewport)myViewportComponent).getMagnificator();

      if (magnificator != null) {
        Point inContent = convertToContentCoordinates(myMagnificationPoint);

        final Point inContentScaled = magnificator.magnify(magnificationToScale(magnification), inContent);

        int voffset = inContentScaled.y - myMagnificationPoint.y;
        int hoffset = inContentScaled.x - myMagnificationPoint.x;
        myViewportComponent.repaint();
        myViewportComponent.validate();

        scrollTo(voffset, hoffset);
      }
    }

    myMagnificationPoint = null;
    myMagnification = 0;
    myCachedImage = null;
  }

  protected void scrollTo(int voffset, int hoffset) {
    JViewport viewport = (JViewport)myViewportComponent;
    JScrollPane pane = (JScrollPane)viewport.getParent();
    JScrollBar vsb = pane.getVerticalScrollBar();
    vsb.setValue(voffset);
    JScrollBar hsb = pane.getHorizontalScrollBar();
    hsb.setValue(hoffset);
  }
  
  protected Point convertToContentCoordinates(Point point) {
    return SwingUtilities.convertPoint(myViewportComponent, point, myContentComponent);
  }

  public boolean isActive() {
    return myCachedImage != null;
  }

  private static double magnificationToScale(double magnification) {
    return magnification < 0 ? 1f / (1 - magnification) : (1 + magnification);
  }

  public void magnify(double magnification) {
    if (myMagnification != magnification) {
      myMagnification = magnification;

      if (myCachedImage == null) {
        Rectangle bounds = myViewportComponent.getBounds();
        BufferedImage image = UIUtil.createImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB);

        Graphics graphics = image.getGraphics();
        graphics.setClip(0, 0, bounds.width, bounds.height);
        myViewportComponent.paint(graphics);

        myCachedImage = image;
      }
    }
    myViewportComponent.repaint();
  }
}
