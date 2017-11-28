/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJIconCache {
  private static final HashMap<String, Icon> cache = new HashMap<>();

  public static Icon getIcon(String name, boolean editable, boolean selected, boolean focused, boolean enabled, boolean pressed) {
    String key = name;
    if (editable) key += "Editable";
    if (selected) key+= "Selected";

    if (pressed) key += "Pressed";
    else if (focused) key+= "Focused";
    else if (!enabled) key+="Disabled";

    if (IntelliJLaf.isGraphite()) key= "graphite/" + key;
    if (UIUtil.isUnderWin10LookAndFeel()) key = "win10/" + key;

    Icon icon = cache.get(key);
    if (icon == null) {
      icon = IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/" + key + ".png", MacIntelliJIconCache.class, true);
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
