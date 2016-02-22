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
package com.intellij.ide.ui;

import com.intellij.ide.WelcomeWizardUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.EventDispatcher;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

import static com.intellij.util.ui.UIUtil.isValidFont;

@State(
  name = "UISettings",
  storages = @Storage("ui.lnf.xml")
)
public class UISettings extends SimpleModificationTracker implements PersistentStateComponent<UISettings> {
  /** Not tabbed pane. */
  public static final int TABS_NONE = 0;

  public static UISettings getInstance() {
    return ServiceManager.getService(UISettings.class);
  }

  /**
   * Use this method if you are not sure whether the application is initialized.
   * @return persisted UISettings instance or default values.
   */
  public static UISettings getShadowInstance() {
    Application application = ApplicationManager.getApplication();
    UISettings settings = application == null ? null : getInstance();
    return settings == null ? new UISettings() : settings;
  }

  @Property(filter = FontFilter.class) public String FONT_FACE;
  @Property(filter = FontFilter.class) public int FONT_SIZE;
  public int RECENT_FILES_LIMIT = 50;
  public int CONSOLE_COMMAND_HISTORY_LIMIT = 300;
  public boolean OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE = false;
  public int CONSOLE_CYCLE_BUFFER_SIZE_KB = 1024;
  public int EDITOR_TAB_LIMIT = 10;
  public boolean REUSE_NOT_MODIFIED_TABS = false;
  public boolean ANIMATE_WINDOWS = true;
  @Deprecated //todo remove in IDEA 16
  public int ANIMATION_SPEED = 4000; // Pixels per second
  public int ANIMATION_DURATION = 300; // Milliseconds
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
  public boolean SCROLL_TAB_LAYOUT_IN_EDITOR = true;
  public boolean HIDE_TABS_IF_NEED = true;
  public boolean SHOW_CLOSE_BUTTON = true;
  public int EDITOR_TAB_PLACEMENT = 1;
  public boolean HIDE_KNOWN_EXTENSION_IN_TABS = false;
  public boolean SHOW_ICONS_IN_QUICK_NAVIGATION = true;
  public boolean CLOSE_NON_MODIFIED_FILES_FIRST = false;
  public boolean ACTIVATE_MRU_EDITOR_ON_CLOSE = false;
  public boolean ACTIVATE_RIGHT_EDITOR_ON_CLOSE = false;
  public AntialiasingType IDE_AA_TYPE = AntialiasingType.SUBPIXEL;
  public AntialiasingType EDITOR_AA_TYPE = AntialiasingType.SUBPIXEL;
  public ColorBlindness COLOR_BLINDNESS; 
  public boolean USE_LCD_RENDERING_IN_EDITOR = true;
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
  public boolean DND_WITH_PRESSED_ALT_ONLY = false;
  public boolean FILE_COLORS_IN_PROJECT_VIEW = false;
  public boolean DEFAULT_AUTOSCROLL_TO_SOURCE = false;
  @Transient
  public boolean PRESENTATION_MODE = false;
  public int PRESENTATION_MODE_FONT_SIZE = 24;
  public boolean MARK_MODIFIED_TABS_WITH_ASTERISK = false;
  public boolean SHOW_TABS_TOOLTIPS = true;
  public boolean SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES = true;
  public boolean NAVIGATE_TO_PREVIEW = false;
  public boolean SORT_BOOKMARKS = false;
  public boolean MERGE_EQUAL_STACKTRACES = true;

  private final EventDispatcher<UISettingsListener> myDispatcher = EventDispatcher.create(UISettingsListener.class);

  public UISettings() {
    tweakPlatformDefaults();
    setSystemFontFaceAndSize();

    Boolean scrollToSource = WelcomeWizardUtil.getAutoScrollToSource();
    if (scrollToSource != null) {
      DEFAULT_AUTOSCROLL_TO_SOURCE = scrollToSource;
    }
  }

  private void tweakPlatformDefaults() {
    // TODO[anton] consider making all IDEs use the same settings
    if (PlatformUtils.isAppCode()) {
      SCROLL_TAB_LAYOUT_IN_EDITOR = true;
      ACTIVATE_RIGHT_EDITOR_ON_CLOSE = true;
      SHOW_ICONS_IN_MENUS = false;
    }
  }

  /**
   * @deprecated use {@link UISettings#addUISettingsListener(UISettingsListener, Disposable disposable)} instead.
   */
  public void addUISettingsListener(UISettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  public void addUISettingsListener(@NotNull final UISettingsListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  /**
   * Notifies all registered listeners that UI settings has been changed.
   */
  public void fireUISettingsChanged() {
    incModificationCount();
    myDispatcher.getMulticaster().uiSettingsChanged(this);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(this);
    IconLoader.setFilter(COLOR_BLINDNESS == ColorBlindness.protanopia
                         ? DaltonizationFilter.protanopia
                         : COLOR_BLINDNESS == ColorBlindness.deuteranopia
                           ? DaltonizationFilter.deuteranopia
                           : COLOR_BLINDNESS == ColorBlindness.tritanopia
                             ? DaltonizationFilter.tritanopia
                             : null);
  }

  public void removeUISettingsListener(UISettingsListener listener) {
    myDispatcher.removeListener(listener);
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

    return Pair.create("Dialog", 12);
  }

  public static class FontFilter implements SerializationFilter {
    @Override
    public boolean accepts(@NotNull Accessor accessor, @NotNull Object bean) {
      UISettings settings = (UISettings)bean;
      return !hasDefaultFontSetting(settings);
    }
  }

  private static boolean hasDefaultFontSetting(final UISettings settings) {
    final Pair<String, Integer> fontData = getSystemFontFaceAndSize();
    return fontData.first.equals(settings.FONT_FACE) && fontData.second.equals(settings.FONT_SIZE);
  }

  @Override
  public UISettings getState() {
    return this;
  }

  @Override
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

  public static final boolean FORCE_USE_FRACTIONAL_METRICS =
    SystemProperties.getBooleanProperty("idea.force.use.fractional.metrics", false);

  public static void setupFractionalMetrics(final Graphics2D g2d) {
    if (FORCE_USE_FRACTIONAL_METRICS) {
      g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
  }

  /* This method must not be used for set up antialiasing for editor components
   */
  public static void setupAntialiasing(final Graphics g) {

    Graphics2D g2d = (Graphics2D)g;
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST,  UIUtil.getLcdContrastValue());

    Application application = ApplicationManager.getApplication();
    if (application == null) {
      // We cannot use services while Application has not been loaded yet
      // So let's apply the default hints.
      UIUtil.applyRenderingHints(g);
      return;
    }

    UISettings uiSettings = getInstance();

    if (uiSettings != null) {
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false));
    } else {
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    }

      setupFractionalMetrics(g2d);
  }
}