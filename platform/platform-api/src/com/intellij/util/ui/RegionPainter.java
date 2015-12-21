/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.ui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * The {@code RegionPainter} interface defines exactly one method, {@code paint}.
 * It is used in situations where the developer can change the painting routine
 * of a component without having to resort to subclassing the component.
 * It is also generically useful when doing any form of painting delegation.
 * <p>
 * Painters are simply encapsulations of Java2D code and make it fairly trivial
 * to reuse existing painters or to combine them together.
 * Implementations of this interface are also trivial to write,
 * such that if you can't find a painter that does what you need,
 * you can write one with minimal effort.
 *
 * @author Sergey.Malenkov
 * @see com.sun.java.swing.Painter
 */
public interface RegionPainter<T> {
  /**
   * Renders to the given {@link Graphics2D} object.
   *
   * @param g      the {@code Graphics2D} object to render to
   * @param x      X position of the area to paint
   * @param y      Y position of the area to paint
   * @param width  width of the area to paint
   * @param height height of the area to paint
   * @param object an optional configuration parameter
   */
  void paint(Graphics2D g, int x, int y, int width, int height, T object);

  /**
   * This class provides a caching functionality for a region painter.
   */
  class Image implements RegionPainter<Object> {
    private BufferedImage myImage;

    /**
     * This method is called for the cached image before {@code drawImage}.
     * It should be overridden if the image must be updated without creating a new image.
     *
     * @param image the cached image to update
     */
    protected void updateImage(BufferedImage image) {
    }

    /**
     * This method is called if the cached image is invalidated or it's size is changed.
     * It must be overridden to paint on newly created image.
     *
     * @param width  width of the new image
     * @param height height of the new image
     * @return new {@link BufferedImage} object
     */
    protected BufferedImage createImage(int width, int height) {
      return UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * This method is called to invalidate the cached image.
     */
    protected void invalidate() {
      myImage = null;
    }

    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Object object) {
      if (width > 0 && height > 0) {
        if (myImage == null || width != myImage.getWidth() || height != myImage.getHeight()) {
          myImage = createImage(width, height);
        }
        else if (myImage != null) {
          updateImage(myImage);
        }
        if (myImage != null) {
          UIUtil.drawImage(g, myImage, null, x, y);
        }
      }
    }
  }
}
