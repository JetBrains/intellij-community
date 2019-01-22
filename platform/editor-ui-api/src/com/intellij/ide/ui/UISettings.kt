// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("PropertyName")

package com.intellij.ide.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ComponentTreeEventDispatcher
import com.intellij.util.SystemProperties
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.annotations.Transient
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.SwingConstants

private val LOG = logger<UISettings>()

@State(name = "UISettings", storages = [(Storage("ui.lnf.xml"))], reportStatistic = true)
class UISettings @JvmOverloads constructor(private val notRoamableOptions: NotRoamableUiSettings = NotRoamableUiSettings()) : PersistentStateComponent<UISettingsState> {
  private var state = UISettingsState()

  private val myTreeDispatcher = ComponentTreeEventDispatcher.create(UISettingsListener::class.java)

  var ideAAType: AntialiasingType
    get() = notRoamableOptions.state.ideAAType
    set(value) {
      notRoamableOptions.state.ideAAType = value
    }

  var editorAAType: AntialiasingType
    get() = notRoamableOptions.state.editorAAType
    set(value) {
      notRoamableOptions.state.editorAAType = value
    }

  var allowMergeButtons: Boolean
    get() = state.allowMergeButtons
    set(value) {
      state.allowMergeButtons = value
    }

  val alwaysShowWindowsButton: Boolean
    get() = state.alwaysShowWindowsButton

  var animateWindows: Boolean
    get() = state.animateWindows
    set(value) {
      state.animateWindows = value
    }

  var showMemoryIndicator: Boolean
    get() = state.showMemoryIndicator
    set(value) {
      state.showMemoryIndicator = value
    }

  var colorBlindness: ColorBlindness?
    get() = state.colorBlindness
    set(value) {
      state.colorBlindness = value
    }

  var hideToolStripes: Boolean
    get() = state.hideToolStripes
    set(value) {
      state.hideToolStripes = value
    }

  var hideNavigationOnFocusLoss: Boolean
    get() = state.hideNavigationOnFocusLoss
    set(value) {
      state.hideNavigationOnFocusLoss = value
    }

  var reuseNotModifiedTabs: Boolean
    get() = state.reuseNotModifiedTabs
    set(value) {
      state.reuseNotModifiedTabs = value
    }

  val maxClipboardContents: Int
    get() = state.maxClipboardContents

  var disableMnemonics: Boolean
    get() = state.disableMnemonics
    set(value) {
      state.disableMnemonics = value
    }

  var disableMnemonicsInControls: Boolean
    get() = state.disableMnemonicsInControls
    set(value) {
      state.disableMnemonicsInControls = value
    }

  var dndWithPressedAltOnly: Boolean
    get() = state.dndWithPressedAltOnly
    set(value) {
      state.dndWithPressedAltOnly = value
    }

  var useSmallLabelsOnTabs: Boolean
    get() = state.useSmallLabelsOnTabs
    set(value) {
      state.useSmallLabelsOnTabs = value
    }

  val smoothScrolling: Boolean
    get() = state.smoothScrolling

  val closeTabButtonOnTheRight: Boolean
    get() = state.closeTabButtonOnTheRight

  var cycleScrolling: Boolean
    get() = state.cycleScrolling
    set(value) {
      state.cycleScrolling = value
    }

  var navigateToPreview: Boolean
    get() = state.navigateToPreview
    set(value) {
      state.navigateToPreview = value
    }

  val scrollTabLayoutInEditor: Boolean
    get() = state.scrollTabLayoutInEditor

  var showToolWindowsNumbers: Boolean
    get() = state.showToolWindowsNumbers
    set(value) {
      state.showToolWindowsNumbers = value
    }

  var showEditorToolTip: Boolean
    get() = state.showEditorToolTip
    set(value) {
      state.showEditorToolTip = value
    }

  var showNavigationBar: Boolean
    get() = state.showNavigationBar
    set(value) {
      state.showNavigationBar = value
    }

  var showStatusBar: Boolean
    get() = state.showStatusBar
    set(value) {
      state.showStatusBar = value
    }

  var showIconInQuickNavigation: Boolean
    get() = state.showIconInQuickNavigation
    set(value) {
      state.showIconInQuickNavigation = value
    }

  var moveMouseOnDefaultButton: Boolean
    get() = state.moveMouseOnDefaultButton
    set(value) {
      state.moveMouseOnDefaultButton = value
    }

  var showMainToolbar: Boolean
    get() = state.showMainToolbar
    set(value) {
      state.showMainToolbar = value
    }

  var showIconsInMenus: Boolean
    get() = state.showIconsInMenus
    set(value) {
      state.showIconsInMenus = value
    }

  var sortLookupElementsLexicographically: Boolean
    get() = state.sortLookupElementsLexicographically
    set(value) {
      state.sortLookupElementsLexicographically = value
    }

  val hideTabsIfNeed: Boolean
    get() = state.hideTabsIfNeed

  var hideKnownExtensionInTabs: Boolean
    get() = state.hideKnownExtensionInTabs
    set(value) {
      state.hideKnownExtensionInTabs = value
    }

  var leftHorizontalSplit: Boolean
    get() = state.leftHorizontalSplit
    set(value) {
      state.leftHorizontalSplit = value
    }

  var rightHorizontalSplit: Boolean
    get() = state.rightHorizontalSplit
    set(value) {
      state.rightHorizontalSplit = value
    }

  var wideScreenSupport: Boolean
    get() = state.wideScreenSupport
    set(value) {
      state.wideScreenSupport = value
    }

  var sortBookmarks: Boolean
    get() = state.sortBookmarks
    set(value) {
      state.sortBookmarks = value
    }

  val showCloseButton: Boolean
    get() = state.showCloseButton

  var presentationMode: Boolean
    get() = state.presentationMode
    set(value) {
      state.presentationMode = value
    }

  val presentationModeFontSize: Int
    get() = state.presentationModeFontSize

  var editorTabPlacement: Int
    get() = state.editorTabPlacement
    set(value) {
      state.editorTabPlacement = value
    }

  var editorTabLimit: Int
    get() = state.editorTabLimit
    set(value) {
      state.editorTabLimit = value
    }

  val recentFilesLimit: Int
    get() = state.recentFilesLimit

  var maxLookupWidth: Int
    get() = state.maxLookupWidth
    set(value) {
      state.maxLookupWidth = value
    }

  var maxLookupListHeight: Int
    get() = state.maxLookupListHeight
    set(value) {
      state.maxLookupListHeight = value
    }

  var overrideLafFonts: Boolean
    get() = state.overrideLafFonts
    set(value) {
      state.overrideLafFonts = value
    }

  var fontFace: String?
    get() = notRoamableOptions.state.fontFace
    set(value) {
      notRoamableOptions.state.fontFace = value
    }

  var fontSize: Int
    get() = notRoamableOptions.state.fontSize
    set(value) {
      notRoamableOptions.state.fontSize = value
    }

  var fontScale: Float
    get() = notRoamableOptions.state.fontScale
    set(value) {
      notRoamableOptions.state.fontScale = value
    }

  var showDirectoryForNonUniqueFilenames: Boolean
    get() = state.showDirectoryForNonUniqueFilenames
    set(value) {
      state.showDirectoryForNonUniqueFilenames = value
    }

  var pinFindInPath: Boolean
    get() = state.pinFindInPath
    set(value) {
      state.pinFindInPath = value
    }

  var activeRightEditorOnClose: Boolean
    get() = state.activeRightEditorOnClose
    set(value) {
      state.activeRightEditorOnClose = value
    }

  var showTabsTooltips: Boolean
    get() = state.showTabsTooltips
    set(value) {
      state.showTabsTooltips = value
    }

  var markModifiedTabsWithAsterisk: Boolean
    get() = state.markModifiedTabsWithAsterisk
    set(value) {
      state.markModifiedTabsWithAsterisk = value
    }

  @Suppress("unused")
  var overrideConsoleCycleBufferSize: Boolean
    get() = state.overrideConsoleCycleBufferSize
    set(value) {
      state.overrideConsoleCycleBufferSize = value
    }

  var consoleCycleBufferSizeKb: Int
    get() = state.consoleCycleBufferSizeKb
    set(value) {
      state.consoleCycleBufferSizeKb = value
    }

  var consoleCommandHistoryLimit: Int
    get() = state.consoleCommandHistoryLimit
    set(value) {
      state.consoleCommandHistoryLimit = value
    }

  companion object {
    init {
      verbose("defFontSize=%d, defFontScale=%.2f", defFontSize, defFontScale)
    }

    @JvmStatic
    private fun verbose(msg: String, vararg args: Any) = if (JBUI.SCALE_VERBOSE) LOG.info(String.format(msg, *args)) else {}

    const val ANIMATION_DURATION = 300 // Milliseconds

    /** Not tabbed pane.  */
    const val TABS_NONE = 0

    @Suppress("ObjectPropertyName")
    @Volatile
    private var _instance: UISettings? = null

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
      get() = UISettingsState.defFontSize

    @JvmStatic
    fun restoreFontSize(readSize: Int, readScale: Float?): Int {
      var size = readSize
      if (readScale == null || readScale <= 0) {
        verbose("Reset font to default")
        // Reset font to default on switch from IDE-managed HiDPI to JRE-managed HiDPI. Doesn't affect OSX.
        if (UIUtil.isJreHiDPIEnabled() && !SystemInfo.isMac) size = UISettingsState.defFontSize
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

  private fun withDefFont(): UISettings {
    notRoamableOptions.state.initDefFont()
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
    state._incrementModificationCount()

    IconLoader.setFilter(ColorBlindnessSupport.get(state.colorBlindness)?.filter)

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
    CONSOLE_COMMAND_HISTORY_LIMIT = state.consoleCommandHistoryLimit
    FONT_SIZE = fontSize
    FONT_FACE = fontFace
    EDITOR_TAB_LIMIT = editorTabLimit
    OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE = overrideConsoleCycleBufferSize
    CONSOLE_CYCLE_BUFFER_SIZE_KB = consoleCycleBufferSizeKb
  }

  override fun getState() = state

  override fun loadState(state: UISettingsState) {
    this.state = state
    updateDeprecatedProperties()

    migrateOldSettings()

    // Check tab placement in editor
    val editorTabPlacement = state.editorTabPlacement
    if (editorTabPlacement != TABS_NONE &&
        editorTabPlacement != SwingConstants.TOP &&
        editorTabPlacement != SwingConstants.LEFT &&
        editorTabPlacement != SwingConstants.BOTTOM &&
        editorTabPlacement != SwingConstants.RIGHT) {
      state.editorTabPlacement = SwingConstants.TOP
    }

    // Check that alpha delay and ratio are valid
    if (state.alphaModeDelay < 0) {
      state.alphaModeDelay = 1500
    }
    if (state.alphaModeRatio < 0.0f || state.alphaModeRatio > 1.0f) {
      state.alphaModeRatio = 0.5f
    }

    if (state.maxClipboardContents <= 0) {
      state.maxClipboardContents = 5
    }

    fireUISettingsChanged()
  }

  @Suppress("DEPRECATION")
  private fun migrateOldSettings() {
    if (state.ideAAType != AntialiasingType.SUBPIXEL) {
      editorAAType = state.ideAAType
      state.ideAAType = AntialiasingType.SUBPIXEL
    }
    if (state.editorAAType != AntialiasingType.SUBPIXEL) {
      editorAAType = state.editorAAType
      state.editorAAType = AntialiasingType.SUBPIXEL
    }

    if (state.fontSize != UISettingsState.defFontSize) {
      fontSize = state.fontSize
      state.fontSize = UISettingsState.defFontSize
    }
    if (state.fontScale != 0f) {
      fontScale = state.fontScale
      state.fontScale = 0f
    }
    if (state.fontFace != null) {
      fontFace = state.fontFace
      state.fontFace = null
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