// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

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

@State(name = "UISettings", storages = [(Storage("ui.lnf.xml"))])
class UISettings : BaseState(), PersistentStateComponent<UISettings> {
  // These font properties should not be set in the default ctor,
  // so that to make the serialization logic judge if a property
  // should be stored or shouldn't by the provided filter only.
  @get:Property(filter = FontFilter::class)
  @get:OptionTag("FONT_FACE")
  var fontFace: String? by string()

  @get:Property(filter = FontFilter::class)
  @get:OptionTag("FONT_SIZE")
  var fontSize: Int by property(defFontSize)

  @get:Property(filter = FontFilter::class)
  @get:OptionTag("FONT_SCALE")
  var fontScale: Float by property(0f)

  @get:OptionTag("RECENT_FILES_LIMIT") var recentFilesLimit: Int by property(50)
  @get:OptionTag("CONSOLE_COMMAND_HISTORY_LIMIT") var consoleCommandHistoryLimit: Int by property(300)
  @get:OptionTag("OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE") var overrideConsoleCycleBufferSize: Boolean by property(false)
  @get:OptionTag("CONSOLE_CYCLE_BUFFER_SIZE_KB") var consoleCycleBufferSizeKb: Int by property(1024)
  @get:OptionTag("EDITOR_TAB_LIMIT") var editorTabLimit: Int by property(10)

  @get:OptionTag("REUSE_NOT_MODIFIED_TABS") var reuseNotModifiedTabs: Boolean by property(false)
  @get:OptionTag("ANIMATE_WINDOWS") var animateWindows: Boolean by property(true)
  @get:OptionTag("SHOW_TOOL_WINDOW_NUMBERS") var showToolWindowsNumbers: Boolean by property(true)
  @get:OptionTag("HIDE_TOOL_STRIPES") var hideToolStripes: Boolean by property(false)
  @get:OptionTag("WIDESCREEN_SUPPORT") var wideScreenSupport: Boolean by property(false)
  @get:OptionTag("LEFT_HORIZONTAL_SPLIT") var leftHorizontalSplit: Boolean by property(false)
  @get:OptionTag("RIGHT_HORIZONTAL_SPLIT") var rightHorizontalSplit: Boolean by property(false)
  @get:OptionTag("SHOW_EDITOR_TOOLTIP") var showEditorToolTip: Boolean by property(true)
  @get:OptionTag("SHOW_MEMORY_INDICATOR") var showMemoryIndicator: Boolean by property(false)
  @get:OptionTag("ALLOW_MERGE_BUTTONS") var allowMergeButtons: Boolean by property(true)
  @get:OptionTag("SHOW_MAIN_TOOLBAR") var showMainToolbar: Boolean by property(false)
  @get:OptionTag("SHOW_STATUS_BAR") var showStatusBar: Boolean by property(true)
  @get:OptionTag("SHOW_NAVIGATION_BAR") var showNavigationBar: Boolean by property(true)
  @get:OptionTag("ALWAYS_SHOW_WINDOW_BUTTONS") var alwaysShowWindowsButton: Boolean by property(false)
  @get:OptionTag("CYCLE_SCROLLING") var cycleScrolling: Boolean by property(true)
  @get:OptionTag("SCROLL_TAB_LAYOUT_IN_EDITOR") var scrollTabLayoutInEditor: Boolean by property(true)
  @get:OptionTag("HIDE_TABS_IF_NEED") var hideTabsIfNeed: Boolean by property(true)
  @get:OptionTag("SHOW_CLOSE_BUTTON") var showCloseButton: Boolean by property(true)
  @get:OptionTag("CLOSE_TAB_BUTTON_ON_THE_RIGHT") var closeTabButtonOnTheRight: Boolean by property(true)
  @get:OptionTag("EDITOR_TAB_PLACEMENT") var editorTabPlacement: Int by property(SwingConstants.TOP)
  @get:OptionTag("HIDE_KNOWN_EXTENSION_IN_TABS") var hideKnownExtensionInTabs: Boolean by property(false)
  @get:OptionTag("SHOW_ICONS_IN_QUICK_NAVIGATION") var showIconInQuickNavigation: Boolean by property(true)

  @get:OptionTag("CLOSE_NON_MODIFIED_FILES_FIRST") var closeNonModifiedFilesFirst: Boolean by property(false)
  @get:OptionTag("ACTIVATE_MRU_EDITOR_ON_CLOSE") var activeMruEditorOnClose: Boolean by property(false)
  // TODO[anton] consider making all IDEs use the same settings
  @get:OptionTag("ACTIVATE_RIGHT_EDITOR_ON_CLOSE") var activeRightEditorOnClose: Boolean by property(PlatformUtils.isAppCode())

  @get:OptionTag("IDE_AA_TYPE") var ideAAType: AntialiasingType by property(AntialiasingType.SUBPIXEL)
  @get:OptionTag("EDITOR_AA_TYPE") var editorAAType: AntialiasingType by property(AntialiasingType.SUBPIXEL)
  @get:OptionTag("COLOR_BLINDNESS") var colorBlindness: ColorBlindness? by property<ColorBlindness?>()
  @get:OptionTag("MOVE_MOUSE_ON_DEFAULT_BUTTON") var moveMouseOnDefaultButton: Boolean by property(false)
  @get:OptionTag("ENABLE_ALPHA_MODE") var enableAlphaMode: Boolean by property(false)
  @get:OptionTag("ALPHA_MODE_DELAY") var alphaModeDelay: Int by property(1500)
  @get:OptionTag("ALPHA_MODE_RATIO") var alphaModeRatio: Float by property(0.5f)
  @get:OptionTag("MAX_CLIPBOARD_CONTENTS") var maxClipboardContents: Int by property(5)
  @get:OptionTag("OVERRIDE_NONIDEA_LAF_FONTS") var overrideLafFonts: Boolean by property(false)
  @get:OptionTag("SHOW_ICONS_IN_MENUS") var showIconsInMenus: Boolean by property(!PlatformUtils.isAppCode())
  // IDEADEV-33409, should be disabled by default on MacOS
  @get:OptionTag("DISABLE_MNEMONICS") var disableMnemonics: Boolean by property(SystemInfo.isMac)
  @get:OptionTag("DISABLE_MNEMONICS_IN_CONTROLS") var disableMnemonicsInControls: Boolean by property(false)
  @get:OptionTag("USE_SMALL_LABELS_ON_TABS") var useSmallLabelsOnTabs: Boolean by property(SystemInfo.isMac)
  @get:OptionTag("MAX_LOOKUP_WIDTH2") var maxLookupWidth: Int by property(500)
  @get:OptionTag("MAX_LOOKUP_LIST_HEIGHT") var maxLookupListHeight: Int by property(11)
  @get:OptionTag("HIDE_NAVIGATION_ON_FOCUS_LOSS") var hideNavigationOnFocusLoss: Boolean by property(true)
  @get:OptionTag("DND_WITH_PRESSED_ALT_ONLY") var dndWithPressedAltOnly: Boolean by property(false)
  @get:OptionTag("DEFAULT_AUTOSCROLL_TO_SOURCE") var defaultAutoScrollToSource: Boolean by property(false)
  @Transient var presentationMode: Boolean = false
  @get:OptionTag("PRESENTATION_MODE_FONT_SIZE") var presentationModeFontSize: Int by property(24)
  @get:OptionTag("MARK_MODIFIED_TABS_WITH_ASTERISK") var markModifiedTabsWithAsterisk: Boolean by property(false)
  @get:OptionTag("SHOW_TABS_TOOLTIPS") var showTabsTooltips: Boolean by property(true)
  @get:OptionTag("SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES") var showDirectoryForNonUniqueFilenames: Boolean by property(true)
  var smoothScrolling: Boolean by property(SystemInfo.isMac && (SystemInfo.isJetBrainsJvm || SystemInfo.IS_AT_LEAST_JAVA9))
  @get:OptionTag("NAVIGATE_TO_PREVIEW") var navigateToPreview: Boolean by property(false)

  @get:OptionTag("SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY") var sortLookupElementsLexicographically: Boolean by property(false)
  @get:OptionTag("MERGE_EQUAL_STACKTRACES") var mergeEqualStackTraces: Boolean by property(true)
  @get:OptionTag("SORT_BOOKMARKS") var sortBookmarks: Boolean by property(false)
  @get:OptionTag("PIN_FIND_IN_PATH_POPUP") var pinFindInPath: Boolean by property(false)

  private val myTreeDispatcher = ComponentTreeEventDispatcher.create(UISettingsListener::class.java)

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

  override fun getState(): UISettings = this

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

    init {
      verbose("defFontSize=%d, defFontScale=%.2f", defFontSize, defFontScale)
    }

    @JvmStatic
    private fun verbose(msg: String, vararg args: Any) = if (JBUI.SCALE_VERBOSE) LOG.info(String.format(msg, *args)) else {}

    const val ANIMATION_DURATION: Int = 300 // Milliseconds

    /** Not tabbed pane.  */
    const val TABS_NONE: Int = 0

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
    val FORCE_USE_FRACTIONAL_METRICS: Boolean = SystemProperties.getBooleanProperty("idea.force.use.fractional.metrics", false)

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
      GraphicsUtil.setAntialiasingType(component, instance.editorAAType.textInfo)
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
        verbose("Reset font to default")
        // Reset font to default on switch from IDE-managed HiDPI to JRE-managed HiDPI. Doesn't affect OSX.
        if (UIUtil.isJreHiDPIEnabled() && !SystemInfo.isMac) size = defFontSize
      }
      else {
        var oldDefFontScale = defFontScale
        if (SystemInfo.isLinux) {
          val fdata = UIUtil.getSystemFontData()
          if (fdata != null) {
            // [tav] todo: temp workaround for transitioning IDEA 173 to 181
            // not converting fonts stored with scale equal to the old calculation
            oldDefFontScale = fdata.second / 12f
            verbose("oldDefFontScale=%.2f", oldDefFontScale)
          }
        }
        if (readScale != defFontScale && readScale != oldDefFontScale) size = Math.round((readSize / readScale) * defFontScale)
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
  var HIDE_TOOL_STRIPES: Boolean = true

  @Suppress("unused")
  @Deprecated("Use consoleCommandHistoryLimit", replaceWith = ReplaceWith("consoleCommandHistoryLimit"))
  @JvmField
  @Transient
  var CONSOLE_COMMAND_HISTORY_LIMIT: Int = 300

  @Suppress("unused")
  @Deprecated("Use cycleScrolling", replaceWith = ReplaceWith("cycleScrolling"))
  @JvmField
  @Transient
  var CYCLE_SCROLLING: Boolean = true

  @Suppress("unused")
  @Deprecated("Use showMainToolbar", replaceWith = ReplaceWith("showMainToolbar"))
  @JvmField
  @Transient
  var SHOW_MAIN_TOOLBAR: Boolean = false

  @Suppress("unused")
  @Deprecated("Use showCloseButton", replaceWith = ReplaceWith("showCloseButton"))
  @JvmField
  @Transient
  var SHOW_CLOSE_BUTTON: Boolean = true

  @Suppress("unused")
  @Deprecated("Use editorAAType", replaceWith = ReplaceWith("editorAAType"))
  @JvmField
  @Transient
  var EDITOR_AA_TYPE: AntialiasingType? = AntialiasingType.SUBPIXEL

  @Suppress("unused")
  @Deprecated("Use presentationMode", replaceWith = ReplaceWith("presentationMode"))
  @JvmField
  @Transient
  var PRESENTATION_MODE: Boolean = false

  @Suppress("unused")
  @Deprecated("Use overrideLafFonts", replaceWith = ReplaceWith("overrideLafFonts"))
  @JvmField
  @Transient
  var OVERRIDE_NONIDEA_LAF_FONTS: Boolean = false

  @Suppress("unused")
  @Deprecated("Use presentationModeFontSize", replaceWith = ReplaceWith("presentationModeFontSize"))
  @JvmField
  @Transient
  var PRESENTATION_MODE_FONT_SIZE: Int = 24

  @Suppress("unused")
  @Deprecated("Use editorTabLimit", replaceWith = ReplaceWith("editorTabLimit"))
  @JvmField
  @Transient
  var EDITOR_TAB_LIMIT: Int = editorTabLimit

  @Suppress("unused")
  @Deprecated("Use overrideConsoleCycleBufferSize", replaceWith = ReplaceWith("overrideConsoleCycleBufferSize"))
  @JvmField
  @Transient
  var OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE: Boolean = false

  @Suppress("unused")
  @Deprecated("Use consoleCycleBufferSizeKb", replaceWith = ReplaceWith("consoleCycleBufferSizeKb"))
  @JvmField
  @Transient
  var CONSOLE_CYCLE_BUFFER_SIZE_KB: Int = consoleCycleBufferSizeKb
  //</editor-fold>
}