// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.util.HashMap;

/**
 * @author Konstantin Bulenkov
 */
public class IconCache {
  private static final HashMap<String, Icon> cache = new HashMap<>();

  public static Icon getIcon(String name, boolean editable, boolean selected, boolean focused, boolean enabled, boolean pressed) {
    String key = name;
    if (editable) key += "Editable";
    if (selected) key+= "Selected";

    if (pressed) key += "Pressed";
    else if (focused) key+= "Focused";
    else if (!enabled) key+="Disabled";

    String dir = "";

    // For Mac blue theme and other LAFs use default directory icons
    if (UIUtil.isUnderDefaultMacTheme()) dir = IntelliJLaf.isGraphite() ? "graphite/" : "";
    else if (UIUtil.isUnderWin10LookAndFeel()) dir = "win10/";
    else if (UIUtil.isUnderDarcula()) dir = "darcula/";
    else if (UIUtil.isUnderIntelliJLaF()) dir = "intellij/";

    key = dir + key;

    Icon icon = cache.get(key);
    if (icon == null) {
      icon = IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/" + key + ".png", IconCache.class, true);
      cache.put(key, icon);
    }
    return icon;
  }

  public static Icon getIcon(String name, boolean editable, boolean selected, boolean focused, boolean enabled) {
    return getIcon(name, editable, selected, focused, enabled, false);
  }

  public static Icon getIcon(String name, boolean selected, boolean focused, boolean enabled) {
    return getIcon(name, false, selected, focused, enabled);
  }

  public static Icon getIcon(String name, boolean selected, boolean focused) {
    return getIcon(name, false, selected, focused, true);
  }

  public static Icon getIcon(String name) {
    return getIcon(name, false, false, false, true);
  }
}
