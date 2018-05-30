// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UITheme;
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
import com.intellij.util.Alarm;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AppContext;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
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

/**
 * @author Konstantin Bulenkov
 */
public class DarculaLaf extends BasicLookAndFeel {
  private static final Object SYSTEM = new Object();
  public static final String NAME = "Darcula";
  BasicLookAndFeel base;

  protected Disposable myDisposable;
  private Alarm myMnemonicAlarm;
  private static boolean myAltPressed;

  public DarculaLaf() {}

  private static void installMacOSXFonts(UIDefaults defaults) {
    final String face = "HelveticaNeue-Regular";
    final FontUIResource uiFont = getFont(face, 13, Font.PLAIN);
    LafManagerImpl.initFontDefaults(defaults, uiFont);
    for (Object key : new HashSet<>(defaults.keySet())) {
      Object value = defaults.get(key);
      if (value instanceof FontUIResource) {
        FontUIResource font = (FontUIResource)value;
        if (font.getFamily().equals("Lucida Grande") || font.getFamily().equals("Serif")) {
          if (!key.toString().contains("Menu")) {
            defaults.put(key, getFont(face, font.getSize(), font.getStyle()));
          }
        }
      }
    }

    FontUIResource uiFont11 = getFont(face, 11, Font.PLAIN);
    defaults.put("TableHeader.font", uiFont11);

    FontUIResource buttonFont = getFont("HelveticaNeue-Medium", 13, Font.PLAIN);
    defaults.put("Button.font", buttonFont);
    Font menuFont = getFont("Lucida Grande", 14, Font.PLAIN);
    defaults.put("Menu.font", menuFont);
    defaults.put("MenuItem.font", menuFont);
    defaults.put("MenuItem.acceleratorFont", menuFont);
    defaults.put("PasswordField.font", defaults.getFont("TextField.font"));
  }

  @NotNull
  private static FontUIResource getFont(String yosemite, int size, @JdkConstants.FontStyle int style) {
    if (SystemInfo.isMacOSElCapitan) {
      // Text family should be used for relatively small sizes (<20pt), don't change to Display
      // see more about SF https://medium.com/@mach/the-secret-of-san-francisco-fonts-4b5295d9a745#.2ndr50z2v
      Font font = new Font(".SF NS Text", style, size);
      if (!UIUtil.isDialogFont(font)) {
        return new FontUIResource(font);
      }
    }
    return new FontUIResource(yosemite, style, size);
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
      final UIDefaults metalDefaults = new MetalLookAndFeel().getDefaults();
      final UIDefaults defaults = base.getDefaults();
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
      if (SystemInfo.isLinux && JBUI.isUsrHiDPI()) {
        applySystemFonts(defaults);
      }
      defaults.put("EditorPane.font", defaults.getFont("TextField.font"));
      if (SystemInfo.isMacOSYosemite) {
        installMacOSXFonts(defaults);
      }
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
    URL url = getClass().getResource(getPrefix() + (JBUI.isUsrHiDPI() ? "@2x.css" : ".css"));
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

  @NotNull
  protected String getPrefix() {
    return "darcula";
  }

  @Nullable
  protected String getSystemPrefix() {
    String osSuffix = SystemInfo.isMac ? "mac" : SystemInfo.isWindows ? "windows" : "linux";
    return getPrefix() + "_" + osSuffix;
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
    Properties properties = new Properties();
    try {
      InputStream stream = getClass().getResourceAsStream(getPrefix() + ".properties");
      properties.load(stream);
      stream.close();

      String systemPrefix = getSystemPrefix();
      if (StringUtil.isNotEmpty(systemPrefix)) {
        stream = getClass().getResourceAsStream(systemPrefix + ".properties");
        properties.load(stream);
        stream.close();
      }

      HashMap<String, Object> darculaGlobalSettings = new HashMap<>();
      final String prefix = getPrefix() + ".";
      for (String key : properties.stringPropertyNames()) {
        if (key.startsWith(prefix)) {
          Object value = parseValue(key, properties.getProperty(key));
          String darculaKey = key.substring(prefix.length());
          if (value == SYSTEM) {
            darculaGlobalSettings.remove(darculaKey);
          } else {
            darculaGlobalSettings.put(darculaKey, value);
          }
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
    if ("system".equals(value)) {
      return SYSTEM;
    }

    if (value.endsWith(".png") || value.endsWith(".svg")) {
      Icon icon = IconLoader.findIcon(value, DarculaLaf.class, true);
      if (icon != null) {
        return icon;
      }
    }

    return UITheme.parseValue(key, value);
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

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  @Override
  public void initialize() {
    myDisposable = Disposer.newDisposable();
    base = createBaseLookAndFeel();

    try {
      base.initialize();
    } catch (Exception ignore) {}
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      Disposer.register(application, myDisposable);
    }
    myMnemonicAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myDisposable);
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
}
