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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.laf.DarculaMetalTheme;
import com.intellij.ide.ui.laf.IdeaLaf;
import com.intellij.ide.ui.laf.LafManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColorUtil;
import com.intellij.util.Alarm;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import sun.awt.AppContext;

import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.BasicLookAndFeel;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaLaf extends BasicLookAndFeel {
  public static final String NAME = "Darcula";
  BasicLookAndFeel base;
  private static Disposable myDisposable;
  private static Alarm myMnemonicAlarm;
  private static boolean myAltPressed;

  public DarculaLaf() {
    base = createBaseLookAndFeel();
  }

  protected BasicLookAndFeel createBaseLookAndFeel() {
    try {
      if (SystemInfo.isMac) {
        final String name = UIManager.getSystemLookAndFeelClassName();
        return (BasicLookAndFeel)Class.forName(name).newInstance();
      } else {
        return new IdeaLaf();
      }
    }
    catch (Exception e) {
      log(e);
    }
    return null;
  }

  private void callInit(String method, UIDefaults defaults) {
    try {
      final Method superMethod = BasicLookAndFeel.class.getDeclaredMethod(method, UIDefaults.class);
      superMethod.setAccessible(true);
      superMethod.invoke(base, defaults);
    }
    catch (Exception e) {
      log(e);
    }
  }

  @SuppressWarnings("UnusedParameters")
  protected static void log(Exception e) {
//    everything is gonna be alright
    e.printStackTrace();
  }

  @Override
  public UIDefaults getDefaults() {
    try {
      final Method superMethod = BasicLookAndFeel.class.getDeclaredMethod("getDefaults");
      superMethod.setAccessible(true);
      final UIDefaults metalDefaults = (UIDefaults)superMethod.invoke(new MetalLookAndFeel());

      final UIDefaults defaults = (UIDefaults)superMethod.invoke(base);
      if (SystemInfo.isLinux) {
        if (!Registry.is("darcula.use.native.fonts.on.linux")) {
          Font font = findFont("DejaVu Sans");
          if (font != null) {
            for (Object key : defaults.keySet()) {
              if (key instanceof String && ((String)key).endsWith(".font")) {
                defaults.put(key, new FontUIResource(font.deriveFont(13f)));
              }
            }
          }
        } else if (Arrays.asList("CN", "JP", "KR", "TW").contains(Locale.getDefault().getCountry())) {
          for (Object key : defaults.keySet()) {
            if (key instanceof String && ((String)key).endsWith(".font")) {
              final Font font = defaults.getFont(key);
              if (font != null) {
                defaults.put(key, new FontUIResource("Dialog", font.getStyle(), font.getSize()));
              }
            }
          }
        }
      }

      LafManagerImpl.initInputMapDefaults(defaults);
      initIdeaDefaults(defaults);
      patchStyledEditorKit(defaults);
      patchComboBox(metalDefaults, defaults);
      defaults.remove("Spinner.arrowButtonBorder");
      defaults.put("Spinner.arrowButtonSize", JBUI.size(16, 5).asUIResource());
      MetalLookAndFeel.setCurrentTheme(createMetalTheme());
      if (SystemInfo.isWindows && Registry.is("ide.win.frame.decoration")) {
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
      }
      if (SystemInfo.isLinux && JBUI.isHiDPI()) {
        applySystemFonts(defaults);
      }
      defaults.put("EditorPane.font", defaults.getFont("TextField.font"));
      return defaults;
    }
    catch (Exception e) {
      log(e);
    }
    return super.getDefaults();
  }

  private static void applySystemFonts(UIDefaults defaults) {
    try {
      String fqn = UIUtil.getSystemLookAndFeelClassName();
      Object systemLookAndFeel = Class.forName(fqn).newInstance();
      final Method superMethod = BasicLookAndFeel.class.getDeclaredMethod("getDefaults");
      superMethod.setAccessible(true);
      final UIDefaults systemDefaults = (UIDefaults)superMethod.invoke(systemLookAndFeel);
      for (Map.Entry<Object, Object> entry : systemDefaults.entrySet()) {
        if (entry.getValue() instanceof Font) {
          defaults.put(entry.getKey(), entry.getValue());
        }
      }
    } catch (Exception e) {
      log(e);
    }
  }

  protected DefaultMetalTheme createMetalTheme() {
    return new DarculaMetalTheme();
  }

  private static Font findFont(String name) {
    for (Font font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
      if (font.getName().equals(name)) {
        return font;
      }
    }
    return null;
  }

  private static void patchComboBox(UIDefaults metalDefaults, UIDefaults defaults) {
    defaults.remove("ComboBox.ancestorInputMap");
    defaults.remove("ComboBox.actionMap");
    defaults.put("ComboBox.ancestorInputMap", metalDefaults.get("ComboBox.ancestorInputMap"));
    defaults.put("ComboBox.actionMap", metalDefaults.get("ComboBox.actionMap"));
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private void patchStyledEditorKit(UIDefaults defaults) {
    URL url = getClass().getResource(getPrefix() + (JBUI.isHiDPI() ? "@2x.css" : ".css"));
    StyleSheet styleSheet = UIUtil.loadStyleSheet(url);
    defaults.put("StyledEditorKit.JBDefaultStyle", styleSheet);
    try {
      Field keyField = HTMLEditorKit.class.getDeclaredField("DEFAULT_STYLES_KEY");
      keyField.setAccessible(true);
      AppContext.getAppContext().put(keyField.get(null), UIUtil.loadStyleSheet(url));
    }
    catch (Exception e) {
      log(e);
    }
  }

  protected String getPrefix() {
    return "darcula";
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
  protected void initIdeaDefaults(UIDefaults defaults) {
    loadDefaults(defaults);
    defaults.put("Table.ancestorInputMap", new UIDefaults.LazyInputMap(new Object[] {
      "ctrl C", "copy",
      "meta C", "copy",
      "ctrl V", "paste",
      "meta V", "paste",
      "ctrl X", "cut",
      "meta X", "cut",
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
      "meta A", "selectAll",
      "ESCAPE", "cancel",
      "F2", "startEditing"
    }));
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  protected void loadDefaults(UIDefaults defaults) {
    final Properties properties = new Properties();
    final String osSuffix = SystemInfo.isMac ? "mac" : SystemInfo.isWindows ? "windows" : "linux";
    try {
      InputStream stream = getClass().getResourceAsStream(getPrefix() + ".properties");
      properties.load(stream);
      stream.close();

      stream = getClass().getResourceAsStream(getPrefix() + "_" + osSuffix + ".properties");
      properties.load(stream);
      stream.close();

      HashMap<String, Object> darculaGlobalSettings = new HashMap<>();
      final String prefix = getPrefix() + ".";
      for (String key : properties.stringPropertyNames()) {
        if (key.startsWith(prefix)) {
          darculaGlobalSettings.put(key.substring(prefix.length()), parseValue(key, properties.getProperty(key)));
        }
      }

      for (Object key : defaults.keySet()) {
        if (key instanceof String && ((String)key).contains(".")) {
          final String s = (String)key;
          final String darculaKey = s.substring(s.lastIndexOf('.') + 1);
          if (darculaGlobalSettings.containsKey(darculaKey)) {
            defaults.put(key, darculaGlobalSettings.get(darculaKey));
          }
        }
      }

      for (String key : properties.stringPropertyNames()) {
        final String value = properties.getProperty(key);
        defaults.put(key, parseValue(key, value));
      }
    }
    catch (IOException e) {log(e);}
  }

  protected Object parseValue(String key, @NotNull String value) {
    if ("null".equals(value)) {
      return null;
    }

    if (key.endsWith("Insets")) {
      return parseInsets(value);
    } else if (key.endsWith("Border") || key.endsWith("border")) {

      try {
        if (StringUtil.split(value, ",").size() == 4) {
          return new BorderUIResource.EmptyBorderUIResource(parseInsets(value));
        } else {
          return Class.forName(value).newInstance();
        }
      } catch (Exception e) {
        log(e);
      }
    } else {
      final Color color = parseColor(value);
      final Integer invVal = getInteger(value);
      final Boolean boolVal = "true".equals(value) ? Boolean.TRUE : "false".equals(value) ? Boolean.FALSE : null;
      Icon icon = value.startsWith("AllIcons.") ? IconLoader.getIcon(value) : null;
      if (icon == null && value.endsWith(".png")) {
        icon = IconLoader.findIcon(value, DarculaLaf.class, true);
      }
      if (color != null) {
        return  new ColorUIResource(color);
      } else if (invVal != null) {
        return invVal;
      } else if (icon != null) {
        return new IconUIResource(icon);
      } else if (boolVal != null) {
        return boolVal;
      }
    }
    return value;
  }

  private static Insets parseInsets(String value) {
    final List<String> numbers = StringUtil.split(value, ",");
    return new InsetsUIResource(Integer.parseInt(numbers.get(0)),
                                           Integer.parseInt(numbers.get(1)),
                                           Integer.parseInt(numbers.get(2)),
                                           Integer.parseInt(numbers.get(3)));
  }

  @SuppressWarnings("UseJBColor")
  private static Color parseColor(String value) {
    if (value != null && value.length() == 8) {
      final Color color = ColorUtil.fromHex(value.substring(0, 6));
      if (color != null) {
        try {
          int alpha = Integer.parseInt(value.substring(6, 8), 16);
          return new ColorUIResource(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        } catch (Exception ignore){}
      }
      return null;
    }
    return ColorUtil.fromHex(value, null);
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
    return NAME;
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
    try {
      base.initialize();
    } catch (Exception ignore) {}
    myDisposable = Disposer.newDisposable();
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      Disposer.register(application, myDisposable);
    }
    myMnemonicAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, myDisposable);
    IdeEventQueue.getInstance().addDispatcher(e -> {
      if (e instanceof KeyEvent && ((KeyEvent)e).getKeyCode() == KeyEvent.VK_ALT) {
        myAltPressed = e.getID() == KeyEvent.KEY_PRESSED;
        myMnemonicAlarm.cancelAllRequests();
        final Component focusOwner = IdeFocusManager.findInstance().getFocusOwner();
        if (focusOwner != null) {
          myMnemonicAlarm.addRequest(() -> repaintMnemonics(focusOwner, myAltPressed), 10);
        }
      }
      return false;
    }, myDisposable);
  }

  public static boolean isAltPressed() {
    return myAltPressed;
  }

  private static void repaintMnemonics(@NotNull Component focusOwner, boolean pressed) {
    if (pressed != myAltPressed) return;
    Window window = SwingUtilities.windowForComponent(focusOwner);
    if (window != null) {
      for (Component component : window.getComponents()) {
        if (component instanceof JComponent) {
          for (JComponent c : UIUtil.findComponentsOfType((JComponent)component, JComponent.class)) {
            if ((c instanceof JLabel && ((JLabel)c).getDisplayedMnemonicIndex() != -1)
              || (c instanceof AbstractButton && ((AbstractButton)c).getDisplayedMnemonicIndex() != -1)
              ) {
              c.repaint();
            }
          }
        }
      }
    }
  }

  @Override
  public void uninitialize() {
    try {
      base.initialize();
    } catch (Exception ignore) {}
    Disposer.dispose(myDisposable);
    myDisposable = null;
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

  public static Icon loadIcon(String iconName) {
    return IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/" + iconName, DarculaLaf.class, true);
  }
}
