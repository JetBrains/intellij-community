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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ComponentTreeEventDispatcher
import com.intellij.util.PlatformUtils
import com.intellij.util.SystemProperties
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.isValidFont
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Transient
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
  @get:Property(filter = FontFilter::class)
  @get:OptionTag("FONT_FACE")
  var fontFace by string()

  @get:Property(filter = FontFilter::class)
  @get:OptionTag("FONT_SIZE")
  var fontSize by storedProperty(defFontSize)

  @get:Property(filter = FontFilter::class)
  @get:OptionTag("FONT_SCALE")
  var fontScale by storedProperty(0f)

  @get:OptionTag("RECENT_FILES_LIMIT") var recentFilesLimit by storedProperty(50)
  @get:OptionTag("CONSOLE_COMMAND_HISTORY_LIMIT") var consoleCommandHistoryLimit by storedProperty(300)
  @get:OptionTag("OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE") var overrideConsoleCycleBufferSize by storedProperty(false)
  @get:OptionTag("CONSOLE_CYCLE_BUFFER_SIZE_KB") var consoleCycleBufferSizeKb by storedProperty(1024)
  @get:OptionTag("EDITOR_TAB_LIMIT") var editorTabLimit by storedProperty(10)

  @get:OptionTag("REUSE_NOT_MODIFIED_TABS") var reuseNotModifiedTabs by storedProperty(false)
  @get:OptionTag("ANIMATE_WINDOWS") var animateWindows by storedProperty(true)
  @get:OptionTag("SHOW_TOOL_WINDOW_NUMBERS") var showToolWindowsNumbers by storedProperty(true)
  @get:OptionTag("HIDE_TOOL_STRIPES") var hideToolStripes by storedProperty(true)
  @get:OptionTag("WIDESCREEN_SUPPORT") var wideScreenSupport by storedProperty(false)
  @get:OptionTag("LEFT_HORIZONTAL_SPLIT") var leftHorizontalSplit by storedProperty(false)
  @get:OptionTag("RIGHT_HORIZONTAL_SPLIT") var rightHorizontalSplit by storedProperty(false)
  @get:OptionTag("SHOW_EDITOR_TOOLTIP") var showEditorToolTip by storedProperty(true)
  @get:OptionTag("SHOW_MEMORY_INDICATOR") var showMemoryIndicator by storedProperty(false)
  @get:OptionTag("ALLOW_MERGE_BUTTONS") var allowMergeButtons by storedProperty(true)
  @get:OptionTag("SHOW_MAIN_TOOLBAR") var showMainToolbar by storedProperty(false)
  @get:OptionTag("SHOW_STATUS_BAR") var showStatusBar by storedProperty(true)
  @get:OptionTag("SHOW_NAVIGATION_BAR") var showNavigationBar by storedProperty(true)
  @get:OptionTag("ALWAYS_SHOW_WINDOW_BUTTONS") var alwaysShowWindowsButton by storedProperty(false)
  @get:OptionTag("CYCLE_SCROLLING") var cycleScrolling by storedProperty(true)
  @get:OptionTag("SCROLL_TAB_LAYOUT_IN_EDITOR") var scrollTabLayoutInEditor by storedProperty(true)
  @get:OptionTag("HIDE_TABS_IF_NEED") var hideTabsIfNeed by storedProperty(true)
  @get:OptionTag("SHOW_CLOSE_BUTTON") var showCloseButton by storedProperty(true)
  @get:OptionTag("EDITOR_TAB_PLACEMENT") var editorTabPlacement by storedProperty(1)
  @get:OptionTag("HIDE_KNOWN_EXTENSION_IN_TABS") var hideKnownExtensionInTabs by storedProperty(false)
  @get:OptionTag("SHOW_ICONS_IN_QUICK_NAVIGATION") var showIconInQuickNavigation by storedProperty(true)

  @get:OptionTag("CLOSE_NON_MODIFIED_FILES_FIRST") var closeNonModifiedFilesFirst by storedProperty(false)
  @get:OptionTag("ACTIVATE_MRU_EDITOR_ON_CLOSE") var activeMruEditorOnClose by storedProperty(false)
  // TODO[anton] consider making all IDEs use the same settings
  @get:OptionTag("ACTIVATE_RIGHT_EDITOR_ON_CLOSE") var activeRightEditorOnClose by storedProperty(PlatformUtils.isAppCode())

  @get:OptionTag("IDE_AA_TYPE") var ideAAType by storedProperty(AntialiasingType.SUBPIXEL)
  @get:OptionTag("EDITOR_AA_TYPE") var editorAAType by storedProperty(AntialiasingType.SUBPIXEL)
  @get:OptionTag("COLOR_BLINDNESS") var colorBlindness by storedProperty<ColorBlindness?>()
  @get:OptionTag("MOVE_MOUSE_ON_DEFAULT_BUTTON") var moveMouseOnDefaultButton by storedProperty(false)
  @get:OptionTag("ENABLE_ALPHA_MODE") var enableAlphaMode by storedProperty(false)
  @get:OptionTag("ALPHA_MODE_DELAY") var alphaModeDelay by storedProperty(1500)
  @get:OptionTag("ALPHA_MODE_RATIO") var alphaModeRatio by storedProperty(0.5f)
  @get:OptionTag("MAX_CLIPBOARD_CONTENTS") var maxClipboardContents by storedProperty(5)
  @get:OptionTag("OVERRIDE_NONIDEA_LAF_FONTS") var overrideLafFonts by storedProperty(false)
  @get:OptionTag("SHOW_ICONS_IN_MENUS") var showIconsInMenus by storedProperty(!PlatformUtils.isAppCode())
  // IDEADEV-33409, should be disabled by default on MacOS
  @get:OptionTag("DISABLE_MNEMONICS") var disableMnemonics by storedProperty(SystemInfo.isMac)
  @get:OptionTag("DISABLE_MNEMONICS_IN_CONTROLS") var disableMnemonicsInControls by storedProperty(false)
  @get:OptionTag("USE_SMALL_LABELS_ON_TABS") var useSmallLabelsOnTabs by storedProperty(SystemInfo.isMac)
  @get:OptionTag("MAX_LOOKUP_WIDTH2") var maxLookupWidth by storedProperty(500)
  @get:OptionTag("MAX_LOOKUP_LIST_HEIGHT") var maxLookupListHeight by storedProperty(11)
  @get:OptionTag("HIDE_NAVIGATION_ON_FOCUS_LOSS") var hideNavigationOnFocusLoss by storedProperty(true)
  @get:OptionTag("DND_WITH_PRESSED_ALT_ONLY") var dndWithPressedAltOnly by storedProperty(false)
  @get:OptionTag("DEFAULT_AUTOSCROLL_TO_SOURCE") var defaultAutoScrollToSource by storedProperty(false)
  @Transient var presentationMode = false
  @get:OptionTag("PRESENTATION_MODE_FONT_SIZE") var presentationModeFontSize by storedProperty(24)
  @get:OptionTag("MARK_MODIFIED_TABS_WITH_ASTERISK") var markModifiedTabsWithAsterisk by storedProperty(false)
  @get:OptionTag("SHOW_TABS_TOOLTIPS") var showTabsTooltips by storedProperty(true)
  @get:OptionTag("SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES") var showDirectoryForNonUniqueFilenames by storedProperty(true)
  var smoothScrolling by storedProperty(SystemInfo.isMac && (SystemInfo.isJetBrainsJvm || SystemInfo.isJavaVersionAtLeast("9")))
  @get:OptionTag("NAVIGATE_TO_PREVIEW") var navigateToPreview by storedProperty(false)

  @get:OptionTag("SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY") var sortLookupElementsLexicographically by storedProperty(false)
  @get:OptionTag("MERGE_EQUAL_STACKTRACES") var mergeEqualStackTraces by storedProperty(true)
  @get:OptionTag("SORT_BOOKMARKS") var sortBookmarks by storedProperty(false)
  @get:OptionTag("PIN_FIND_IN_PATH_POPUP") var pinFindInPath by storedProperty(false)

  private val myTreeDispatcher = ComponentTreeEventDispatcher.create(UISettingsListener::class.java)

  init {
    WelcomeWizardUtil.getAutoScrollToSource()?.let {
      defaultAutoScrollToSource = it
    }
  }

  private fun withDefFont(): UISettings {
    initDefFont()
    return this
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Please use {@link UISettingsListener#TOPIC}")
  fun addUISettingsListener(listener: UISettingsListener, parentDisposable: Disposable) {
    ApplicationManager.getApplication().messageBus.connect(parentDisposable).subscribe(UISettingsListener.TOPIC, listener)
  }

  /**
   * Notifies all registered listeners that UI settings has been changed.
   */
  fun fireUISettingsChanged() {
    updateDeprecatedProperties()

    // todo remove when all old properties will be converted
    incrementModificationCount()

    IconLoader.setFilter(ColorBlindnessSupport.get(colorBlindness)?.filter)

    // if this is the main UISettings instance (and not on first call to getInstance) push event to bus and to all current components
    if (this === _instance) {
      myTreeDispatcher.multicaster.uiSettingsChanged(this)
      ApplicationManager.getApplication().messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(this)
    }
  }

  @Suppress("DEPRECATION")
  private fun updateDeprecatedProperties() {
    HIDE_TOOL_STRIPES = hideToolStripes
    SHOW_MAIN_TOOLBAR = showMainToolbar
    CYCLE_SCROLLING = cycleScrolling
    SHOW_CLOSE_BUTTON = showCloseButton
    EDITOR_AA_TYPE = editorAAType
    PRESENTATION_MODE = presentationMode
    OVERRIDE_NONIDEA_LAF_FONTS = overrideLafFonts
    PRESENTATION_MODE_FONT_SIZE = presentationModeFontSize
    CONSOLE_COMMAND_HISTORY_LIMIT = consoleCommandHistoryLimit
    FONT_SIZE = fontSize
    FONT_FACE = fontFace
    EDITOR_TAB_LIMIT = editorTabLimit
    OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE = overrideConsoleCycleBufferSize
    CONSOLE_CYCLE_BUFFER_SIZE_KB = consoleCycleBufferSizeKb
  }

  private fun initDefFont() {
    val fontData = systemFontFaceAndSize
    if (fontFace == null) fontFace = fontData.first
    if (fontSize <= 0) fontSize = fontData.second
    if (fontScale <= 0) fontScale = defFontScale
  }

  class FontFilter : SerializationFilter {
    override fun accepts(accessor: Accessor, bean: Any): Boolean {
      val settings = bean as UISettings
      val fontData = systemFontFaceAndSize
      if ("fontFace" == accessor.name) {
        return fontData.first != settings.fontFace
      }
      // fontSize/fontScale should either be stored in pair or not stored at all
      // otherwise the fontSize restore logic gets broken (see loadState)
      return !(fontData.second == settings.fontSize && 1f == settings.fontScale)
    }
  }

  override fun getState() = this

  override fun loadState(state: UISettings) {
    XmlSerializerUtil.copyBean(state, this)
    resetModificationCount()
    updateDeprecatedProperties()

    // Check tab placement in editor
    if (editorTabPlacement != TABS_NONE &&
        editorTabPlacement != SwingConstants.TOP &&
        editorTabPlacement != SwingConstants.LEFT &&
        editorTabPlacement != SwingConstants.BOTTOM &&
        editorTabPlacement != SwingConstants.RIGHT) {
      editorTabPlacement = SwingConstants.TOP
    }

    // Check that alpha delay and ratio are valid
    if (alphaModeDelay < 0) {
      alphaModeDelay = 1500
    }
    if (alphaModeRatio < 0.0f || alphaModeRatio > 1.0f) {
      alphaModeRatio = 0.5f
    }

    fontSize = restoreFontSize(fontSize, fontScale)
    fontScale = defFontScale
    initDefFont()

    // 1. Sometimes system font cannot display standard ASCII symbols. If so we have
    // find any other suitable font withing "preferred" fonts first.
    var fontIsValid = isValidFont(Font(fontFace, Font.PLAIN, fontSize))
    if (!fontIsValid) {
      for (preferredFont in arrayOf("dialog", "Arial", "Tahoma")) {
        if (isValidFont(Font(preferredFont, Font.PLAIN, fontSize))) {
          fontFace = preferredFont
          fontIsValid = true
          break
        }
      }

      // 2. If all preferred fonts are not valid in current environment
      // we have to find first valid font (if any)
      if (!fontIsValid) {
        val fontNames = UIUtil.getValidFontNames(false)
        if (fontNames.isNotEmpty()) {
          fontFace = fontNames[0]
        }
      }
    }

    if (maxClipboardContents <= 0) {
      maxClipboardContents = 5
    }

    fireUISettingsChanged()
  }

  companion object {
    private val LOG = Logger.getInstance(UISettings::class.java)

    const val ANIMATION_DURATION = 300 // Milliseconds

    /** Not tabbed pane.  */
    const val TABS_NONE = 0

    private @Volatile var _instance: UISettings? = null

    @JvmStatic
    val instance: UISettings
      get() = instanceOrNull!!

    @JvmStatic
    val instanceOrNull: UISettings?
      get() {
        var result = _instance
        if (result == null) {
          if (ApplicationManager.getApplication() == null) {
            return null
          }

          result = ServiceManager.getService(UISettings::class.java)
          _instance = result
        }
        return result
      }

    /**
     * Use this method if you are not sure whether the application is initialized.
     * @return persisted UISettings instance or default values.
     */
    @JvmStatic
    val shadowInstance: UISettings
      get() {
        val app = ApplicationManager.getApplication()
        return (if (app == null) null else instanceOrNull) ?: UISettings().withDefFont()
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
    @JvmStatic
    fun setupComponentAntialiasing(component: JComponent) {
      com.intellij.util.ui.GraphicsUtil.setAntialiasingType(component, AntialiasingType.getAAHintForSwingComponent())
    }

    @JvmStatic
    fun setupEditorAntialiasing(component: JComponent) {
      instance.editorAAType?.let { GraphicsUtil.setAntialiasingType(component, it.textInfo) }
    }

    /**
     * Returns the default font scale, which depends on the HiDPI mode (see JBUI#ScaleType).
     * <p>
     * The font is represented:
     * - in relative (dpi-independent) points in the JRE-managed HiDPI mode, so the method returns 1.0f
     * - in absolute (dpi-dependent) points in the IDE-managed HiDPI mode, so the method returns the default screen scale
     *
     * @return the system font scale
     */
    @JvmStatic
    val defFontScale: Float
      get() = if (UIUtil.isJreHiDPIEnabled()) 1f else JBUI.sysScale()

    /**
     * Returns the default font size scaled by #defFontScale
     *
     * @return the default scaled font size
     */
    @JvmStatic
    val defFontSize: Int
      get() = Math.round(UIUtil.DEF_SYSTEM_FONT_SIZE * defFontScale)

    @JvmStatic
    fun restoreFontSize(readSize: Int, readScale: Float?): Int {
      var size = readSize
      if (readScale == null || readScale <= 0) {
        // Reset font to default on switch from IDE-managed HiDPI to JRE-managed HiDPI. Doesn't affect OSX.
        if (UIUtil.isJreHiDPIEnabled() && !SystemInfo.isMac) size = defFontSize
      }
      else {
        if (readScale != defFontScale) size = Math.round((readSize / readScale) * defFontScale)
      }
      LOG.info("Loaded: fontSize=$readSize, fontScale=$readScale; restored: fontSize=$size, fontScale=$defFontScale")
      return size
    }
  }

  //<editor-fold desc="Deprecated stuff.">
  @Suppress("unused")
  @Deprecated("Use fontFace", replaceWith = ReplaceWith("fontFace"))
  @JvmField
  @Transient
  var FONT_FACE: String? = null

  @Suppress("unused")
  @Deprecated("Use fontSize", replaceWith = ReplaceWith("fontSize"))
  @JvmField
  @Transient
  var FONT_SIZE: Int? = 0

  @Suppress("unused")
  @Deprecated("Use hideToolStripes", replaceWith = ReplaceWith("hideToolStripes"))
  @JvmField
  @Transient
  var HIDE_TOOL_STRIPES = true

  @Suppress("unused")
  @Deprecated("Use consoleCommandHistoryLimit", replaceWith = ReplaceWith("consoleCommandHistoryLimit"))
  @JvmField
  @Transient
  var CONSOLE_COMMAND_HISTORY_LIMIT = 300

  @Suppress("unused")
  @Deprecated("Use cycleScrolling", replaceWith = ReplaceWith("cycleScrolling"))
  @JvmField
  @Transient
  var CYCLE_SCROLLING = true

  @Suppress("unused")
  @Deprecated("Use showMainToolbar", replaceWith = ReplaceWith("showMainToolbar"))
  @JvmField
  @Transient
  var SHOW_MAIN_TOOLBAR = false

  @Suppress("unused")
  @Deprecated("Use showCloseButton", replaceWith = ReplaceWith("showCloseButton"))
  @JvmField
  @Transient
  var SHOW_CLOSE_BUTTON = true

  @Suppress("unused")
  @Deprecated("Use editorAAType", replaceWith = ReplaceWith("editorAAType"))
  @JvmField
  @Transient
  var EDITOR_AA_TYPE: AntialiasingType? = AntialiasingType.SUBPIXEL

  @Suppress("unused")
  @Deprecated("Use presentationMode", replaceWith = ReplaceWith("presentationMode"))
  @JvmField
  @Transient
  var PRESENTATION_MODE = false

  @Suppress("unused")
  @Deprecated("Use overrideLafFonts", replaceWith = ReplaceWith("overrideLafFonts"))
  @JvmField
  @Transient
  var OVERRIDE_NONIDEA_LAF_FONTS = false

  @Suppress("unused")
  @Deprecated("Use presentationModeFontSize", replaceWith = ReplaceWith("presentationModeFontSize"))
  @JvmField
  @Transient
  var PRESENTATION_MODE_FONT_SIZE = 24

  @Suppress("unused")
  @Deprecated("Use editorTabLimit", replaceWith = ReplaceWith("editorTabLimit"))
  @JvmField
  @Transient
  var EDITOR_TAB_LIMIT = editorTabLimit

  @Suppress("unused")
  @Deprecated("Use overrideConsoleCycleBufferSize", replaceWith = ReplaceWith("overrideConsoleCycleBufferSize"))
  @JvmField
  @Transient
  var OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE = false

  @Suppress("unused")
  @Deprecated("Use consoleCycleBufferSizeKb", replaceWith = ReplaceWith("consoleCycleBufferSizeKb"))
  @JvmField
  @Transient
  var CONSOLE_CYCLE_BUFFER_SIZE_KB = consoleCycleBufferSizeKb
  //</editor-fold>
}