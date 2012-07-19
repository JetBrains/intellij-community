/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.IconUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.basic.BasicLookAndFeel;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;

/**
 * @author Konstantin Bulenkov
 */
final class DurculaLaf extends BasicLookAndFeel {
  BasicLookAndFeel base;
  public DurculaLaf() {
    try {
      if (SystemInfo.isWindows) {
        base = new IdeaLaf();
      } else {
        final String name = UIManager.getSystemLookAndFeelClassName();
        base = (BasicLookAndFeel)Class.forName(name).newInstance();
      }
    }
    catch (Exception ignore) {
      log(ignore);
    }
  }

  private void callInit(String method, UIDefaults defaults) {
    try {
      final Method superMethod = BasicLookAndFeel.class.getDeclaredMethod(method, UIDefaults.class);
      superMethod.setAccessible(true);
      superMethod.invoke(base, defaults);
    }
    catch (Exception ignore) {
      log(ignore);
    }
  }

  private static void log(Exception e) {
    //everything's gonna be alright
    //e.printStackTrace();
  }

  @Override
  public UIDefaults getDefaults() {
    try {
      final Method superMethod = BasicLookAndFeel.class.getDeclaredMethod("getDefaults");
      superMethod.setAccessible(true);
      final UIDefaults defaults = (UIDefaults)superMethod.invoke(base);
      LafManagerImpl.initInputMapDefaults(defaults);
      initIdeaDefaults(defaults);
      return defaults;
    }
    catch (Exception ignore) {
      log(ignore);
    }
    return super.getDefaults();
  }

  private void call(String method) {
    try {
      final Method superMethod = BasicLookAndFeel.class.getDeclaredMethod(method);
      superMethod.setAccessible(true);
      superMethod.invoke(base);
    }
    catch (Exception ignore) {
      log(ignore);
    }
  }

  public void initComponentDefaults(UIDefaults defaults) {
    callInit("initComponentDefaults", defaults);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  static void initIdeaDefaults(UIDefaults defaults) {
    loadDefaults(defaults, null); //load defaults
    loadDefaults(defaults, SystemInfo.isMac ? "mac" : SystemInfo.isWindows ? "windows" : "linux"); // load OS customization
    defaults.put("Table.ancestorInputMap", new UIDefaults.LazyInputMap(new Object[] {
      "ctrl C", "copy",
      "ctrl V", "paste",
      "ctrl X", "cut",
      "COPY", "copy",
      "PASTE", "paste",
      "CUT", "cut",
      "control INSERT", "copy",
      "shift INSERT", "paste",
      "shift DELETE", "cut",
      "RIGHT", "selectNextColumn",
      "KP_RIGHT", "selectNextColumn",
      "LEFT", "selectPreviousColumn",
      "KP_LEFT", "selectPreviousColumn",
      "DOWN", "selectNextRow",
      "KP_DOWN", "selectNextRow",
      "UP", "selectPreviousRow",
      "KP_UP", "selectPreviousRow",
      "shift RIGHT", "selectNextColumnExtendSelection",
      "shift KP_RIGHT", "selectNextColumnExtendSelection",
      "shift LEFT", "selectPreviousColumnExtendSelection",
      "shift KP_LEFT", "selectPreviousColumnExtendSelection",
      "shift DOWN", "selectNextRowExtendSelection",
      "shift KP_DOWN", "selectNextRowExtendSelection",
      "shift UP", "selectPreviousRowExtendSelection",
      "shift KP_UP", "selectPreviousRowExtendSelection",
      "PAGE_UP", "scrollUpChangeSelection",
      "PAGE_DOWN", "scrollDownChangeSelection",
      "HOME", "selectFirstColumn",
      "END", "selectLastColumn",
      "shift PAGE_UP", "scrollUpExtendSelection",
      "shift PAGE_DOWN", "scrollDownExtendSelection",
      "shift HOME", "selectFirstColumnExtendSelection",
      "shift END", "selectLastColumnExtendSelection",
      "ctrl PAGE_UP", "scrollLeftChangeSelection",
      "ctrl PAGE_DOWN", "scrollRightChangeSelection",
      "ctrl HOME", "selectFirstRow",
      "ctrl END", "selectLastRow",
      "ctrl shift PAGE_UP", "scrollRightExtendSelection",
      "ctrl shift PAGE_DOWN", "scrollLeftExtendSelection",
      "ctrl shift HOME", "selectFirstRowExtendSelection",
      "ctrl shift END", "selectLastRowExtendSelection",
      "TAB", "selectNextColumnCell",
      "shift TAB", "selectPreviousColumnCell",
      //"ENTER", "selectNextRowCell",
      "shift ENTER", "selectPreviousRowCell",
      "ctrl A", "selectAll",
      //"ESCAPE", "cancel",
      "F2", "startEditing"
    }));
  }

  private static void loadDefaults(UIDefaults defaults, @Nullable String postfix) {
    try {
      final Properties properties = new Properties();
      final String secondPart = postfix == null ? "" : "_" + postfix;
      final String name = "darcula" + secondPart + ".properties";
      final InputStream stream = DurculaLaf.class.getResourceAsStream(name);
      properties.load(stream);
      for (String key : properties.stringPropertyNames()) {
        final String value = properties.getProperty(key);
        if (key.endsWith("Insets")) {
          final List<String> numbers = StringUtil.split(value, ",");
          defaults.put(key, new InsetsUIResource(Integer.parseInt(numbers.get(0)),
                                                 Integer.parseInt(numbers.get(1)),
                                                 Integer.parseInt(numbers.get(2)),
                                                 Integer.parseInt(numbers.get(3))));
        } else {
          final Color color = ColorUtil.fromHex(value, null);
          final Integer invVal = getInteger(value);
          Icon icon = value != null && value.startsWith("AllIcons.") ? IconLoader.getIcon(value) : null;
          if (color != null) {
            defaults.put(key, new ColorUIResource(color));
          } else if (invVal != null) {
            defaults.put(key, invVal);
          } else if (icon != null) {
            defaults.put(key, new IconUIResource(icon));
          } else {
            defaults.put(key, value);
          }
        }
      }
    }
    catch (IOException e) {
      log(e);
    }
  }

  private static Integer getInteger(String value) {
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }


  @Override
  public String getName() {
    return "Darcula";
  }

  @Override
  public String getID() {
    return getName();
  }

  @Override
  public String getDescription() {
    return "IntelliJ Dark Look and Feel";
  }

  @Override
  public boolean isNativeLookAndFeel() {
    return true;
  }

  @Override
  public boolean isSupportedLookAndFeel() {
    return true;
  }

  @Override
  protected void initSystemColorDefaults(UIDefaults defaults) {
    callInit("initSystemColorDefaults", defaults);
  }

  @Override
  protected void initClassDefaults(UIDefaults defaults) {
    callInit("initClassDefaults", defaults);
  }

  @Override
  public void initialize() {
    call("initialize");
  }

  @Override
  public void uninitialize() {
    call("uninitialize");
  }

  @Override
  protected void loadSystemColors(UIDefaults defaults, String[] systemColors, boolean useNative) {
    try {
      final Method superMethod = BasicLookAndFeel.class.getDeclaredMethod("loadSystemColors",
                                                                   UIDefaults.class,
                                                                   String[].class,
                                                                   boolean.class);
      superMethod.setAccessible(true);
      superMethod.invoke(base, defaults, systemColors, useNative);
    }
    catch (Exception ignore) {
      log(ignore);
    }
  }


  @Override
  public boolean getSupportsWindowDecorations() {
    return true;
  }
}
