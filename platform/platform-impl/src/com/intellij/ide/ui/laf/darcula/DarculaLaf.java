// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.DarculaMetalTheme;
import com.intellij.ide.ui.laf.IdeaLaf;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.TableActions;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MultiResolutionImageProvider;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.function.Function;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaLaf extends BasicLookAndFeel implements UserDataHolder {
  private static final Object SYSTEM = new Object();
  public static final @NlsSafe String NAME = "Darcula";
  private static final @NlsSafe String DESCRIPTION = "IntelliJ Dark Look and Feel";
  private LookAndFeel base;
  private boolean isBaseInitialized;

  protected Disposable myDisposable;
  private final UserDataHolderBase myUserData = new UserDataHolderBase();
  private static boolean myAltPressed;

  public DarculaLaf(@NotNull LookAndFeel base) {
    this.base = base;
    isBaseInitialized = true;
  }

  public DarculaLaf() {
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
    Logger.getInstance(DarculaLaf.class).error(e);
  }

  private static @NotNull Font toFont(UIDefaults defaults, Object key) {
    Object value = defaults.get(key);
    if (value instanceof Font) {
      return (Font)value;
    }
    else if (value instanceof UIDefaults.ActiveValue) {
      value = ((UIDefaults.ActiveValue)value).createValue(defaults);
      if (value instanceof Font) {
        return (Font)value;
      }
    }
    throw new UnsupportedOperationException("Unable to extract Font from \"" + key + "\"");
  }

  @Override
  public UIDefaults getDefaults() {
    try {
      UIDefaults metalDefaults = new MetalLookAndFeel().getDefaults();
      UIDefaults defaults = base.getDefaults();

      if (SystemInfoRt.isLinux && Arrays.asList("CN", "JP", "KR", "TW").contains(Locale.getDefault().getCountry())) {
        defaults.keySet().stream().
          filter(key -> key.toString().endsWith(".font")).
          forEach(key -> {
            Font font = toFont(defaults, key);
            Font uiFont = new FontUIResource("Dialog", font.getStyle(), font.getSize());
            defaults.put(key, uiFont);
          });
      }

      StartupUiUtil.initInputMapDefaults(defaults);
      initIdeaDefaults(defaults);
      patchStyledEditorKit(defaults);
      patchComboBox(metalDefaults, defaults);
      defaults.remove("Spinner.arrowButtonBorder");
      defaults.put("Spinner.arrowButtonSize", JBUI.size(16, 5).asUIResource());
      MetalLookAndFeel.setCurrentTheme(createMetalTheme());
      if (SystemInfoRt.isLinux && JBUIScale.isUsrHiDPI()) {
        applySystemFonts(defaults);
      }
      if (SystemInfoRt.isMac) {
        defaults.put("RootPane.defaultButtonWindowKeyBindings", new Object[]{
          "ENTER", "press",
          "released ENTER", "release",
          "ctrl ENTER","press",
          "ctrl released ENTER","release",
          "meta ENTER", "press",
          "meta released ENTER", "release"
        });
      }
      defaults.put("EditorPane.font", toFont(defaults, "TextField.font"));
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
      Constructor<?> constructor = Class.forName(fqn).getDeclaredConstructor();
      constructor.setAccessible(true);
      Object systemLookAndFeel = constructor.newInstance();
      final Method superMethod = BasicLookAndFeel.class.getDeclaredMethod("getDefaults");
      superMethod.setAccessible(true);
      Map<Object, Object> systemDefaults = (UIDefaults)superMethod.invoke(systemLookAndFeel);
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

  private static void patchComboBox(UIDefaults metalDefaults, UIDefaults defaults) {
    defaults.remove("ComboBox.ancestorInputMap");
    defaults.remove("ComboBox.actionMap");
    defaults.put("ComboBox.ancestorInputMap", metalDefaults.get("ComboBox.ancestorInputMap"));
    defaults.put("ComboBox.actionMap", metalDefaults.get("ComboBox.actionMap"));
  }

  private void patchStyledEditorKit(UIDefaults defaults) {
    URL url = getClass().getResource(getPrefix() + (JBUIScale.isUsrHiDPI() ? "@2x.css" : ".css"));
    StyleSheet styleSheet = StartupUiUtil.loadStyleSheet(url);
    defaults.put("StyledEditorKit.JBDefaultStyle", styleSheet);
    try {
      Field keyField = HTMLEditorKit.class.getDeclaredField("DEFAULT_STYLES_KEY");
      keyField.setAccessible(true);
      AppContext.getAppContext().put(keyField.get(null), StartupUiUtil.loadStyleSheet(url));
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
    if (isLoadFromJsonEnabled()) {
      return null;
    }
    String osSuffix = SystemInfoRt.isMac ? "mac" : SystemInfoRt.isWindows ? "windows" : "linux";
    return getPrefix() + "_" + osSuffix;
  }

  @Override
  public void initComponentDefaults(UIDefaults defaults) {
    callInit("initComponentDefaults", defaults);
  }

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
    if (isLoadFromJsonEnabled()) {
      loadDefaultsFromJson(defaults);
    }
    else {
      loadDefaultsFromProperties(defaults);
    }
  }

  private static boolean isLoadFromJsonEnabled() {
    return Boolean.parseBoolean(System.getProperty("ide.load.laf.as.json", "true"));
  }

  protected void loadDefaultsFromJson(UIDefaults defaults) {
    loadDefaultsFromJson(defaults, getPrefix());
    if (getSystemPrefix() != null) {
      loadDefaultsFromJson(defaults, getSystemPrefix());
    }
  }

  private void loadDefaultsFromJson(UIDefaults defaults, String prefix) {
    String filename = prefix + ".theme.json";
    try (InputStream stream = getClass().getResourceAsStream(filename)) {
      assert stream != null : "Can't load " + filename;
      UITheme theme = UITheme.loadFromJson(stream, "Darcula", getClass().getClassLoader(), Function.identity());
      theme.applyProperties(defaults);
    }
    catch (IOException e) {
      log(e);
    }
  }

  protected void loadDefaultsFromProperties(UIDefaults defaults) {
    try {
      Map<String, String> map = new HashMap<>(300);
      //noinspection NonSynchronizedMethodOverridesSynchronizedMethod
      @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
      Properties properties = new Properties() {
        @Override
        public Object put(Object key, Object value) {
          return map.put((String)key, (String)value);
        }
      };
      try (InputStream stream = getClass().getResourceAsStream(getPrefix() + ".properties")) {
        properties.load(stream);
      }

      String systemPrefix = getSystemPrefix();
      if (systemPrefix != null && !systemPrefix.isEmpty()) {
        try (InputStream stream = getClass().getResourceAsStream(systemPrefix + ".properties")) {
          properties.load(stream);
        }
      }

      Map<String, Object> darculaGlobalSettings = new HashMap<>(32);
      String prefix = getPrefix();
      prefix = prefix.substring(prefix.lastIndexOf("/") + 1) + ".";

      for (String key : map.keySet()) {
        if (key.startsWith(prefix)) {
          Object value = parseValue(key, map.get(key));
          String darculaKey = key.substring(prefix.length());
          if (value == SYSTEM) {
            darculaGlobalSettings.remove(darculaKey);
          }
          else {
            darculaGlobalSettings.put(darculaKey, value);
          }
        }
      }

      UIDefaults multiUiDefaults = UIManager.getDefaults();
      for (Object key : defaults.keySet()) {
        if (key instanceof String && ((String)key).contains(".")) {
          String s = (String)key;
          String darculaKey = s.substring(s.lastIndexOf('.') + 1);
          if (darculaGlobalSettings.containsKey(darculaKey)) {
            // MultiUIDefaults misses correct property merging
            multiUiDefaults.remove(key);
            defaults.put(key, darculaGlobalSettings.get(darculaKey));
          }
        }
      }

      for (Map.Entry<String, String> entry : map.entrySet()) {
        // MultiUIDefaults misses correct property merging
        multiUiDefaults.remove(entry.getKey());
        defaults.put(entry.getKey(), parseValue(entry.getKey(), entry.getValue()));
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

    return UITheme.parseValue(key, value, getClass().getClassLoader());
  }

  @Override
  @Nls(capitalization = Nls.Capitalization.Title)
  public String getName() {
    return NAME;
  }

  @Override
  public String getID() {
    return getName();
  }

  @Override
  public String getDescription() {
    return DESCRIPTION;
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
    if (!isBaseInitialized) {
      try {
        if (base == null) {
          base = createBaseLaF();
        }
        base.initialize();
      }
      catch (Throwable e) {
        Logger.getInstance(DarculaLaf.class).error(e);
      }
    }

    if (LoadingState.LAF_INITIALIZED.isOccurred()) {
      ideEventQueueInitialized(IdeEventQueue.getInstance());
    }
  }

  @ApiStatus.Internal
  public static @NotNull LookAndFeel createBaseLaF() throws Throwable {
    if (SystemInfoRt.isMac) {
      Class<?> aClass = DarculaLaf.class.getClassLoader().loadClass(UIManager.getSystemLookAndFeelClassName());
      return (BasicLookAndFeel)MethodHandles.lookup().findConstructor(aClass, MethodType.methodType(void.class)).invoke();
    }
    else {
      return new IdeaLaf();
    }
  }

  @ApiStatus.Internal
  public void appCreated(@NotNull Disposable parent) {
    if (myDisposable != null) {
      Disposer.register(parent, myDisposable);
    }
  }

  @ApiStatus.Internal
  public final void ideEventQueueInitialized(@NotNull IdeEventQueue eventQueue) {
    if (myDisposable == null) {
      myDisposable = Disposer.newDisposable();
      if (LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
        Application application = ApplicationManager.getApplication();
        if (application != null) {
          Disposer.register(application, myDisposable);
        }
      }
    }

    eventQueue.addDispatcher(new IdeEventQueue.EventDispatcher() {
      private Alarm mnemonicAlarm;

      @Override
      public boolean dispatch(@NotNull AWTEvent e) {
        if (!(e instanceof KeyEvent) || ((KeyEvent)e).getKeyCode() != KeyEvent.VK_ALT) {
          return false;
        }

        //noinspection AssignmentToStaticFieldFromInstanceMethod
        myAltPressed = e.getID() == KeyEvent.KEY_PRESSED;
        Alarm mnemonicAlarm = this.mnemonicAlarm;
        if (mnemonicAlarm == null) {
          mnemonicAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myDisposable);
          this.mnemonicAlarm = mnemonicAlarm;
        }

        mnemonicAlarm.cancelAllRequests();
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner != null) {
          mnemonicAlarm.addRequest(() -> repaintMnemonics(focusOwner, myAltPressed), 10);
        }
        return false;
      }
    }, myDisposable);
  }

  public static boolean isAltPressed() {
    return myAltPressed;
  }

  private static void repaintMnemonics(@NotNull Component focusOwner, boolean pressed) {
    if (pressed != myAltPressed) {
      return;
    }

    Window window = SwingUtilities.windowForComponent(focusOwner);
    if (window == null) {
      return;
    }

    for (Component component : window.getComponents()) {
      if (component instanceof JComponent) {
        for (JComponent c : ComponentUtil.findComponentsOfType((JComponent)component, JComponent.class)) {
          if ((c instanceof JLabel && ((JLabel)c).getDisplayedMnemonicIndex() != -1) ||
              (c instanceof AbstractButton && ((AbstractButton)c).getDisplayedMnemonicIndex() != -1)) {
            c.repaint();
          }
        }
      }
    }
  }

  @Override
  public void uninitialize() {
    try {
      isBaseInitialized = false;
      base.uninitialize();
    }
    catch (Exception ignore) {
    }

    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
      myDisposable = null;
    }
  }

  @Override
  protected final void loadSystemColors(UIDefaults defaults, String[] systemColors, boolean useNative) {
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
  public final Icon getDisabledIcon(JComponent component, Icon icon) {
    if (icon == null) {
      return null;
    }

    ScaleContext ctx = ScaleContext.create(component);
    icon = MultiResolutionImageProvider.convertFromJBIcon(icon, ctx);
    Icon disabledIcon = super.getDisabledIcon(component, icon);
    disabledIcon = MultiResolutionImageProvider.convertFromMRIcon(disabledIcon, ctx);

    if (disabledIcon != null) {
      return disabledIcon;
    }
    return IconLoader.getDisabledIcon(icon, component);
  }

  @Override
  public final boolean getSupportsWindowDecorations() {
    return true;
  }
}
