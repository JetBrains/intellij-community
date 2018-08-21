// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public class PluginLogoInfo {
  private static LayeredIcon PluginLogoJB_40;
  private static LayeredIcon PluginLogoError_40;
  private static LayeredIcon PluginLogoJBError_40;

  private static LayeredIcon PluginLogoDisabledJB_40;
  private static LayeredIcon PluginLogoDisabledError_40;
  private static LayeredIcon PluginLogoDisabledJBError_40;

  private static LayeredIcon PluginLogoJB_80;
  private static LayeredIcon PluginLogoError_80;
  private static LayeredIcon PluginLogoJBError_80;

  private static LayeredIcon PluginLogoDisabledJB_80;
  private static LayeredIcon PluginLogoDisabledError_80;
  private static LayeredIcon PluginLogoDisabledJBError_80;

  private static boolean myCreateIcons = true;

  static {
    LafManager.getInstance().addLafManagerListener(source -> myCreateIcons = true);
  }

  private static void createIcons() {
    if (!myCreateIcons) {
      return;
    }
    myCreateIcons = false;

    setSouthEast(PluginLogoJB_40 = new LayeredIcon(2), AllIcons.Plugins.PluginLogo_40, AllIcons.Plugins.ModifierJBLogo);
    setSouthWest(PluginLogoError_40 = new LayeredIcon(2), AllIcons.Plugins.PluginLogo_40, AllIcons.Plugins.ModifierInvalid);
    setSouthEastWest(PluginLogoJBError_40 = new LayeredIcon(3), AllIcons.Plugins.PluginLogo_40, AllIcons.Plugins.ModifierJBLogo,
                     AllIcons.Plugins.ModifierInvalid);

    Icon disabledJBLogo = IconLoader.getDisabledIcon(AllIcons.Plugins.ModifierJBLogo);
    assert disabledJBLogo != null;

    setSouthEast(PluginLogoDisabledJB_40 = new LayeredIcon(2), AllIcons.Plugins.PluginLogoDisabled_40, disabledJBLogo);
    setSouthWest(PluginLogoDisabledError_40 = new LayeredIcon(2), AllIcons.Plugins.PluginLogoDisabled_40,
                 AllIcons.Plugins.ModifierInvalid);
    setSouthEastWest(PluginLogoDisabledJBError_40 = new LayeredIcon(3), AllIcons.Plugins.PluginLogoDisabled_40, disabledJBLogo,
                     AllIcons.Plugins.ModifierInvalid);

    Icon jbLogo2x = IconUtil.scale(AllIcons.Plugins.ModifierJBLogo, null, 2);
    Icon errorLogo2x = IconUtil.scale(AllIcons.Plugins.ModifierInvalid, null, 2);

    setSouthEast(PluginLogoJB_80 = new LayeredIcon(2), AllIcons.Plugins.PluginLogo_80, jbLogo2x);
    setSouthWest(PluginLogoError_80 = new LayeredIcon(2), AllIcons.Plugins.PluginLogo_80, errorLogo2x);
    setSouthEastWest(PluginLogoJBError_80 = new LayeredIcon(3), AllIcons.Plugins.PluginLogo_80, jbLogo2x, errorLogo2x);

    Icon disabledJBLogo2x = IconLoader.getDisabledIcon(jbLogo2x);
    assert disabledJBLogo2x != null;

    setSouthEast(PluginLogoDisabledJB_80 = new LayeredIcon(2), AllIcons.Plugins.PluginLogoDisabled_80, disabledJBLogo2x);
    setSouthWest(PluginLogoDisabledError_80 = new LayeredIcon(2), AllIcons.Plugins.PluginLogoDisabled_80, errorLogo2x);
    setSouthEastWest(PluginLogoDisabledJBError_80 = new LayeredIcon(3), AllIcons.Plugins.PluginLogoDisabled_80, disabledJBLogo2x,
                     errorLogo2x);
  }

  private static void setSouthEast(@NotNull LayeredIcon layeredIcon, @NotNull Icon main, @NotNull Icon southEast) {
    layeredIcon.setIcon(main, 0);
    layeredIcon.setIcon(southEast, 1, SwingConstants.SOUTH_EAST);
  }

  private static void setSouthWest(@NotNull LayeredIcon layeredIcon, @NotNull Icon main, @NotNull Icon southWest) {
    layeredIcon.setIcon(main, 0);
    layeredIcon.setIcon(southWest, 1, SwingConstants.SOUTH_WEST);
  }

  private static void setSouthEastWest(@NotNull LayeredIcon layeredIcon,
                                       @NotNull Icon main,
                                       @NotNull Icon southEast,
                                       @NotNull Icon southWest) {
    layeredIcon.setIcon(main, 0);
    layeredIcon.setIcon(southEast, 1, SwingConstants.SOUTH_EAST);
    layeredIcon.setIcon(southWest, 2, SwingConstants.SOUTH_WEST);
  }

  @NotNull
  public static Icon getIcon(boolean big, boolean jb, boolean error, boolean disabled) {
    createIcons();

    if (jb && !error) {
      if (big) {
        return disabled ? PluginLogoDisabledJB_80 : PluginLogoJB_80;
      }
      return disabled ? PluginLogoDisabledJB_40 : PluginLogoJB_40;
    }
    if (!jb && error) {
      if (big) {
        return disabled ? PluginLogoDisabledError_80 : PluginLogoError_80;
      }
      return disabled ? PluginLogoDisabledError_40 : PluginLogoError_40;
    }
    if (jb/* && error*/) {
      if (big) {
        return disabled ? PluginLogoDisabledJBError_80 : PluginLogoJBError_80;
      }
      return disabled ? PluginLogoDisabledJBError_40 : PluginLogoJBError_40;
    }
    // !jb && !error
    if (big) {
      return disabled ? AllIcons.Plugins.PluginLogoDisabled_80 : AllIcons.Plugins.PluginLogo_80;
    }
    return disabled ? AllIcons.Plugins.PluginLogoDisabled_40 : AllIcons.Plugins.PluginLogo_40;
  }
}