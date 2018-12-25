// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Objects;

/**
 * @author Alexander Lobas
 */
class PluginLogoIcon implements PluginLogoIconProvider {
  private final Icon myPluginLogo_40;
  private final Icon myPluginLogoJB_40;
  private final Icon myPluginLogoError_40;
  private final Icon myPluginLogoJBError_40;

  private final Icon myPluginLogoDisabled_40;
  private final Icon myPluginLogoDisabledJB_40;
  private final Icon myPluginLogoDisabledError_40;
  private final Icon myPluginLogoDisabledJBError_40;

  private final Icon myPluginLogo_80;
  private final Icon myPluginLogoJB_80;
  private final Icon myPluginLogoError_80;
  private final Icon myPluginLogoJBError_80;

  private final Icon myPluginLogoDisabled_80;
  private final Icon myPluginLogoDisabledJB_80;
  private final Icon myPluginLogoDisabledError_80;
  private final Icon myPluginLogoDisabledJBError_80;

  private static Icon myCachedDisabledJBLogo;
  private static Icon myCachedJBLogo2x;
  private static Icon myCachedErrorLogo2x;
  private static Icon myCachedDisabledJBLogo2x;

  static void clearCache() {
    myCachedDisabledJBLogo = null;
    myCachedJBLogo2x = null;
    myCachedErrorLogo2x = null;
    myCachedDisabledJBLogo2x = null;
  }

  PluginLogoIcon(@NotNull Icon logo_40, @NotNull Icon logoDisabled_40, @NotNull Icon logo_80, @NotNull Icon logoDisabled_80) {
    myPluginLogo_40 = logo_40;
    myPluginLogoJB_40 = setSouthEast(logo_40, AllIcons.Plugins.ModifierJBLogo);
    myPluginLogoError_40 = setSouthWest(logo_40, AllIcons.Plugins.ModifierInvalid);
    myPluginLogoJBError_40 = setSouthEastWest(logo_40, AllIcons.Plugins.ModifierJBLogo, AllIcons.Plugins.ModifierInvalid);

    if (myCachedDisabledJBLogo == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      myCachedDisabledJBLogo = getDisabledIcon(AllIcons.Plugins.ModifierJBLogo);
    }

    myPluginLogoDisabled_40 = logoDisabled_40;
    myPluginLogoDisabledJB_40 = setSouthEast(logoDisabled_40, myCachedDisabledJBLogo);
    myPluginLogoDisabledError_40 = setSouthWest(logoDisabled_40, AllIcons.Plugins.ModifierInvalid);
    myPluginLogoDisabledJBError_40 = setSouthEastWest(logoDisabled_40, myCachedDisabledJBLogo, AllIcons.Plugins.ModifierInvalid);

    if (myCachedJBLogo2x == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      myCachedJBLogo2x = IconUtil.scale(AllIcons.Plugins.ModifierJBLogo, null, 2);
    }

    if (myCachedErrorLogo2x == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      myCachedErrorLogo2x = IconUtil.scale(AllIcons.Plugins.ModifierInvalid, null, 2);
    }

    myPluginLogo_80 = logo_80;
    myPluginLogoJB_80 = setSouthEast(logo_80, myCachedJBLogo2x);
    myPluginLogoError_80 = setSouthWest(logo_80, myCachedErrorLogo2x);
    myPluginLogoJBError_80 = setSouthEastWest(logo_80, myCachedJBLogo2x, myCachedErrorLogo2x);

    if (myCachedDisabledJBLogo2x == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      myCachedDisabledJBLogo2x = getDisabledIcon(myCachedJBLogo2x);
    }

    myPluginLogoDisabled_80 = logoDisabled_80;
    myPluginLogoDisabledJB_80 = setSouthEast(logoDisabled_80, myCachedDisabledJBLogo2x);
    myPluginLogoDisabledError_80 = setSouthWest(logoDisabled_80, myCachedErrorLogo2x);
    myPluginLogoDisabledJBError_80 = setSouthEastWest(logoDisabled_80, myCachedDisabledJBLogo2x, myCachedErrorLogo2x);
  }

  @NotNull
  private static Icon setSouthEast(@NotNull Icon main, @NotNull Icon southEast) {
    LayeredIcon layeredIcon = new LayeredIcon(2);

    layeredIcon.setIcon(main, 0);
    layeredIcon.setIcon(southEast, 1, SwingConstants.SOUTH_EAST);

    return layeredIcon;
  }

  @NotNull
  protected Icon getDisabledIcon(Icon icon) {
    return Objects.requireNonNull(IconLoader.getDisabledIcon(icon));
  }

  @NotNull
  private static Icon setSouthWest(@NotNull Icon main, @NotNull Icon southWest) {
    LayeredIcon layeredIcon = new LayeredIcon(2);

    layeredIcon.setIcon(main, 0);
    layeredIcon.setIcon(southWest, 1, SwingConstants.SOUTH_WEST);

    return layeredIcon;
  }

  @NotNull
  private static Icon setSouthEastWest(@NotNull Icon main, @NotNull Icon southEast, @NotNull Icon southWest) {
    LayeredIcon layeredIcon = new LayeredIcon(3);

    layeredIcon.setIcon(main, 0);
    layeredIcon.setIcon(southEast, 1, SwingConstants.SOUTH_EAST);
    layeredIcon.setIcon(southWest, 2, SwingConstants.SOUTH_WEST);

    return layeredIcon;
  }

  @NotNull
  @Override
  public Icon getIcon(boolean big, boolean jb, boolean error, boolean disabled) {
    if (jb && !error) {
      if (big) {
        return disabled ? myPluginLogoDisabledJB_80 : myPluginLogoJB_80;
      }
      return disabled ? myPluginLogoDisabledJB_40 : myPluginLogoJB_40;
    }
    if (!jb && error) {
      if (big) {
        return disabled ? myPluginLogoDisabledError_80 : myPluginLogoError_80;
      }
      return disabled ? myPluginLogoDisabledError_40 : myPluginLogoError_40;
    }
    if (jb/* && error*/) {
      if (big) {
        return disabled ? myPluginLogoDisabledJBError_80 : myPluginLogoJBError_80;
      }
      return disabled ? myPluginLogoDisabledJBError_40 : myPluginLogoJBError_40;
    }
    // !jb && !error
    if (big) {
      return disabled ? myPluginLogoDisabled_80 : myPluginLogo_80;
    }
    return disabled ? myPluginLogoDisabled_40 : myPluginLogo_40;
  }
}