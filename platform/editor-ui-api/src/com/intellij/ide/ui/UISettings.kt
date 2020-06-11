// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.serviceContainer.NonInjectable
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ComponentTreeEventDispatcher
import com.intellij.util.SystemProperties
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.SwingConstants
import kotlin.math.roundToInt

private val LOG = logger<UISettings>()

@State(name = "UISettings", storages = [(Storage("ui.lnf.xml"))], reportStatistic = true)
class UISettings @NonInjectable constructor(private val notRoamableOptions: NotRoamableUiSettings) : PersistentStateComponent<UISettingsState> {
  constructor() : this(ApplicationManager.getApplication().getService(NotRoamableUiSettings::class.java))

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

  val allowMergeButtons: Boolean
    get() = Registry.`is`("ide.allow.merge.buttons")

  val animateWindows: Boolean
    get() = Registry.`is`("ide.animate.toolwindows")

  @Deprecated("use StatusBarWidgetSettings#isEnabled(MemoryUsagePanel.WIDGET_ID)")
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

  var useContrastScrollbars: Boolean
    get() = state.useContrastScrollBars
    set(value) {
      state.useContrastScrollBars = value
    }

  var hideToolStripes: Boolean
    get() = state.hideToolStripes
    set(value) {
      state.hideToolStripes = value
    }

  val hideNavigationOnFocusLoss: Boolean
    get() = Registry.`is`("ide.hide.navigation.on.focus.loss")

  var reuseNotModifiedTabs: Boolean
    get() = state.reuseNotModifiedTabs
    set(value) {
      state.reuseNotModifiedTabs = value
    }

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

  var smoothScrolling: Boolean
    get() = state.smoothScrolling
    set(value) {
      state.smoothScrolling = value
    }

  val animatedScrolling: Boolean
    get() = state.animatedScrolling

  val animatedScrollingDuration: Int
    get() = state.animatedScrollingDuration

  val animatedScrollingCurvePoints: Int
    get() = state.animatedScrollingCurvePoints

  val closeTabButtonOnTheRight: Boolean
    get() = state.closeTabButtonOnTheRight

  val cycleScrolling: Boolean
    get() = Registry.`is`("ide.cycle.scrolling")

  var navigateToPreview: Boolean
    get() = state.navigateToPreview
    set(value) {
      state.navigateToPreview = value
    }

  var selectedTabsLayoutInfoId: String?
    get() = state.selectedTabsLayoutInfoId
    set(value) {
      state.selectedTabsLayoutInfoId = value
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

  var showMembersInNavigationBar: Boolean
    get() = state.showMembersInNavigationBar
    set(value) {
      state.showMembersInNavigationBar = value
    }

  var showStatusBar: Boolean
    get() = state.showStatusBar
    set(value) {
      state.showStatusBar = value
    }

  var showMainMenu: Boolean
    get() = state.showMainMenu
    set(value) {
      state.showMainMenu = value
    }

  val showIconInQuickNavigation: Boolean
    get() = Registry.`is`("ide.show.icons.in.quick.navigation")

  var showTreeIndentGuides: Boolean
    get() = state.showTreeIndentGuides
    set(value) {
      state.showTreeIndentGuides = value
    }

  var compactTreeIndents: Boolean
    get() = state.compactTreeIndents
    set(value) {
      state.compactTreeIndents = value
    }

  var moveMouseOnDefaultButton: Boolean
    @ScheduledForRemoval(inVersion = "2020.3")
    @Deprecated("Use registry key 'ide.settings.move.mouse.on.default.button'")
    get() = state.moveMouseOnDefaultButton
    set(value) {
      state.moveMouseOnDefaultButton = value
    }

  var showMainToolbar: Boolean
    get() = state.showMainToolbar
    set(value) {
      state.showMainToolbar = value
    }

  val showIconsInMenus: Boolean
    get() = Registry.`is`("ide.show.icons.in.menus")

  var sortLookupElementsLexicographically: Boolean
    get() = state.sortLookupElementsLexicographically
    set(value) {
      state.sortLookupElementsLexicographically = value
    }

  val hideTabsIfNeeded: Boolean
    get() = state.hideTabsIfNeeded
  var showFileIconInTabs: Boolean
    get() = state.showFileIconInTabs
    set(value) {
      state.showFileIconInTabs = value
    }
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

  var presentationModeFontSize: Int
    get() = state.presentationModeFontSize
    set(value) {
      state.presentationModeFontSize = value
    }

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

  var recentFilesLimit: Int
    get() = state.recentFilesLimit
    set(value) {
      state.recentFilesLimit = value
    }

  var recentLocationsLimit: Int
    get() = state.recentLocationsLimit
    set(value) {
      state.recentLocationsLimit = value
    }

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

  var sortTabsAlphabetically: Boolean
    get() = state.sortTabsAlphabetically
    set(value) {
      state.sortTabsAlphabetically = value
    }

  var openTabsAtTheEnd: Boolean
    get() = state.openTabsAtTheEnd
    set(value) {
      state.openTabsAtTheEnd = value
    }

  var showInplaceComments: Boolean
    get() = state.showInplaceComments
    set(value) {
      state.showInplaceComments = value
    }

  val showInplaceCommentsInternal: Boolean
    get() = showInplaceComments && ApplicationManager.getApplication()?.isInternal ?: false

  var enableAlphaMode: Boolean
    get() = state.enableAlphaMode
    set(value) {
      state.enableAlphaMode = value
    }

  var fullPathsInWindowHeader: Boolean
    get() = state.fullPathsInWindowHeader
    set(value) {
      state.fullPathsInWindowHeader = value
    }

  init {
    // TODO Remove the registry keys and migration code in 2019.3
    if (SystemProperties.`is`("tabs.alphabetical")) {
      sortTabsAlphabetically = true
    }
  }

  companion object {
    init {
      verbose("defFontSize=%d, defFontScale=%.2f", defFontSize, defFontScale)
    }

    @JvmStatic
    private fun verbose(msg: String, vararg args: Any) {
      if (JBUIScale.SCALE_VERBOSE) {
        LOG.info(String.format(msg, *args))
      }
    }

    const val ANIMATION_DURATION = 300 // Milliseconds

    /** Not tabbed pane.  */
    const val TABS_NONE = 0

    @Suppress("ObjectPropertyName")
    @Volatile
    private var cachedInstance: UISettings? = null

    @JvmStatic
    val instance: UISettings
      get() {
        var result = cachedInstance
        if (result == null) {
          LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred()
          result = ApplicationManager.getApplication().getService(UISettings::class.java)!!
          cachedInstance = result
        }
        return result
      }

    @JvmStatic
    val instanceOrNull: UISettings?
      get() {
        val result = cachedInstance
        if (result == null && LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred) {
          return instance
        }
        return result
      }

    /**
     * Use this method if you are not sure whether the application is initialized.
     * @return persisted UISettings instance or default values.
     */
    @JvmStatic
    val shadowInstance: UISettings
      get() = instanceOrNull ?: UISettings(NotRoamableUiSettings())

    @JvmStatic
    val PREFERRED_FRACTIONAL_METRICS_VALUE: Any
      get() {
        return if (!Registry.`is`("ide.disable.fractionalMetrics", false)
                   && SystemProperties.getBooleanProperty("idea.force.use.fractional.metrics", SystemInfo.isMacOSCatalina))
          RenderingHints.VALUE_FRACTIONALMETRICS_ON
        else
          RenderingHints.VALUE_FRACTIONALMETRICS_OFF
      }

    @JvmStatic
    fun setupFractionalMetrics(g2d: Graphics2D) {
      g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, PREFERRED_FRACTIONAL_METRICS_VALUE)
    }

    /**
     * This method must not be used for set up antialiasing for editor components. To make sure antialiasing settings are taken into account
     * when preferred size of component is calculated, [.setupComponentAntialiasing] method should be called from
     * `updateUI()` or `setUI()` method of component.
     */
    @JvmStatic
    fun setupAntialiasing(g: Graphics) {
      g as Graphics2D
      g.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, UIUtil.getLcdContrastValue())

      if (LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred && ApplicationManager.getApplication() == null) {
        // cannot use services while Application has not been loaded yet, so let's apply the default hints
        GraphicsUtil.applyRenderingHints(g)
        return
      }

      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))

      setupFractionalMetrics(g)
    }

    /**
     * @see #setupAntialiasing(Graphics)
     */
    @JvmStatic
    fun setupComponentAntialiasing(component: JComponent) {
      GraphicsUtil.setAntialiasingType(component, AntialiasingType.getAAHintForSwingComponent())
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
      get() = if (JreHiDpiUtil.isJreHiDPIEnabled()) 1f else JBUIScale.sysScale()

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
        if (JreHiDpiUtil.isJreHiDPIEnabled() && !SystemInfo.isMac) size = UISettingsState.defFontSize
      }
      else if (readScale != defFontScale) {
        size = ((readSize / readScale) * defFontScale).roundToInt()
      }
      LOG.info("Loaded: fontSize=$readSize, fontScale=$readScale; restored: fontSize=$size, fontScale=$defFontScale")
      return size
    }
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
    if (this === cachedInstance) {
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
    if (migrateOldFontSettings()) {
      notRoamableOptions.fixFontSettings()
    }

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
    if (state.ideAAType == AntialiasingType.SUBPIXEL && !AntialiasingType.canUseSubpixelAAForIDE()) {
      state.ideAAType = AntialiasingType.GREYSCALE;
    }
    if (state.moveMouseOnDefaultButton) {
      Registry.get("ide.settings.move.mouse.on.default.button").setValue(true)
      state.moveMouseOnDefaultButton = false
    }
  }

  @Suppress("DEPRECATION")
  private fun migrateOldFontSettings(): Boolean {
    var migrated = false
    if (state.fontSize != 0) {
      fontSize = restoreFontSize(state.fontSize, state.fontScale)
      state.fontSize = 0
      migrated = true
    }
    if (state.fontScale != 0f) {
      fontScale = state.fontScale
      state.fontScale = 0f
      migrated = true
    }
    if (state.fontFace != null) {
      fontFace = state.fontFace
      state.fontFace = null
      migrated = true
    }
    return migrated
  }

  //<editor-fold desc="Deprecated stuff.">
  @Suppress("unused", "PropertyName")
  @Deprecated("Use fontFace", replaceWith = ReplaceWith("fontFace"))
  @JvmField
  @Transient
  var FONT_FACE: String? = null

  @Suppress("unused", "PropertyName")
  @Deprecated("Use fontSize", replaceWith = ReplaceWith("fontSize"))
  @JvmField
  @Transient
  var FONT_SIZE: Int? = 0

  @Suppress("unused", "PropertyName")
  @Deprecated("Use hideToolStripes", replaceWith = ReplaceWith("hideToolStripes"))
  @JvmField
  @Transient
  var HIDE_TOOL_STRIPES = true

  @Suppress("unused", "PropertyName")
  @Deprecated("Use consoleCommandHistoryLimit", replaceWith = ReplaceWith("consoleCommandHistoryLimit"))
  @JvmField
  @Transient
  var CONSOLE_COMMAND_HISTORY_LIMIT = 300

  @Suppress("unused", "PropertyName")
  @Deprecated("Use cycleScrolling", replaceWith = ReplaceWith("cycleScrolling"))
  @JvmField
  @Transient
  var CYCLE_SCROLLING = true

  @Suppress("unused", "PropertyName")
  @Deprecated("Use showMainToolbar", replaceWith = ReplaceWith("showMainToolbar"))
  @JvmField
  @Transient
  var SHOW_MAIN_TOOLBAR = false

  @Suppress("unused", "PropertyName")
  @Deprecated("Use showCloseButton", replaceWith = ReplaceWith("showCloseButton"))
  @JvmField
  @Transient
  var SHOW_CLOSE_BUTTON = true

  @Suppress("unused", "PropertyName")
  @Deprecated("Use editorAAType", replaceWith = ReplaceWith("editorAAType"))
  @JvmField
  @Transient
  var EDITOR_AA_TYPE: AntialiasingType? = AntialiasingType.SUBPIXEL

  @Suppress("unused", "PropertyName")
  @Deprecated("Use presentationMode", replaceWith = ReplaceWith("presentationMode"))
  @JvmField
  @Transient
  var PRESENTATION_MODE = false

  @Suppress("unused", "PropertyName", "SpellCheckingInspection")
  @Deprecated("Use overrideLafFonts", replaceWith = ReplaceWith("overrideLafFonts"))
  @JvmField
  @Transient
  var OVERRIDE_NONIDEA_LAF_FONTS = false

  @Suppress("unused", "PropertyName")
  @Deprecated("Use presentationModeFontSize", replaceWith = ReplaceWith("presentationModeFontSize"))
  @JvmField
  @Transient
  var PRESENTATION_MODE_FONT_SIZE = 24

  @Suppress("unused", "PropertyName")
  @Deprecated("Use editorTabLimit", replaceWith = ReplaceWith("editorTabLimit"))
  @JvmField
  @Transient
  var EDITOR_TAB_LIMIT = editorTabLimit

  @Suppress("unused", "PropertyName")
  @Deprecated("Use overrideConsoleCycleBufferSize", replaceWith = ReplaceWith("overrideConsoleCycleBufferSize"))
  @JvmField
  @Transient
  var OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE = false

  @Suppress("unused", "PropertyName")
  @Deprecated("Use consoleCycleBufferSizeKb", replaceWith = ReplaceWith("consoleCycleBufferSizeKb"))
  @JvmField
  @Transient
  var CONSOLE_CYCLE_BUFFER_SIZE_KB = consoleCycleBufferSizeKb
  //</editor-fold>
}