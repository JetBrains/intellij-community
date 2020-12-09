// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.util.Computable;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBImageIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static com.intellij.ui.scale.ScaleType.USR_SCALE;

/**
 * @author Alexander Lobas
 */
public final class HiDPIPluginLogoIcon extends PluginLogoIcon {
  private static Icon myCachedErrorLogo2x;

  HiDPIPluginLogoIcon(@NotNull Icon logo, @NotNull Icon logoBig) {
    super(logo, createHiDPIDisabledIcon(logo, true), logoBig, createHiDPIDisabledIcon(logoBig, true));
  }

  HiDPIPluginLogoIcon(@NotNull Icon logo, @NotNull Icon logoDisabled, @NotNull Icon logoBig, @NotNull Icon logoDisabledBig) {
    super(logo, logoDisabled, logoBig, logoDisabledBig);
  }

  @Override
  protected @NotNull Icon getErrorLogo2x() {
    if (myCachedErrorLogo2x == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      myCachedErrorLogo2x = super.getErrorLogo2x();
    }
    return myCachedErrorLogo2x;
  }

  static void clearCache() {
    myCachedErrorLogo2x = null;
    synchronized (disabledIcons) {
      disabledIcons.clear();
    }
  }

  @Override
  protected @NotNull Icon getDisabledIcon(@NotNull Icon icon, boolean base) {
    return createHiDPIDisabledIcon(icon, base);
  }

  @Override
  protected @NotNull Icon getScaled2xIcon(@NotNull Icon icon) {
    Computable<Icon> superCall = () -> super.getScaled2xIcon(icon);

    return new Icon() {
      final ScaleContext myContext = ScaleContext.create();
      Icon myIcon;

      @NotNull
      Icon getIcon() {
        if (myContext.update() || myIcon == null) {
          myIcon = superCall.compute();
        }
        return myIcon;
      }

      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        getIcon().paintIcon(c, g, x, y);
      }

      @Override
      public int getIconWidth() {
        return getIcon().getIconWidth();
      }

      @Override
      public int getIconHeight() {
        return getIcon().getIconHeight();
      }
    };
  }

  private static @NotNull Icon createHiDPIDisabledIcon(@NotNull Icon icon, boolean base) {
    return getHiDPI(ScaleContext.create(), createDisabledIcon(icon, base));
  }

  static @NotNull Icon loadSVG(@NotNull InputStream stream, int width, int height) throws IOException {
    ScaleContext context = ScaleContext.create();
    BufferedImage image = (BufferedImage)SVGLoader.load(null, stream, context, width, height);
    BufferedImage t = (BufferedImage)ImageUtil.ensureHiDPI(image, context);
    return getHiDPI(context, t);
  }

  private static @NotNull Icon getHiDPI(@NotNull ScaleContext context, @NotNull Object source) {
    if (source instanceof ImageIcon) {
      Image image = ((ImageIcon)source).getImage();
      if (image instanceof JBHiDPIScaledImage) {
        return wrapHiDPI(context, (JBHiDPIScaledImage)image);
      }
      return (Icon)source;
    }
    if (source instanceof JBHiDPIScaledImage) {
      return wrapHiDPI(context, (JBHiDPIScaledImage)source);
    }
    if (source instanceof Image) {
      return new JBImageIcon((Image)source);
    }
    return (Icon)source;
  }

  private static @NotNull Icon wrapHiDPI(@NotNull ScaleContext context, @NotNull JBHiDPIScaledImage image) {
    return new JBImageIcon(image) {
      final double myBase = context.getScale(USR_SCALE);

      private void update() {
        if (context.update()) {
          setImage(image.scale(context.getScale(USR_SCALE) / myBase));
        }
      }

      @Override
      public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
        update();
        super.paintIcon(c, g, x, y);
      }

      @Override
      public int getIconWidth() {
        update();
        return super.getIconWidth();
      }

      @Override
      public int getIconHeight() {
        update();
        return super.getIconHeight();
      }
    };
  }
}