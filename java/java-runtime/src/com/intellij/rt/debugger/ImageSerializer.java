// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ImageSerializer {
  public static String imageToBytes(Image image) throws IOException {
    //noinspection UndesirableClassUsage
    BufferedImage bi = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = bi.createGraphics();
    g.drawImage(image, 0, 0, null);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(bi, "png", baos);
    g.dispose();
    return new String(baos.toByteArray(), StandardCharsets.ISO_8859_1);
  }

  public static String iconToBytesPreview(Icon icon, int maxSize) throws IOException {
    if (icon.getIconHeight() <= maxSize && icon.getIconWidth() <= maxSize) {
      return imageToBytes(toImage(icon));
    }
    return null;
  }

  /** @noinspection unused*/
  public static String iconToBytesPreviewNormal(Icon icon) throws IOException {
    return iconToBytesPreview(icon, 16);
  }

  /** @noinspection unused*/
  public static String iconToBytesPreviewRetina(Icon icon) throws IOException {
    return iconToBytesPreview(icon, 32);
  }

  /** @noinspection unused*/
  public static String iconToBytes(Icon icon) throws IOException {
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
      BufferedImage image;
      try {
        image = GraphicsEnvironment.getLocalGraphicsEnvironment()
          .getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(w, h, Transparency.TRANSLUCENT);
      }
      catch (HeadlessException e) {
        //noinspection UndesirableClassUsage
        image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      }
      final Graphics2D g = image.createGraphics();
      icon.paintIcon(null, g, 0, 0);
      g.dispose();
      return image;
    }
  }

}
