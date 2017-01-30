/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.ui

import com.intellij.ide.WelcomeWizardUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ComponentTreeEventDispatcher
import com.intellij.util.PlatformUtils
import com.intellij.util.SystemProperties
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.isValidFont
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.NonNls
import sun.swing.SwingUtilities2
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.SwingConstants

@State(name = "UISettings", storages = arrayOf(Storage("ui.lnf.xml")))
class UISettings : BaseState(), PersistentStateComponent<UISettings> {
  // These font properties should not be set in the default ctor,
  // so that to make the serialization logic judge if a property
  // should be stored or shouldn't by the provided filter only.
  @JvmField
  @Property(filter = FontFilter::class) var FONT_FACE: String? = null

  @JvmField
  @Property(filter = FontFilter::class) var FONT_SIZE: Int = 0

  @Property(filter = FontFilter::class) private var FONT_SCALE: Float = 0.toFloat()

  @get:OptionTag("SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY")
  var recentFilesLimit by storedProperty(50)

  @JvmField var CONSOLE_COMMAND_HISTORY_LIMIT = 300
  @JvmField var OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE = false
  @JvmField var CONSOLE_CYCLE_BUFFER_SIZE_KB = 1024
  @JvmField var EDITOR_TAB_LIMIT = 10

  @get:OptionTag("REUSE_NOT_MODIFIED_TABS")
  var reuseNotModifiedTabs by storedProperty(false)

  @JvmField var ANIMATE_WINDOWS = true
  @JvmField var ANIMATION_DURATION = 300 // Milliseconds
  @JvmField var SHOW_TOOL_WINDOW_NUMBERS = true
  @JvmField var HIDE_TOOL_STRIPES = true
  @JvmField var WIDESCREEN_SUPPORT = false
  @JvmField var LEFT_HORIZONTAL_SPLIT = false
  @JvmField var RIGHT_HORIZONTAL_SPLIT = false
  @JvmField var SHOW_EDITOR_TOOLTIP = true
  @JvmField var SHOW_MEMORY_INDICATOR = false
  @JvmField var ALLOW_MERGE_BUTTONS = true
  @JvmField var SHOW_MAIN_TOOLBAR = false
  @JvmField var SHOW_STATUS_BAR = true
  @JvmField var SHOW_NAVIGATION_BAR = true
  @JvmField var ALWAYS_SHOW_WINDOW_BUTTONS = false
  @JvmField var CYCLE_SCROLLING = true
  @JvmField var SCROLL_TAB_LAYOUT_IN_EDITOR = true
  @JvmField var HIDE_TABS_IF_NEED = true
  @JvmField var SHOW_CLOSE_BUTTON = true
  @JvmField var EDITOR_TAB_PLACEMENT = 1
  @JvmField var HIDE_KNOWN_EXTENSION_IN_TABS = false
  @JvmField var SHOW_ICONS_IN_QUICK_NAVIGATION = true
  @JvmField var CLOSE_NON_MODIFIED_FILES_FIRST = false
  @JvmField var ACTIVATE_MRU_EDITOR_ON_CLOSE = false
  @JvmField var ACTIVATE_RIGHT_EDITOR_ON_CLOSE = false
  @JvmField var IDE_AA_TYPE = AntialiasingType.SUBPIXEL
  @JvmField var EDITOR_AA_TYPE = AntialiasingType.SUBPIXEL
  @JvmField var COLOR_BLINDNESS: ColorBlindness? = null
  @JvmField var MOVE_MOUSE_ON_DEFAULT_BUTTON = false
  @JvmField var ENABLE_ALPHA_MODE = false
  @JvmField var ALPHA_MODE_DELAY = 1500
  @JvmField var ALPHA_MODE_RATIO = 0.5f
  @JvmField var MAX_CLIPBOARD_CONTENTS = 5
  @JvmField var OVERRIDE_NONIDEA_LAF_FONTS = false
  @JvmField var SHOW_ICONS_IN_MENUS = true
  @JvmField var DISABLE_MNEMONICS = SystemInfo.isMac // IDEADEV-33409, should be disabled by default on MacOS
  @JvmField var DISABLE_MNEMONICS_IN_CONTROLS = false
  @JvmField var USE_SMALL_LABELS_ON_TABS = SystemInfo.isMac
  @JvmField var MAX_LOOKUP_WIDTH2 = 500
  @JvmField var MAX_LOOKUP_LIST_HEIGHT = 11
  @JvmField var HIDE_NAVIGATION_ON_FOCUS_LOSS = true
  @JvmField var DND_WITH_PRESSED_ALT_ONLY = false
  @JvmField var DEFAULT_AUTOSCROLL_TO_SOURCE = false
  @Transient
  @JvmField var PRESENTATION_MODE = false
  @JvmField var PRESENTATION_MODE_FONT_SIZE = 24
  @JvmField var MARK_MODIFIED_TABS_WITH_ASTERISK = false
  @JvmField var SHOW_TABS_TOOLTIPS = true
  @JvmField var SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES = true
  @JvmField var NAVIGATE_TO_PREVIEW = false
  @JvmField var SORT_BOOKMARKS = false

  private val myTreeDispatcher = ComponentTreeEventDispatcher.create(UISettingsListener::class.java)

  @get:OptionTag("SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY")
  var sortLookupElementsLexicographically by storedProperty(false)

  @get:OptionTag("MERGE_EQUAL_STACKTRACES")
  var mergeEqualStackTraces by storedProperty(true)

  @get:OptionTag("SORT_BOOKMARKS")
  var sortBookmarks by storedProperty(false)

  init {
    tweakPlatformDefaults()

    val scrollToSource = WelcomeWizardUtil.getAutoScrollToSource()
    if (scrollToSource != null) {
      DEFAULT_AUTOSCROLL_TO_SOURCE = scrollToSource
    }
  }

  private fun withDefFont(): UISettings {
    initDefFont()
    return this
  }

  private fun tweakPlatformDefaults() {
    // TODO[anton] consider making all IDEs use the same settings
    if (PlatformUtils.isAppCode()) {
      SCROLL_TAB_LAYOUT_IN_EDITOR = true
      ACTIVATE_RIGHT_EDITOR_ON_CLOSE = true
      SHOW_ICONS_IN_MENUS = false
    }
  }


  @Deprecated("Please use {@link UISettingsListener#TOPIC}")
  fun addUISettingsListener(listener: UISettingsListener, parentDisposable: Disposable) {
    ApplicationManager.getApplication().messageBus.connect(parentDisposable).subscribe(UISettingsListener.TOPIC, listener)
  }

  /**
   * Notifies all registered listeners that UI settings has been changed.
   */
  fun fireUISettingsChanged() {
    val support = ColorBlindnessSupport.get(COLOR_BLINDNESS)
    IconLoader.setFilter(support?.filter)

    val app = ApplicationManager.getApplication()
    if (app != null && this == instance) {
      // if this is the main UISettings instance push event to bus and to all current components
      myTreeDispatcher.multicaster.uiSettingsChanged(this)
      app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(this)
    }
  }

  private fun initDefFont() {
    val fontData = systemFontFaceAndSize
    if (FONT_FACE == null) FONT_FACE = fontData.first
    if (FONT_SIZE <= 0) FONT_SIZE = fontData.second
    if (FONT_SCALE <= 0) FONT_SCALE = JBUI.scale(1f)
  }

  class FontFilter : SerializationFilter {
    override fun accepts(accessor: Accessor, bean: Any): Boolean {
      val settings = bean as UISettings
      val fontData = systemFontFaceAndSize
      if ("FONT_FACE" == accessor.name) {
        return fontData.first != settings.FONT_FACE
      }
      // store only in pair
      return !(fontData.second == settings.FONT_SIZE && 1f == settings.FONT_SCALE)
    }
  }

  override fun getState(): UISettings? {
    return this
  }

  override fun loadState(`object`: UISettings) {
    XmlSerializerUtil.copyBean(`object`, this)
    resetModificationCount()

    // Check tab placement in editor
    if (EDITOR_TAB_PLACEMENT != TABS_NONE &&
        EDITOR_TAB_PLACEMENT != SwingConstants.TOP &&
        EDITOR_TAB_PLACEMENT != SwingConstants.LEFT &&
        EDITOR_TAB_PLACEMENT != SwingConstants.BOTTOM &&
        EDITOR_TAB_PLACEMENT != SwingConstants.RIGHT) {
      EDITOR_TAB_PLACEMENT = SwingConstants.TOP
    }

    // Check that alpha delay and ratio are valid
    if (ALPHA_MODE_DELAY < 0) {
      ALPHA_MODE_DELAY = 1500
    }
    if (ALPHA_MODE_RATIO < 0.0f || ALPHA_MODE_RATIO > 1.0f) {
      ALPHA_MODE_RATIO = 0.5f
    }

    if (FONT_SCALE <= 0) {
      // Reset font to default on switch from IDEA-managed HiDPI to JDK-managed HiDPI. Doesn't affect OSX.
      if (UIUtil.isJDKManagedHiDPI() && !SystemInfo.isMac) FONT_SIZE = UIUtil.DEF_SYSTEM_FONT_SIZE.toInt()
    }
    else {
      FONT_SIZE = JBUI.scale(FONT_SIZE / FONT_SCALE).toInt()
    }
    FONT_SCALE = JBUI.scale(1f)
    initDefFont()

    // 1. Sometimes system font cannot display standard ASCII symbols. If so we have
    // find any other suitable font withing "preferred" fonts first.
    var fontIsValid = isValidFont(Font(FONT_FACE, Font.PLAIN, FONT_SIZE))
    if (!fontIsValid) {
      @NonNls val preferredFonts = arrayOf("dialog", "Arial", "Tahoma")
      for (preferredFont in preferredFonts) {
        if (isValidFont(Font(preferredFont, Font.PLAIN, FONT_SIZE))) {
          FONT_FACE = preferredFont
          fontIsValid = true
          break
        }
      }

      // 2. If all preferred fonts are not valid in current environment
      // we have to find first valid font (if any)
      if (!fontIsValid) {
        val fontNames = UIUtil.getValidFontNames(false)
        if (fontNames.isNotEmpty()) {
          FONT_FACE = fontNames[0]
        }
      }
    }

    if (MAX_CLIPBOARD_CONTENTS <= 0) {
      MAX_CLIPBOARD_CONTENTS = 5
    }

    fireUISettingsChanged()
  }

  companion object {
    /** Not tabbed pane.  */
    @JvmField
    val TABS_NONE = 0

    @JvmStatic
    val instance: UISettings
      get() = ServiceManager.getService(UISettings::class.java)

    /**
     * Use this method if you are not sure whether the application is initialized.
     * @return persisted UISettings instance or default values.
     */
    @JvmStatic
    val shadowInstance: UISettings
      get() {
        val app = ApplicationManager.getApplication()
        return (if (app == null) null else instance) ?: UISettings().withDefFont()
      }

    private val systemFontFaceAndSize: Pair<String, Int>
      get() {
        val fontData = UIUtil.getSystemFontData()
        if (fontData != null) {
          return fontData
        }

        return Pair.create("Dialog", 12)
      }

    @JvmField
    val FORCE_USE_FRACTIONAL_METRICS = SystemProperties.getBooleanProperty("idea.force.use.fractional.metrics", false)

    @JvmStatic
    fun setupFractionalMetrics(g2d: Graphics2D) {
      if (FORCE_USE_FRACTIONAL_METRICS) {
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
      }
    }

    /**
     * This method must not be used for set up antialiasing for editor components. To make sure antialiasing settings are taken into account
     * when preferred size of component is calculated, [.setupComponentAntialiasing] method should be called from
     * `updateUI()` or `setUI()` method of component.
     */
    @JvmStatic
    fun setupAntialiasing(g: Graphics) {
      val g2d = g as Graphics2D
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, UIUtil.getLcdContrastValue())

      val application = ApplicationManager.getApplication()
      if (application == null) {
        // We cannot use services while Application has not been loaded yet
        // So let's apply the default hints.
        UIUtil.applyRenderingHints(g)
        return
      }

      val uiSettings = ServiceManager.getService(UISettings::class.java)
      if (uiSettings != null) {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))
      }
      else {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
      }

      setupFractionalMetrics(g2d)
    }

  /**
   * @see #setupAntialiasing(Graphics)
   */
  public static void setupComponentAntialiasing(JComponent component) {
    component.putClientProperty(SwingUtilities2.AA_TEXT_PROPERTY_KEY, AntialiasingType.getAAHintForSwingComponent());
  }

  public static void setupEditorAntialiasing(JComponent component) {
    AntialiasingType aaType = getInstance().EDITOR_AA_TYPE;
    if (aaType != null) {
      component.putClientProperty(SwingUtilities2.AA_TEXT_PROPERTY_KEY, aaType.getTextInfo());
    }
  }
}