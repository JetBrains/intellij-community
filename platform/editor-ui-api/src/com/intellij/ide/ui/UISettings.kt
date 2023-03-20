// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.serviceContainer.NonInjectable
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ComponentTreeEventDispatcher
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.SwingConstants

private val LOG = logger<UISettings>()

@State(name = "UISettings", storages = [(Storage("ui.lnf.xml"))], useLoadedStateAsExisting = false, category = SettingsCategory.UI)
class UISettings @NonInjectable constructor(private val notRoamableOptions: NotRoamableUiSettings) : PersistentStateComponentWithModificationTracker<UISettingsState> {
  constructor() : this(ApplicationManager.getApplication().getService(NotRoamableUiSettings::class.java))

  private var state = UISettingsState()

  private val treeDispatcher = ComponentTreeEventDispatcher.create(UISettingsListener::class.java)

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
    get() = Registry.`is`("ide.allow.merge.buttons", true)

  val animateWindows: Boolean
    get() = Registry.`is`("ide.animate.toolwindows", false)

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
    get() = Registry.`is`("ide.hide.navigation.on.focus.loss", false)

  var reuseNotModifiedTabs: Boolean
    get() = state.reuseNotModifiedTabs
    set(value) {
      state.reuseNotModifiedTabs = value
    }

  var openTabsInMainWindow: Boolean
    get() = state.openTabsInMainWindow
    set(value) {
      state.openTabsInMainWindow = value
    }

  var openInPreviewTabIfPossible: Boolean
    get() = state.openInPreviewTabIfPossible
    set(value) {
      state.openInPreviewTabIfPossible = value
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

  var separateMainMenu: Boolean
    get() = (SystemInfoRt.isWindows || SystemInfoRt.isXWindow) && state.separateMainMenu
    set(value) {
      state.separateMainMenu = value
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
    get() = AdvancedSettings.getBoolean("ide.cycle.scrolling")

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

  var navBarLocation : NavBarLocation
    get() = state.navigationBarLocation
    set(value) {
      state.navigationBarLocation = value
    }

  val showNavigationBarInBottom : Boolean
    get() = showNavigationBar && navBarLocation == NavBarLocation.BOTTOM

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
    get() = Registry.`is`("ide.show.icons.in.quick.navigation", false)

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

  var compactMode: Boolean
    get() = uiDensity == UIDensity.COMPACT
    set(value) {
      uiDensity = if (value) UIDensity.COMPACT else UIDensity.DEFAULT
    }

  var uiDensity: UIDensity
    get() = state.uiDensity
    set(value) {
      state.uiDensity = value
    }

  var showMainToolbar: Boolean
    get() = if (RegistryManager.getInstance().`is`("ide.experimental.ui")) separateMainMenu else state.showMainToolbar
    set(value) {
      state.showMainToolbar = value

      val toolbarSettingsState = ToolbarSettings.getInstance().state!!
      toolbarSettingsState.showNewMainToolbar = !value && toolbarSettingsState.showNewMainToolbar
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

  val hideTabsIfNeeded: Boolean
    get() = state.hideTabsIfNeeded || editorTabPlacement == SwingConstants.LEFT || editorTabPlacement == SwingConstants.RIGHT
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

  var rememberSizeForEachToolWindowOldUI: Boolean
    get() = state.rememberSizeForEachToolWindowOldUI
    set(value) {
      state.rememberSizeForEachToolWindowOldUI = value
    }

  var rememberSizeForEachToolWindowNewUI: Boolean
    get() = state.rememberSizeForEachToolWindowNewUI
    set(value) {
      state.rememberSizeForEachToolWindowNewUI = value
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
    get() = UISettingsUtils.with(this).presentationModeFontSize.toInt()
    set(value) {
      UISettingsUtils.with(this).presentationModeFontSize = value.toFloat()
    }

  var presentationModeIdeScale: Float
    get() = notRoamableOptions.state.presentationModeIdeScale
    set(value) {
      notRoamableOptions.state.presentationModeIdeScale = value
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
    get() = notRoamableOptions.state.overrideLafFonts
    set(value) {
      notRoamableOptions.state.overrideLafFonts = value
    }

  var fontFace: @NlsSafe String?
    get() = notRoamableOptions.state.fontFace
    set(value) {
      notRoamableOptions.state.fontFace = value
    }

  var fontSize: Int
    get() = (notRoamableOptions.state.fontSize + 0.5).toInt()
    set(value) {
      notRoamableOptions.state.fontSize = value.toFloat()
    }

  var fontSize2D: Float
    get() = notRoamableOptions.state.fontSize
    set(value) {
      notRoamableOptions.state.fontSize = value
    }

  var fontScale: Float
    get() = notRoamableOptions.state.fontScale
    set(value) {
      notRoamableOptions.state.fontScale = value
    }

  var ideScale: Float
    get() = notRoamableOptions.state.ideScale
    set(value) {
      notRoamableOptions.state.ideScale = value
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

  var alwaysKeepTabsAlphabeticallySorted: Boolean
    get() = state.alwaysKeepTabsAlphabeticallySorted
    set(value) {
      state.alwaysKeepTabsAlphabeticallySorted = value
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

  var fullPathsInWindowHeader: Boolean
    get() = state.fullPathsInWindowHeader
    set(value) {
      state.fullPathsInWindowHeader = value
    }

  var mergeMainMenuWithWindowTitle: Boolean
    get() = state.mergeMainMenuWithWindowTitle
    set(value) {
      state.mergeMainMenuWithWindowTitle = value
    }

  var showVisualFormattingLayer: Boolean
    get() = state.showVisualFormattingLayer
    set(value) {
      state.showVisualFormattingLayer = value
    }

  var showBreakpointsOverLineNumbers: Boolean
    get() = state.showBreakpointsOverLineNumbers
    set(value) {
      state.showBreakpointsOverLineNumbers = value
    }

  companion object {
    init {
      if (JBUIScale.SCALE_VERBOSE) {
        LOG.info(String.format("defFontSize=%.1f, defFontScale=%.2f", defFontSize, defFontScale))
      }
    }

    const val ANIMATION_DURATION = 300 // Milliseconds

    /** Not tabbed pane.  */
    const val TABS_NONE = 0

    @Volatile
    private var cachedInstance: UISettings? = null

    @JvmStatic
    fun getInstance(): UISettings {
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
          return getInstance()
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

    private fun calcFractionalMetricsHint(registryKey: String, defaultValue: Boolean): Any {
      val hint: Boolean
      if (LoadingState.APP_STARTED.isOccurred) {
        val registryValue = Registry.get(registryKey)
        if (registryValue.isMultiValue) {
          val option = registryValue.selectedOption
          if (option.equals("Enabled")) hint = true
          else if (option.equals("Disabled")) hint = false
          else hint = defaultValue
        }
        else {
          hint = if (registryValue.isBoolean && registryValue.asBoolean()) true else defaultValue
        }
      }
      else hint = defaultValue
      return if (hint) RenderingHints.VALUE_FRACTIONALMETRICS_ON else RenderingHints.VALUE_FRACTIONALMETRICS_OFF
    }

    fun getPreferredFractionalMetricsValue(): Any {
      val enableByDefault = SystemInfo.isMacOSCatalina || (FontSubpixelResolution.ENABLED
                                                           && AntialiasingType.getKeyForCurrentScope(false) ==
                                                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
      return calcFractionalMetricsHint("ide.text.fractional.metrics", enableByDefault)
    }

    @JvmStatic
    val editorFractionalMetricsHint: Any
      get() {
        val enableByDefault = FontSubpixelResolution.ENABLED
                              && AntialiasingType.getKeyForCurrentScope(true) == RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        return calcFractionalMetricsHint("editor.text.fractional.metrics", enableByDefault)
      }

    @JvmStatic
    fun setupFractionalMetrics(g2d: Graphics2D) {
      g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, getPreferredFractionalMetricsValue())
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

      if (!LoadingState.COMPONENTS_REGISTERED.isOccurred) {
        // cannot use services while Application has not been loaded yet, so let's apply the default hints
        GraphicsUtil.applyRenderingHints(g)
        return
      }

      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))

      setupFractionalMetrics(g)
    }

    @JvmStatic
    fun setupComponentAntialiasing(component: JComponent) {
      GraphicsUtil.setAntialiasingType(component, AntialiasingType.getAAHintForSwingComponent())
    }

    @JvmStatic
    fun setupEditorAntialiasing(component: JComponent) {
      GraphicsUtil.setAntialiasingType(component, getInstance().editorAAType.textInfo)
    }

    /**
     * Returns the default font scale, which depends on the HiDPI mode (see [com.intellij.ui.scale.ScaleType]).
     * <p>
     * The font is represented:
     * - in relative (dpi-independent) points in the JRE-managed HiDPI mode, so the method returns 1.0f
     * - in absolute (dpi-dependent) points in the IDE-managed HiDPI mode, so the method returns the default screen scale
     *
     * @return the system font scale
     */
    @JvmStatic
    val defFontScale: Float
      get() = when {
        JreHiDpiUtil.isJreHiDPIEnabled() -> 1f
        else -> JBUIScale.sysScale()
      }

    /**
     * Returns the default font size scaled by #defFontScale
     *
     * @return the default scaled font size
     */
    @JvmStatic
    val defFontSize: Float
      get() = UISettingsState.defFontSize

    @JvmStatic
    fun restoreFontSize(readSize: Float, readScale: Float?): Float {
      var size = readSize
      if (readScale == null || readScale <= 0) {
        if (JBUIScale.SCALE_VERBOSE) LOG.info("Reset font to default")
        // Reset font to default on switch from IDE-managed HiDPI to JRE-managed HiDPI. Doesn't affect OSX.
        if (!SystemInfoRt.isMac && JreHiDpiUtil.isJreHiDPIEnabled()) {
          size = UISettingsState.defFontSize
        }
      }
      else if (readScale != defFontScale) {
        size = (readSize / readScale) * defFontScale
      }
      if (JBUIScale.SCALE_VERBOSE) LOG.info("Loaded: fontSize=$readSize, fontScale=$readScale; restored: fontSize=$size, fontScale=$defFontScale")
      return size
    }

    const val MERGE_MAIN_MENU_WITH_WINDOW_TITLE_PROPERTY = "ide.win.frame.decoration"

    @JvmStatic
    val mergeMainMenuWithWindowTitleOverrideValue = System.getProperty(MERGE_MAIN_MENU_WITH_WINDOW_TITLE_PROPERTY)?.toBoolean()
    val isMergeMainMenuWithWindowTitleOverridden = mergeMainMenuWithWindowTitleOverrideValue != null
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Please use {@link UISettingsListener#TOPIC}")
  @ScheduledForRemoval
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
      try {
        treeDispatcher.multicaster.uiSettingsChanged(this)
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Exception) {
        LOG.error(e)
      }

      ApplicationManager.getApplication().messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(this)
    }
  }

  @Suppress("DEPRECATION")
  private fun updateDeprecatedProperties() {
    OVERRIDE_NONIDEA_LAF_FONTS = overrideLafFonts
    FONT_SIZE = fontSize
    FONT_FACE = fontFace
    EDITOR_TAB_LIMIT = editorTabLimit
  }

  override fun getState() = state

  override fun noStateLoaded() {
    super.noStateLoaded()
    migrateFontParameters()
  }

  override fun loadState(state: UISettingsState) {
    this.state = state
    updateDeprecatedProperties()

    migrateOldSettings()
    if (migrateOldFontSettings()) {
      notRoamableOptions.fixFontSettings()
    }
    migrateFontParameters()

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

  override fun getStateModificationCount(): Long {
    return state.modificationCount
  }

  @Suppress("DEPRECATION")
  private fun migrateOldSettings() {
    if (state.ideAAType != AntialiasingType.SUBPIXEL) {
      ideAAType = state.ideAAType
      state.ideAAType = AntialiasingType.SUBPIXEL
    }
    if (state.editorAAType != AntialiasingType.SUBPIXEL) {
      editorAAType = state.editorAAType
      state.editorAAType = AntialiasingType.SUBPIXEL
    }
    if (!state.allowMergeButtons) {
      Registry.get("ide.allow.merge.buttons").setValue(false)
      state.allowMergeButtons = true
    }
  }

  @Suppress("DEPRECATION")
  private fun migrateOldFontSettings(): Boolean {
    var migrated = false
    if (state.fontSize != 0) {
      fontSize2D = restoreFontSize(state.fontSize.toFloat(), state.fontScale)
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

  private fun migrateFontParameters() {
    notRoamableOptions.migratePresentationModeFontSize(state.presentationModeFontSize)
    notRoamableOptions.migrateOverrideLafFonts(state.overrideLafFonts)
  }

  //<editor-fold desc="Deprecated stuff.">
  @Suppress("PropertyName")
  @Deprecated("Use fontFace", replaceWith = ReplaceWith("fontFace"))
  @JvmField
  @Transient
  @ScheduledForRemoval
  var FONT_FACE: String? = null

  @Suppress("PropertyName")
  @Deprecated("Use fontSize", replaceWith = ReplaceWith("fontSize"))
  @JvmField
  @Transient
  @ScheduledForRemoval
  var FONT_SIZE: Int? = 0

  @Suppress("PropertyName", "SpellCheckingInspection")
  @Deprecated("Use overrideLafFonts", replaceWith = ReplaceWith("overrideLafFonts"))
  @JvmField
  @Transient
  @ScheduledForRemoval
  var OVERRIDE_NONIDEA_LAF_FONTS = false

  @Suppress("PropertyName")
  @Deprecated("Use editorTabLimit", replaceWith = ReplaceWith("editorTabLimit"))
  @JvmField
  @Transient
  @ScheduledForRemoval
  var EDITOR_TAB_LIMIT = editorTabLimit
  //</editor-fold>
}
