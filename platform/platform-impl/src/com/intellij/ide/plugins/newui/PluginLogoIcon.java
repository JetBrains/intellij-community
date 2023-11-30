// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.icons.FilteredIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.GrayFilter;
import com.intellij.util.ui.JBImageIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
class PluginLogoIcon implements PluginLogoIconProvider {
  private static final GrayFilter grayFilter = new GrayFilter();

  static final LoadingCache<JBImageIcon, Icon> disabledIcons = Caffeine.newBuilder().weakKeys().maximumSize(256).build(key -> {
    return new FilteredIcon(key, () -> new GrayFilter(JBColor.isBright() ? 20 : 19, 0, 100));
  });
  static final LoadingCache<JBImageIcon, Icon> baseDisabledIcons = Caffeine.newBuilder().weakKeys().maximumSize(256).build(key -> {
    return new FilteredIcon(key, () -> grayFilter);
  });

  private final Icon myPluginLogo;
  private final Icon myPluginLogoError;

  private final Icon myPluginLogoDisabled;
  private final Icon myPluginLogoDisabledError;

  private final Icon myPluginLogoBig;
  private final Icon myPluginLogoErrorBig;

  private final Icon myPluginLogoDisabledBig;
  private final Icon myPluginLogoDisabledErrorBig;

  PluginLogoIcon(@NotNull Icon logo, @NotNull Icon logoDisabled, @NotNull Icon logoBig, @NotNull Icon logoDisabledBig) {
    myPluginLogo = logo;
    myPluginLogoError = setSouthWest(logo, AllIcons.Plugins.ModifierInvalid);

    myPluginLogoDisabled = logoDisabled;
    myPluginLogoDisabledError = setSouthWest(logoDisabled, AllIcons.Plugins.ModifierInvalid);

    Icon errorLogo2x = getErrorLogo2x();

    myPluginLogoBig = logoBig;
    myPluginLogoErrorBig = setSouthWest(logoBig, errorLogo2x);

    myPluginLogoDisabledBig = logoDisabledBig;
    myPluginLogoDisabledErrorBig = setSouthWest(logoDisabledBig, errorLogo2x);
  }

  protected @NotNull Icon getDisabledIcon(@NotNull JBImageIcon icon, boolean base) {
    return calculateDisabledIcon(icon, base);
  }

  static @NotNull Icon calculateDisabledIcon(@NotNull JBImageIcon icon, boolean base) {
    return base ? baseDisabledIcons.get(icon) : disabledIcons.get(icon);
  }

  protected @NotNull Icon getScaled2xIcon(@NotNull Icon icon) {
    return IconUtil.scale(icon, null, 2.0f);
  }

  private static @NotNull Icon setSouthWest(@NotNull Icon main, @NotNull Icon southWest) {
    LayeredIcon layeredIcon = new LayeredIcon(2);

    layeredIcon.setIcon(main, 0);
    layeredIcon.setIcon(southWest, 1, SwingConstants.SOUTH_WEST);

    return layeredIcon;
  }

  protected @NotNull Icon getErrorLogo2x() {
    return PluginLogoKt.reloadPluginIcon(AllIcons.Plugins.ModifierInvalid, 20, 20);
  }

  @Override
  public @NotNull Icon getIcon(boolean big, boolean error, boolean disabled) {
    if (error) {
      if (big) {
        return disabled ? myPluginLogoDisabledErrorBig : myPluginLogoErrorBig;
      }
      return disabled ? myPluginLogoDisabledError : myPluginLogoError;
    }
    if (big) {
      return disabled ? myPluginLogoDisabledBig : myPluginLogoBig;
    }
    return disabled ? myPluginLogoDisabled : myPluginLogo;
  }
}