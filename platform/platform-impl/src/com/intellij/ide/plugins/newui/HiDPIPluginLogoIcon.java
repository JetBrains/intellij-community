// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.util.Computable;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.JBImageIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

import static com.intellij.ui.scale.ScaleType.USR_SCALE;

/**
 * @author Alexander Lobas
 */
public class HiDPIPluginLogoIcon extends PluginLogoIcon {
  private static Icon myCachedDisabledJBLogo;
  private static Icon myCachedJBLogo2x;
  private static Icon myCachedErrorLogo2x;
  private static Icon myCachedDisabledJBLogo2x;

  HiDPIPluginLogoIcon(@NotNull Icon logo_40, @NotNull Icon logo_80) {
    super(logo_40, createHiDPIDisabledIcon(logo_40, true), logo_80, createHiDPIDisabledIcon(logo_80, true));
  }

  HiDPIPluginLogoIcon(@NotNull Icon logo_40, @NotNull Icon logoDisabled_40, @NotNull Icon logo_80, @NotNull Icon logoDisabled_80) {
    super(logo_40, logoDisabled_40, logo_80, logoDisabled_80);
  }

  @Override
  @NotNull
  protected Icon getDisabledJBLogo() {
    if (myCachedDisabledJBLogo == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      myCachedDisabledJBLogo = super.getDisabledJBLogo();
    }
    return myCachedDisabledJBLogo;
  }

  @Override
  @NotNull
  protected Icon getJBLogo2x() {
    if (myCachedJBLogo2x == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      myCachedJBLogo2x = super.getJBLogo2x();
    }
    return myCachedJBLogo2x;
  }

  @Override
  @NotNull
  protected Icon getErrorLogo2x() {
    if (myCachedErrorLogo2x == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      myCachedErrorLogo2x = super.getErrorLogo2x();
    }
    return myCachedErrorLogo2x;
  }

  @Override
  @NotNull
  protected Icon getDisabledJBLogo2x(@NotNull Icon jbLogo2x) {
    if (myCachedDisabledJBLogo2x == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      myCachedDisabledJBLogo2x = super.getDisabledJBLogo2x(jbLogo2x);
    }
    return myCachedDisabledJBLogo2x;
  }

  static void clearCache() {
    myCachedDisabledJBLogo = null;
    myCachedJBLogo2x = null;
    myCachedErrorLogo2x = null;
    myCachedDisabledJBLogo2x = null;
    DisabledIcons.clear();
  }

  @NotNull
  @Override
  protected Icon getDisabledIcon(@NotNull Icon icon, boolean base) {
    return createHiDPIDisabledIcon(icon, base);
  }

  @NotNull
  @Override
  protected Icon getScaled2xIcon(@NotNull Icon icon) {
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

  @NotNull
  private static Icon createHiDPIDisabledIcon(@NotNull Icon icon, boolean base) {
    return getHiDPI(ScaleContext.create(), createDisabledIcon(icon, base));
  }

  @NotNull
  static Icon loadSVG(@NotNull InputStream stream, int width, int height) throws IOException {
    ScaleContext context = ScaleContext.create();
    return getHiDPI(context, SVGLoader.loadHiDPI(null, stream, context, width, height));
  }

  @NotNull
  private static Icon getHiDPI(@NotNull ScaleContext context, @NotNull Object source) {
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

  @NotNull
  private static Icon wrapHiDPI(@NotNull ScaleContext context, @NotNull JBHiDPIScaledImage image) {
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