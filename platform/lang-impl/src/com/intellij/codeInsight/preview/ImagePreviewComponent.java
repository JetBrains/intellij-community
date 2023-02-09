// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.preview;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;

/**
 * @deprecated see {@link PreviewHintProvider} deprecation notice
 * Use {@link org.intellij.images.ui.ImageComponent} instead.
 */
@Deprecated(forRemoval = true)
public final class ImagePreviewComponent extends JPanel  {

  private final @NotNull BufferedImage myImage;

  /**
   * @param image         buffered image
   * @param imageFileSize File length in bytes.
   */
  private ImagePreviewComponent(final @NotNull BufferedImage image, final long imageFileSize) {
    setLayout(new BorderLayout());

    myImage = (BufferedImage)ImageUtil.ensureHiDPI(image, ScaleContext.create(this));
    add(new ImageComp(), BorderLayout.CENTER);
    add(createLabel(image, imageFileSize), BorderLayout.SOUTH);

    setBackground(UIUtil.getToolTipBackground());
    setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(JBColor.BLACK), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
  }

  private static @NotNull JLabel createLabel(final @NotNull BufferedImage image, long imageFileSize) {
    final int width = image.getWidth();
    final int height = image.getHeight();
    final ColorModel colorModel = image.getColorModel();
    final int i = colorModel.getPixelSize();
    return new JLabel(LangBundle.message("image.preview.label", width, height, i, StringUtil.formatFileSize(imageFileSize)));
  }

  /**
   * This method doesn't use caching, so if you want to use it then you should consider implementing external cache.
   */
  public static ImagePreviewComponent getPreviewComponent(final @NotNull BufferedImage image, final long imageFileSize) {
    return new ImagePreviewComponent(image, imageFileSize);
  }

  private final class ImageComp extends JComponent {
    private final Dimension myPreferredSize;

    private ImageComp() {
      if (myImage.getWidth() > 300 || myImage.getHeight() > 300) {
        // will make image smaller
        final float factor = 300.0f / Math.max(myImage.getWidth(), myImage.getHeight());
        myPreferredSize = new Dimension((int)(myImage.getWidth() * factor), (int)(myImage.getHeight() * factor));
      }
      else {
        myPreferredSize = new Dimension(myImage.getWidth(), myImage.getHeight());
      }
    }

    @Override
    public void paint(final Graphics g) {
      super.paint(g);
      Rectangle r = getBounds();
      final int width = myImage.getWidth();
      final int height = myImage.getHeight();

      UIUtil.drawImage(g, myImage, new Rectangle(0, 0, Math.min(r.width, width), Math.min(r.height, height)), null, this);
    }

    @Override
    public Dimension getPreferredSize() {
      return myPreferredSize;
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }
  }
}
