/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PlatformUtilsCore;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.io.File;
import java.util.Map;

import static com.intellij.util.ui.UIUtil.isValidFont;

@State(
  name = "UISettings",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/ui.lnf.xml"
    )}
)
public class UISettings implements PersistentStateComponent<UISettings>, ExportableApplicationComponent {
  private final EventListenerList myListenerList;

  @Property(filter = FontFilter.class)
  @NonNls
  public String FONT_FACE;
  @Property(filter = FontFilter.class)
  public int FONT_SIZE;

  public int RECENT_FILES_LIMIT = 50;
  public int CONSOLE_COMMAND_HISTORY_LIMIT = 300;
  public int EDITOR_TAB_LIMIT = 10;
  public boolean ANIMATE_WINDOWS = true;
  public int ANIMATION_SPEED = 2000; // Pixels per second
  public boolean SHOW_TOOL_WINDOW_NUMBERS = true;
  public boolean HIDE_TOOL_STRIPES = true;
  public boolean WIDESCREEN_SUPPORT = false;
  public boolean LEFT_HORIZONTAL_SPLIT = false;
  public boolean RIGHT_HORIZONTAL_SPLIT = false;
  public boolean SHOW_EDITOR_TOOLTIP = true;
  public boolean SHOW_MEMORY_INDICATOR = false;
  public boolean ALLOW_MERGE_BUTTONS = true;
  public boolean SHOW_MAIN_TOOLBAR = false;
  public boolean SHOW_STATUS_BAR = true;
  public boolean SHOW_NAVIGATION_BAR = true;
  public boolean ALWAYS_SHOW_WINDOW_BUTTONS = false;
  public boolean CYCLE_SCROLLING = true;
  public boolean SCROLL_TAB_LAYOUT_IN_EDITOR = PlatformUtilsCore.isAppCode();
  public boolean SHOW_CLOSE_BUTTON = true;
  public int EDITOR_TAB_PLACEMENT = 1;
  public boolean HIDE_KNOWN_EXTENSION_IN_TABS = false;
  public boolean SHOW_ICONS_IN_QUICK_NAVIGATION = true;
  public boolean CLOSE_NON_MODIFIED_FILES_FIRST = false;
  public boolean ACTIVATE_MRU_EDITOR_ON_CLOSE = false;
  public boolean ACTIVATE_RIGHT_EDITOR_ON_CLOSE = PlatformUtilsCore.isAppCode();
  public boolean ANTIALIASING_IN_EDITOR = true;
  public boolean MOVE_MOUSE_ON_DEFAULT_BUTTON = false;
  public boolean ENABLE_ALPHA_MODE = false;
  public int ALPHA_MODE_DELAY = 1500;
  public float ALPHA_MODE_RATIO = 0.5f;
  public int MAX_CLIPBOARD_CONTENTS = 5;
  public boolean OVERRIDE_NONIDEA_LAF_FONTS = false;
  public boolean SHOW_ICONS_IN_MENUS = true;
  public boolean DISABLE_MNEMONICS = SystemInfo.isMac; // IDEADEV-33409, should be disabled by default on MacOS
  public boolean DISABLE_MNEMONICS_IN_CONTROLS = false;
  public boolean USE_SMALL_LABELS_ON_TABS = SystemInfo.isMac;
  public boolean SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY = false;
  public int MAX_LOOKUP_WIDTH2 = 500;
  public int MAX_LOOKUP_LIST_HEIGHT = 11;
  public boolean HIDE_NAVIGATION_ON_FOCUS_LOSS = true;
  public boolean FILE_COLORS_IN_PROJECT_VIEW = false;
  public boolean DEFAULT_AUTOSCROLL_TO_SOURCE = false;
  public boolean PRESENTATION_MODE = false;
  public int PRESENTATION_MODE_FONT_SIZE = 24;

  /**
   * Defines whether asterisk is shown on modified editor tab or not
   */
  public boolean MARK_MODIFIED_TABS_WITH_ASTERISK = false;

  public boolean SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES = false;

  /**
   * Not tabbed pane
   */
  public static final int TABS_NONE = 0;

  public UISettings() {
    myListenerList = new EventListenerList();
    tweakPlatformDefaults();
    setSystemFontFaceAndSize();
  }

  private void tweakPlatformDefaults() {
    // TODO: Make it pluggable
    if (PlatformUtilsCore.isAppCode()) {
      SHOW_MAIN_TOOLBAR = false;
      SHOW_ICONS_IN_MENUS = false;
      SHOW_MEMORY_INDICATOR = false;
    }
  }

  /**
   * @deprecated use {@link UISettings#addUISettingsListener(com.intellij.ide.ui.UISettingsListener, Disposable disposable)} instead.
   */
  public void addUISettingsListener(UISettingsListener listener) {
    myListenerList.add(UISettingsListener.class, listener);
  }

  public void addUISettingsListener(@NotNull final UISettingsListener listener, @NotNull Disposable parentDisposable) {
    myListenerList.add(UISettingsListener.class,listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeUISettingsListener(listener);
      }
    });
  }

  /**
   * Notifies all registered listeners that UI settings has been changed.
   */
  public void fireUISettingsChanged() {
    UISettingsListener[] listeners= myListenerList.getListeners(UISettingsListener.class);
    for (UISettingsListener listener : listeners) {
      listener.uiSettingsChanged(this);
    }
  }

  public static UISettings getInstance() {
    return ApplicationManager.getApplication().getComponent(UISettings.class);
  }

  /**
   * Use this method if you are not sure is application initialized or not
   * @return UISettings instance or default values
   */
  public static UISettings getShadowInstance() {
    Application application = ApplicationManager.getApplication();
    return application != null ? getInstance() : new UISettings();
  }

  public void removeUISettingsListener(UISettingsListener listener) {
    myListenerList.remove(UISettingsListener.class,listener);
  }

  private void setSystemFontFaceAndSize() {
    if (FONT_FACE == null || FONT_SIZE <= 0) {
      final Pair<String, Integer> fontData = getSystemFontFaceAndSize();
      FONT_FACE = fontData.first;
      FONT_SIZE = fontData.second;
    }
  }

  private static Pair<String, Integer> getSystemFontFaceAndSize() {
    final Pair<String,Integer> fontData = UIUtil.getSystemFontData();
    if (fontData != null) {
      return fontData;
    }

    if (SystemInfo.isWindows) {
      //noinspection HardCodedStringLiteral
      final Font font = (Font)Toolkit.getDefaultToolkit().getDesktopProperty("win.messagebox.font");
      if (font != null) {
        return Pair.create(font.getName(), font.getSize());
      }
    }

    return Pair.create("Dialog", 12);
  }

  public static class FontFilter implements SerializationFilter {
    public boolean accepts(Accessor accessor, Object bean) {
      UISettings settings = (UISettings)bean;
      return !hasDefaultFontSetting(settings);
    }
  }

  private static boolean hasDefaultFontSetting(final UISettings settings) {
    final Pair<String, Integer> fontData = getSystemFontFaceAndSize();
    return fontData.first.equals(settings.FONT_FACE) && fontData.second.equals(settings.FONT_SIZE);
  }

  public UISettings getState() {
    return this;
  }

  public void loadState(UISettings object) {
    XmlSerializerUtil.copyBean(object, this);

    // Check tab placement in editor
    if (EDITOR_TAB_PLACEMENT != TABS_NONE &&
        EDITOR_TAB_PLACEMENT != SwingConstants.TOP &&
        EDITOR_TAB_PLACEMENT != SwingConstants.LEFT &&
        EDITOR_TAB_PLACEMENT != SwingConstants.BOTTOM &&
        EDITOR_TAB_PLACEMENT != SwingConstants.RIGHT) {
      EDITOR_TAB_PLACEMENT = SwingConstants.TOP;
    }

    // Check that alpha delay and ratio are valid
    if (ALPHA_MODE_DELAY < 0) {
      ALPHA_MODE_DELAY = 1500;
    }
    if (ALPHA_MODE_RATIO < 0.0f || ALPHA_MODE_RATIO > 1.0f) {
      ALPHA_MODE_RATIO = 0.5f;
    }

    setSystemFontFaceAndSize();
    // 1. Sometimes system font cannot display standard ASCII symbols. If so we have
    // find any other suitable font withing "preferred" fonts first.
    boolean fontIsValid = isValidFont(new Font(FONT_FACE, Font.PLAIN, FONT_SIZE));
    if (!fontIsValid) {
      @NonNls final String[] preferredFonts = {"dialog", "Arial", "Tahoma"};
      for (String preferredFont : preferredFonts) {
        if (isValidFont(new Font(preferredFont, Font.PLAIN, FONT_SIZE))) {
          FONT_FACE = preferredFont;
          fontIsValid = true;
          break;
        }
      }

      // 2. If all preferred fonts are not valid in current environment
      // we have to find first valid font (if any)
      if (!fontIsValid) {
        String[] fontNames = UIUtil.getValidFontNames(false);
        if (fontNames.length > 0) {
          FONT_FACE = fontNames[0];
        }
      }
    }

    if (MAX_CLIPBOARD_CONTENTS <= 0) {
      MAX_CLIPBOARD_CONTENTS = 5;
    }

    fireUISettingsChanged();
  }

  private static final boolean DEFAULT_ALIASING             =
    SystemProperties.getBooleanProperty("idea.use.default.antialiasing.in.editor", false);
  private static final boolean FORCE_USE_FRACTIONAL_METRICS =
    SystemProperties.getBooleanProperty("idea.force.use.fractional.metrics", false);

  public static void setupAntialiasing(final Graphics g) {
    if (DEFAULT_ALIASING) return;

    Graphics2D g2d = (Graphics2D)g;
    UISettings uiSettings = getInstance();

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    if (!isRemoteDesktopConnected() && UIUtil.isRetina()) {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
    else {
      if (uiSettings == null || uiSettings.ANTIALIASING_IN_EDITOR) {
        Toolkit tk = Toolkit.getDefaultToolkit();
        //noinspection HardCodedStringLiteral
        Map map = (Map)tk.getDesktopProperty("awt.font.desktophints");
        if (map != null) {
          if (isRemoteDesktopConnected()) {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT);
          }
          else {
            g2d.addRenderingHints(map);
          }
        }
        else {
          g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
        if (FORCE_USE_FRACTIONAL_METRICS) {
          g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        }
      }
      else {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
      }
    }
  }

  /**
   * @return true when Remote Desktop (i.e. Windows RDP) is connected
   */
  // TODO[neuro]: move to UIUtil
  public static boolean isRemoteDesktopConnected() {
    if (System.getProperty("os.name").contains("Windows")) {
      final Map map = (Map)Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
      return map != null && RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT.equals(map.get(RenderingHints.KEY_TEXT_ANTIALIASING));
    }
    return false;
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("ui.lnf")};
  }

  @NotNull
  public String getPresentableName() {
    return IdeBundle.message("ui.settings");
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "UISettings";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
