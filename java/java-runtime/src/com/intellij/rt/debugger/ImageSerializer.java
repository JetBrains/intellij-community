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
package com.intellij.rt.debugger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author egor
 */
public class ImageSerializer {
  public static byte[] imageToBytes(Image image) throws IOException {
    //noinspection UndesirableClassUsage
    BufferedImage bi = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = bi.createGraphics();
    g.drawImage(image, 0, 0, null);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(bi, "png", baos);
    g.dispose();
    return baos.toByteArray();
  }

  public static byte[] iconToBytesPreview(Icon icon, int maxSize) throws IOException {
    if (icon.getIconHeight() <= maxSize && icon.getIconWidth() <= maxSize) {
      return imageToBytes(toImage(icon));
    }
    return null;
  }

  /** @noinspection unused*/
  public static byte[] iconToBytesPreviewNormal(Icon icon) throws IOException {
    return iconToBytesPreview(icon, 16);
  }

  /** @noinspection unused*/
  public static byte[] iconToBytesPreviewRetina(Icon icon) throws IOException {
    return iconToBytesPreview(icon, 32);
  }

  /** @noinspection unused*/
  public static byte[] iconToBytes(Icon icon) throws IOException {
    return imageToBytes(toImage(icon));
  }

  // copied from IconUtils
  private static Image toImage(Icon icon) {
    if (icon instanceof ImageIcon) {
      return ((ImageIcon)icon).getImage();
    }
    else {
      final int w = icon.getIconWidth();
      final int h = icon.getIconHeight();
      final BufferedImage image = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(w, h, Transparency.TRANSLUCENT);
      final Graphics2D g = image.createGraphics();
      icon.paintIcon(null, g, 0, 0);
      g.dispose();
      return image;
    }
  }

}
