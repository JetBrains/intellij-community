// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.DarculaMetalTheme;
import com.intellij.ide.ui.laf.IdeaLaf;
import com.intellij.ide.ui.laf.LafManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TableActions;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
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
public class DarculaLaf extends BasicLookAndFeel implements UserDataHolder {
  private static final Object SYSTEM = new Object();
  public static final String NAME = "Darcula";
  BasicLookAndFeel base;

  protected Disposable myDisposable;
  private Alarm myMnemonicAlarm;
  private final UserDataHolderBase myUserData = new UserDataHolderBase();
  private static boolean myAltPressed;

  public DarculaLaf() {}

  protected BasicLookAndFeel createBaseLookAndFeel() {
    try {
      if (SystemInfo.isMac) {
        final String name = UIManager.getSystemLookAndFeelClassName();
        return (BasicLookAndFeel)Class.forName(name).newInstance();
      }
      else {
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

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myUserData.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myUserData.putUserData(key, value);
  }

  protected static void log(Exception e) {
    // everything is gonna be alright
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
        }
        else if (Arrays.asList("CN", "JP", "KR", "TW").contains(Locale.getDefault().getCountry())) {
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
      if (SystemInfo.isLinux && JBUIScale.isUsrHiDPI()) {
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
      String fqn = StartupUiUtil.getSystemLookAndFeelClassName();
      Object systemLookAndFeel = Class.forName(fqn).newInstance();
      final Method superMethod = BasicLookAndFeel.class.getDeclaredMethod("getDefaults");
      superMethod.setAccessible(true);
      final UIDefaults systemDefaults = (UIDefaults)superMethod.invoke(systemLookAndFeel);
      for (Map.Entry<Object, Object> entry : systemDefaults.entrySet()) {
        if (entry.getValue() instanceof Font) {
          defaults.put(entry.getKey(), entry.getValue());
        }
      }
    }
    catch (Exception e) {
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

  private void patchStyledEditorKit(UIDefaults defaults) {
    URL url = getClass().getResource(getPrefix() + (JBUIScale.isUsrHiDPI() ? "@2x.css" : ".css"));
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

  @Override
  public void initComponentDefaults(UIDefaults defaults) {
    callInit("initComponentDefaults", defaults);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  protected void initIdeaDefaults(UIDefaults defaults) {
    loadDefaults(defaults);
    defaults.put("Table.ancestorInputMap", new UIDefaults.LazyInputMap(new Object[]{
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
      "RIGHT", TableActions.Right.ID,
      "KP_RIGHT", TableActions.Right.ID,
      "LEFT", TableActions.Left.ID,
      "KP_LEFT", TableActions.Left.ID,
      "DOWN", TableActions.Down.ID,
      "KP_DOWN", TableActions.Down.ID,
      "UP", TableActions.Up.ID,
      "KP_UP", TableActions.Up.ID,
      "shift RIGHT", TableActions.ShiftRight.ID,
      "shift KP_RIGHT", TableActions.ShiftRight.ID,
      "shift LEFT", TableActions.ShiftLeft.ID,
      "shift KP_LEFT", TableActions.ShiftLeft.ID,
      "shift DOWN", TableActions.ShiftDown.ID,
      "shift KP_DOWN", TableActions.ShiftDown.ID,
      "shift UP", TableActions.ShiftUp.ID,
      "shift KP_UP", TableActions.ShiftUp.ID,
      "PAGE_UP", TableActions.PageUp.ID,
      "PAGE_DOWN", TableActions.PageDown.ID,
      "HOME", "selectFirstColumn",
      "END", "selectLastColumn",
      "shift PAGE_UP", TableActions.ShiftPageUp.ID,
      "shift PAGE_DOWN", TableActions.ShiftPageDown.ID,
      "shift HOME", "selectFirstColumnExtendSelection",
      "shift END", "selectLastColumnExtendSelection",
      "ctrl PAGE_UP", "scrollLeftChangeSelection",
      "ctrl PAGE_DOWN", "scrollRightChangeSelection",
      "ctrl HOME", TableActions.CtrlHome.ID,
      "ctrl END", TableActions.CtrlEnd.ID,
      "ctrl shift PAGE_UP", "scrollRightExtendSelection",
      "ctrl shift PAGE_DOWN", "scrollLeftExtendSelection",
      "ctrl shift HOME", TableActions.CtrlShiftHome.ID,
      "ctrl shift END", TableActions.CtrlShiftEnd.ID,
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

  protected void loadDefaults(UIDefaults defaults) {
    Properties properties = new Properties();
    try {
      try (InputStream stream = getClass().getResourceAsStream(getPrefix() + ".properties")) {
        properties.load(stream);
      }

      String systemPrefix = getSystemPrefix();
      if (StringUtil.isNotEmpty(systemPrefix)) {
        try (InputStream stream = getClass().getResourceAsStream(systemPrefix + ".properties")) {
          properties.load(stream);
        }
      }

      HashMap<String, Object> darculaGlobalSettings = new HashMap<>();
      final String prefix = getPrefix() + ".";
      for (String key : properties.stringPropertyNames()) {
        if (key.startsWith(prefix)) {
          Object value = parseValue(key, properties.getProperty(key));
          String darculaKey = key.substring(prefix.length());
          if (value == SYSTEM) {
            darculaGlobalSettings.remove(darculaKey);
          }
          else {
            darculaGlobalSettings.put(darculaKey, value);
          }
        }
      }

      for (Object key : defaults.keySet()) {
        if (key instanceof String && ((String)key).contains(".")) {
          final String s = (String)key;
          final String darculaKey = s.substring(s.lastIndexOf('.') + 1);
          if (darculaGlobalSettings.containsKey(darculaKey)) {
            UIManager.getDefaults().remove(key); // MultiUIDefaults misses correct property merging
            defaults.put(key, darculaGlobalSettings.get(darculaKey));
          }
        }
      }

      for (String key : properties.stringPropertyNames()) {
        UIManager.getDefaults().remove(key); // MultiUIDefaults misses correct property merging
        defaults.put(key, parseValue(key, properties.getProperty(key)));
      }
    }
    catch (IOException e) {
      log(e);
    }
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
    }
    catch (Exception ignore) {
    }
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      Disposer.register(application, myDisposable);
    }
    myMnemonicAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myDisposable);
    IdeEventQueue.getInstance().addDispatcher(e -> {
      if (e instanceof KeyEvent && ((KeyEvent)e).getKeyCode() == KeyEvent.VK_ALT) {
        myAltPressed = e.getID() == KeyEvent.KEY_PRESSED;
        myMnemonicAlarm.cancelAllRequests();
        final Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
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
    }
    catch (Exception ignore) {
    }
    Disposer.dispose(myDisposable);
    myDisposable = null;
  }

  @Override
  protected void loadSystemColors(UIDefaults defaults, String[] systemColors, boolean useNative) {
    try {
      Method superMethod = BasicLookAndFeel.class.getDeclaredMethod("loadSystemColors",
                                                                    UIDefaults.class,
                                                                    String[].class,
                                                                    boolean.class);
      superMethod.setAccessible(true);
      superMethod.invoke(base, defaults, systemColors, useNative);
    }
    catch (Exception e) {
      log(e);
    }
  }


  @Override
  public boolean getSupportsWindowDecorations() {
    return true;
  }
}
