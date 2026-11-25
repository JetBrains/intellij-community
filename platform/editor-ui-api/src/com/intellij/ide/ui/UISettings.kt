// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.getValue
import com.intellij.serviceContainer.NonInjectable
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.NewUiValue
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ComponentTreeEventDispatcher
import com.intellij.util.asDisposable
import com.intellij.util.cancelOnDispose
import com.intellij.util.ui.EDT
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

private val LOG = logger<UISettings>()

/**
 * Holds the UI settings of the IDE.
 *
 * Some properties hold the value of a settings v2 key, and their KDoc names the key. A write of such a property is
 * asynchronous. The setter launches the write of the key and returns before the write applies. So a read right after
 * the write can still return the old value.
 *
 * To react to the new value of one such property, use its value listener, such as [addEditorAntialiasingListener]. A
 * value listener gets the new value as the getter reads it. It runs on the EDT, in any modality. It is not called when
 * the value equals the value of its previous call. A value listener that gets a coroutine scope lives until the scope is
 * cancelled. A value listener that gets a disposable lives until the disposable is disposed. An instance over a given
 * component registers no value listener. To react to several values, use [UISettingsListener].
 */
@State(
  name = "UISettings",
  storages = [(Storage("ui.lnf.xml", usePathMacroManager = false))],
  useLoadedStateAsExisting = false,
  category = SettingsCategory.UI,
)
class UISettings private constructor(
  private val notRoamableOptions: NotRoamableUiSettings,
  /**
   * Tells which of two kinds this instance is. The application service reads and writes the keys of [UISettingsKeys].
   * An instance over a given component reads and writes only [notRoamableOptions], and never touches the keys.
   *
   * The second kind serves [shadowInstance] before the settings store is initialized. Then no settings vault exists,
   * so no key can be read. The instance holds a new component that was never loaded, so it gives the default values of
   * [NotRoamableUiOptions]. It must not read or change the real settings of the user.
   */
  private val usesKeys: Boolean,
) : PersistentStateComponentWithModificationTracker<UISettingsState> {
  /** Creates an instance over [notRoamableOptions] that never touches the keys. See [usesKeys]. */
  @NonInjectable
  constructor(notRoamableOptions: NotRoamableUiSettings) : this(notRoamableOptions, usesKeys = false)

  /** Creates the application service, which reads and writes the keys. See [usesKeys]. */
  constructor() : this(ApplicationManager.getApplication().service<NotRoamableUiSettings>(), usesKeys = true)

  private var state = UISettingsState()

  private val treeDispatcher = ComponentTreeEventDispatcher.create(UISettingsListener::class.java)

  /**
   * Holds the antialiasing of the text in the IDE, the value of [UISettingsKeys.IDE_ANTIALIASING]. A write is
   * asynchronous. See [UISettings]. [addIdeAntialiasingListener] follows the value.
   */
  var ideAAType: AntialiasingType
    get() = readSetting(UISettingsKeys.IDE_ANTIALIASING) { notRoamableOptions.ideAAType }
    set(value) {
      writeSetting(UISettingsKeys.IDE_ANTIALIASING, value) { notRoamableOptions.ideAAType = value }
    }

  /**
   * Holds the antialiasing of the text in the editor, the value of [UISettingsKeys.EDITOR_ANTIALIASING]. A write is
   * asynchronous. See [UISettings]. [addEditorAntialiasingListener] follows the value.
   */
  var editorAAType: AntialiasingType
    get() = readSetting(UISettingsKeys.EDITOR_ANTIALIASING) { notRoamableOptions.editorAAType }
    set(value) {
      writeSetting(UISettingsKeys.EDITOR_ANTIALIASING, value) { notRoamableOptions.editorAAType = value }
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

  /**
   * Tells whether the tool windows hide their stripes. It is `true` also with [experimentalSingleStripe]. A write of
   * `false` also turns off [experimentalSingleStripe], and that part is asynchronous. See [UISettings].
   */
  var hideToolStripes: Boolean
    get() = state.hideToolStripes || experimentalSingleStripe
    set(value) {
      state.hideToolStripes = value
      if (!value) {
        experimentalSingleStripe = false
      }
    }

  /**
   * Tells whether the tool windows show their buttons in one stripe, the value of [UISettingsKeys.SINGLE_STRIPE]. A
   * write is asynchronous. See [UISettings].
   */
  @get:Internal
  @set:Internal
  var experimentalSingleStripe: Boolean
    get() = readSetting(UISettingsKeys.SINGLE_STRIPE) { notRoamableOptions.experimentalSingleStripe }
    set(value) {
      writeSetting(UISettingsKeys.SINGLE_STRIPE, value) { notRoamableOptions.experimentalSingleStripe = value }
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

  @get:Internal
  @set:Internal
  var mainMenuDisplayMode: MainMenuDisplayMode
    get() = MainMenuDisplayMode.valueOf(state.mainMenuDisplayMode)
    set(value) {
      mainMenuDisplayModePrev = MainMenuDisplayMode.valueOf(state.mainMenuDisplayMode)
      state.mainMenuDisplayMode = value.name
    }

  @Internal
  var mainMenuDisplayModePrev: MainMenuDisplayMode = mainMenuDisplayMode

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

  var differentToolwindowBackground: Boolean
    get() = state.differentToolwindowBackground
    set(value) {
      state.differentToolwindowBackground = value
    }

  var showToolWindowsNames: Boolean
    get() = state.showToolWindowsNames
    set(value) {
      state.showToolWindowsNames = value
    }

  var toolWindowLeftSideCustomWidth: Int
    get() = state.toolWindowLeftSideCustomWidth
    set(value) {
      state.toolWindowLeftSideCustomWidth = value
    }

  var toolWindowRightSideCustomWidth: Int
    get() = state.toolWindowRightSideCustomWidth
    set(value) {
      state.toolWindowRightSideCustomWidth = value
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

  var navBarLocation: NavBarLocation
    get() = state.navigationBarLocation
    set(value) {
      state.navigationBarLocation = value
    }

  val showNavigationBarInBottom: Boolean
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

  var expandNodesWithSingleClick: Boolean
    get() = state.expandNodesWithSingleClick
    set(value) {
      state.expandNodesWithSingleClick = value
    }

  var compactMode: Boolean
    get() = uiDensity == UIDensity.COMPACT
    set(value) {
      uiDensity = if (value) UIDensity.COMPACT else UIDensity.DEFAULT
    }

  var differentiateProjects: Boolean
    get() = state.differentiateProjects
    set(value) {
      state.differentiateProjects = value
    }

  var uiDensity: UIDensity
    get() = state.uiDensity
    set(value) {
      state.uiDensity = value
    }

  var showMainToolbar: Boolean
    get() = if (NewUiValue.isEnabled()) mainMenuDisplayMode == MainMenuDisplayMode.SEPARATE_TOOLBAR else state.showMainToolbar
    set(value) {
      state.showMainToolbar = value

      val toolbarSettingsState = ToolbarSettings.getInstance().state!!
      toolbarSettingsState.showNewMainToolbar = !value && toolbarSettingsState.showNewMainToolbar
    }

  var showNewMainToolbar: Boolean
    get() = state.showNewMainToolbar
    set(value) {
      state.showNewMainToolbar = value
    }

  var showIconsInMenus: Boolean
    get() = state.showIconsInMenus
    set(value) {
      state.showIconsInMenus = value
    }

  var keepPopupsForToggles: Boolean
    get() = state.keepPopupsForToggles
    set(value) {
      state.keepPopupsForToggles = value
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

  /**
   * Holds the editor font size in the presentation mode, from [presentationModeIdeScale]. A write is asynchronous. See
   * [UISettings].
   */
  var presentationModeFontSize: Int
    get() = UISettingsUtils.with(this).presentationModeFontSize.toInt()
    set(value) {
      UISettingsUtils.with(this).presentationModeFontSize = value.toFloat()
    }

  /**
   * Holds the scale of the IDE in the presentation mode, the value of [UISettingsKeys.PRESENTATION_MODE_IDE_SCALE]. A
   * write is asynchronous. See [UISettings]. [addPresentationModeIdeScaleListener] follows the value.
   */
  var presentationModeIdeScale: Float
    get() = readSetting(UISettingsKeys.PRESENTATION_MODE_IDE_SCALE) { notRoamableOptions.presentationModeIdeScale }
    set(value) {
      writeSetting(UISettingsKeys.PRESENTATION_MODE_IDE_SCALE, value) { notRoamableOptions.presentationModeIdeScale = value }
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

  /**
   * Tells whether the IDE uses the custom font, the value of [UISettingsKeys.CUSTOM_FONT_ENABLED]. A write is
   * asynchronous. See [UISettings].
   */
  var overrideLafFonts: Boolean
    get() = readSetting(UISettingsKeys.CUSTOM_FONT_ENABLED) { notRoamableOptions.state.overrideLafFonts }
    set(value) {
      writeSetting(UISettingsKeys.CUSTOM_FONT_ENABLED, value) { notRoamableOptions.overrideLafFonts = value }
    }

  /**
   * Holds the name of the custom font, the value of [UISettingsKeys.CUSTOM_FONT_FACE]. A font that cannot display the
   * standard ASCII symbols reads as a valid fallback font. No value reads as the system font. A write is asynchronous.
   * See [UISettings].
   */
  var fontFace: @NlsSafe String?
    get() {
      if (!usesKeys) {
        return notRoamableOptions.fontFace
      }
      val fontFace = UISettingsKeys.CUSTOM_FONT_FACE.getValue() ?: return JBUIScale.getSystemFontDataIfInitialized()?.first
      return validFontFaces.computeIfAbsent(fontFace) { findValidFontFace(it, fontSize2D) }
    }
    set(value) {
      writeSetting(UISettingsKeys.CUSTOM_FONT_FACE, value) { notRoamableOptions.fontFace = value }
    }

  /** Holds [fontSize2D] rounded to an integer. A write is asynchronous. See [UISettings]. */
  var fontSize: Int
    get() {
      return (fontSize2D + 0.5).toInt()
    }
    set(value) {
      fontSize2D = value.toFloat()
    }

  /**
   * Holds the size of the custom font, the value of [UISettingsKeys.CUSTOM_FONT_SIZE]. A size of `0` or less reads as
   * the default font size. A write is asynchronous. See [UISettings].
   */
  var fontSize2D: Float
    get() {
      if (!usesKeys) {
        return notRoamableOptions.fontSize
      }
      return UISettingsKeys.CUSTOM_FONT_SIZE.getValue().takeIf { it > 0 } ?: getDefaultFontSize()
    }
    set(value) {
      writeSetting(UISettingsKeys.CUSTOM_FONT_SIZE, value) { notRoamableOptions.fontSize = value }
    }

  var fontScale: Float
    get() = notRoamableOptions.fontScale
    set(value) {
      notRoamableOptions.fontScale = value
    }

  /**
   * Holds the scale of the IDE, the value of [UISettingsKeys.IDE_SCALE]. A scale of `0` or less reads as the default font
   * size, as the load of [NotRoamableUiSettings] repairs it. A write is asynchronous. See [UISettings].
   * [addIdeScaleListener] follows the value.
   */
  var ideScale: Float
    get() {
      if (!usesKeys) {
        return notRoamableOptions.ideScale
      }
      return UISettingsKeys.IDE_SCALE.getValue().takeIf { it > 0 } ?: getDefaultFontSize()
    }
    set(value) {
      writeSetting(UISettingsKeys.IDE_SCALE, value) { notRoamableOptions.ideScale = value }
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

  @get:Internal
  @set:Internal
  var showInplaceCommentsInternal: Boolean
    get() = state.showInplaceCommentsInternal && ApplicationManager.getApplication()?.isInternal ?: false
    set(value) {
      state.showInplaceCommentsInternal = value
    }

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

  /**
   * Holds the scale of the IDE now: the scale of [runWithIdeScale] while its action runs, else [presentationModeIdeScale]
   * in the presentation mode, else [ideScale]. A write goes to the key of the mode and is asynchronous. See [UISettings].
   */
  var currentIdeScale: Float
    get() = temporaryIdeScale ?: if (presentationMode) presentationModeIdeScale else ideScale
    set(scale) {
      when {
        scale.percentValue == currentIdeScale.percentValue -> return
        presentationMode -> presentationModeIdeScale = scale
        else -> ideScale = scale
      }
    }

  /**
   * Holds the scale of [runWithIdeScale] while its action runs, else `null`.
   */
  private var temporaryIdeScale: Float? = null

  /**
   * Runs [action] while [currentIdeScale] reads [scale]. The call stores nothing and does not change the settings of the
   * user. Only the synchronous code of [action] can rely on the scale.
   */
  @Internal
  fun runWithIdeScale(scale: Float, action: () -> Unit) {
    temporaryIdeScale = scale
    try {
      action()
    }
    finally {
      temporaryIdeScale = null
    }
  }

  var showPreviewInSearchEverywhere: Boolean
    get() = state.showPreviewInSearchEverywhere
    set(value) {
      state.showPreviewInSearchEverywhere = value
    }

  @get:Internal
  @set:Internal
  var useSimplifiedSplashImage: Boolean
    get() = state.useSimplifiedSplashImage
    set(value) {
      state.useSimplifiedSplashImage = value
    }

  companion object {
    init {
      if (JBUIScale.SCALE_VERBOSE) {
        LOG.info(String.format("defFontSize=%.1f, defFontScale=%.2f", getDefFontSize(), defFontScale))
      }
    }

    @Suppress("SpellCheckingInspection")
    // enables new style help context tooltips
    private val isHelpTooltipEnabled = System.getProperty("ide.helptooltip.enabled", "true").toBoolean()

    @Internal
    @JvmStatic
    fun isIdeHelpTooltipEnabled(): Boolean = isHelpTooltipEnabled

    const val ANIMATION_DURATION: Int = 300 // Milliseconds

    /** Not tabbed pane.  */
    const val TABS_NONE: Int = 0

    @Volatile
    private var cachedInstance: UISettings? = null

    /** Maps a custom font face to the valid face that [fontFace] reads for it, so each face is checked once. */
    private val validFontFaces = ConcurrentHashMap<String, String>()

    @JvmStatic
    fun getInstance(): UISettings {
      var result = cachedInstance
      if (result == null) {
        LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred()
        result = ApplicationManager.getApplication().service<UISettings>()
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
     * Holds the application service once the settings store is initialized. Before that, it holds a new instance over a
     * new component, which gives the default values. Early startup code, such as the early frame and the frame headers,
     * reads it. See [UISettings.usesKeys].
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
          when {
            option.equals("Enabled") -> hint = true
            option.equals("Disabled") -> hint = false
            else -> hint = defaultValue
          }
        }
        else {
          hint = if (registryValue.isBoolean && registryValue.asBoolean()) true else defaultValue
        }
      }
      else {
        hint = defaultValue
      }
      return if (hint) RenderingHints.VALUE_FRACTIONALMETRICS_ON else RenderingHints.VALUE_FRACTIONALMETRICS_OFF
    }

    fun getPreferredFractionalMetricsValue(): Any {
      val enableByDefault = SystemInfo.isMacOSCatalina ||
                            (FontSubpixelResolution.ENABLED
                             && AntialiasingType.getKeyForCurrentScope(false) == RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
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

    @JvmStatic
    fun setupFractionalMetrics(component: JComponent) {
      component.putClientProperty(RenderingHints.KEY_FRACTIONALMETRICS, getPreferredFractionalMetricsValue())
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
      GraphicsUtil.setAntialiasingType(component, AntialiasingType.getAATextInfoForSwingComponent())
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
    fun getDefFontSize(): Float = getDefaultFontSize()

    @JvmStatic
    fun restoreFontSize(readSize: Float, readScale: Float?): Float {
      var size = readSize
      if (readScale == null || readScale <= 0) {
        if (JBUIScale.SCALE_VERBOSE) {
          LOG.info("Reset font to default")
        }
        // Reset font to default on switch from IDE-managed HiDPI to JRE-managed HiDPI. Doesn't affect OSX.
        if (!SystemInfoRt.isMac && JreHiDpiUtil.isJreHiDPIEnabled()) {
          size = getDefaultFontSize()
        }
      }
      else if (readScale != defFontScale) {
        size = (readSize / readScale) * defFontScale
      }
      if (JBUIScale.SCALE_VERBOSE) {
        LOG.info("Loaded: fontSize=$readSize, fontScale=$readScale; restored: fontSize=$size, fontScale=$defFontScale")
      }
      return size
    }
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Please use {@link UISettingsListener#TOPIC}")
  @ScheduledForRemoval
  fun addUISettingsListener(listener: UISettingsListener, parentDisposable: Disposable) {
    ApplicationManager.getApplication().messageBus.connect(parentDisposable).subscribe(UISettingsListener.TOPIC, listener)
  }

  /** Calls [listener] with the new [ideAAType] after it changes, until [coroutineScope] is cancelled. See [UISettings]. */
  fun addIdeAntialiasingListener(coroutineScope: CoroutineScope, listener: (AntialiasingType) -> Unit) {
    addValueListener(UISettingsKeys.IDE_ANTIALIASING, coroutineScope.asDisposable(), { ideAAType }, listener)
  }

  /** Calls [listener] with the new [editorAAType] after it changes, until [coroutineScope] is cancelled. See [UISettings]. */
  fun addEditorAntialiasingListener(coroutineScope: CoroutineScope, listener: (AntialiasingType) -> Unit) {
    addValueListener(UISettingsKeys.EDITOR_ANTIALIASING, coroutineScope.asDisposable(), { editorAAType }, listener)
  }

  /** Calls [listener] with the new [ideScale] after it changes, until [parentDisposable] is disposed. See [UISettings]. */
  fun addIdeScaleListener(parentDisposable: Disposable, listener: (Float) -> Unit) {
    addValueListener(UISettingsKeys.IDE_SCALE, parentDisposable, { ideScale }, listener)
  }

  /**
   * Calls [listener] with the new [presentationModeIdeScale] after it changes, until [parentDisposable] is disposed. See
   * [UISettings].
   */
  fun addPresentationModeIdeScaleListener(parentDisposable: Disposable, listener: (Float) -> Unit) {
    addValueListener(UISettingsKeys.PRESENTATION_MODE_IDE_SCALE, parentDisposable, { presentationModeIdeScale }, listener)
  }

  /**
   * Notifies all registered listeners that UI settings have been changed.
   */
  fun fireUISettingsChanged() {
    if (!EDT.isCurrentThreadEdt()) {
      LOG.error(Throwable("UISettings should only be changed on the EDT"))
      // Fall back to invokeLater. It doesn't matter which invokeLater we use here, as this is something that shouldn't happen either way.
      SwingUtilities.invokeLater {
        fireUISettingsChanged()
      }
      return
    }

    // todo remove when all old properties will be converted
    state._incrementModificationCount()

    ColorBlindnessSupport.get(state.colorBlindness)?.filter?.let {
      IconLoader.setFilter(it)
    }

    // if this is the main UISettings instance (and not on first call to getInstance), push event to bus and to all current components
    if (this === cachedInstance) {
      runCatching {
        treeDispatcher.multicaster.uiSettingsChanged(this)
      }.getOrLogException(LOG)

      ApplicationManager.getApplication().messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(this)
    }
  }

  /**
   * Returns the value of [key] at the application context. An instance over a given component returns
   * [componentValue] instead.
   */
  private inline fun <T> readSetting(key: SettingsKey<T>, componentValue: () -> T): T =
    if (usesKeys) key.getValue() else componentValue()

  /**
   * Launches a write of [value] for [key], as [UISettingsKeys.setAsync] does. An instance over a given component
   * writes [value] with [writeComponent] instead.
   */
  private inline fun <T> writeSetting(key: SettingsKey<T>, value: T, writeComponent: () -> Unit) {
    if (usesKeys) UISettingsKeys.setAsync(key, value) else writeComponent()
  }

  /**
   * Calls [listener] on the EDT, in any modality, after each change of [key] at the application context, until
   * [parentDisposable] is disposed. [listener] gets the value that [readValue] returns at that time. It is not called
   * when that value equals the value of its previous call, or, before the first call, the value at the registration.
   * An instance over a given component adds no listener.
   */
  private fun <T> addValueListener(key: SettingsKey<T>, parentDisposable: Disposable, readValue: () -> T, listener: (T) -> Unit) {
    if (!usesKeys) return
    val coroutineScope = service<UISettingsKeysScope>().coroutineScope
    val listenerJob = SupervisorJob(coroutineScope.coroutineContext.job)
    listenerJob.cancelOnDispose(parentDisposable)
    var reportedValue = readValue()
    key.addListener(parentDisposable, SettingsContext.app()) { _, _ ->
      // Any modality, because a modal dialog such as Settings can write a key.
      coroutineScope.launch(listenerJob + Dispatchers.EDT + ModalityState.any().asContextElement()) {
        val value = readValue()
        if (value != reportedValue) {
          reportedValue = value
          listener(value)
        }
      }
    }
  }

  @Internal
  override fun getState(): UISettingsState = state

  @Internal
  override fun initializeComponent() {
    UISettingsKeys.ensureServiceCreated()
  }

  @Internal
  override fun noStateLoaded() {
    migrateFontParameters()
    migrateSearchEverywherePreview()
  }

  @Internal
  override fun loadState(state: UISettingsState) {
    this.state = state

    migrateOldSettings()
    if (migrateOldFontSettings()) {
      notRoamableOptions.fixFontSettings()
    }
    migrateFontParameters()
    migrateSearchEverywherePreview()

    // check tab placement in the editor
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

    if (LoadingState.APP_READY.isOccurred) {
      SwingUtilities.invokeLater {
        fireUISettingsChanged()
      }
    }
  }

  override fun getStateModificationCount(): Long {
    return state.modificationCount
  }

  @Suppress("DEPRECATION")
  private fun migrateOldSettings() {
    if (state.ideAAType != AntialiasingType.SUBPIXEL) {
      notRoamableOptions.ideAAType = state.ideAAType
      state.ideAAType = AntialiasingType.SUBPIXEL
    }
    if (state.editorAAType != AntialiasingType.SUBPIXEL) {
      notRoamableOptions.editorAAType = state.editorAAType
      state.editorAAType = AntialiasingType.SUBPIXEL
    }
    if (!state.allowMergeButtons) {
      Registry.get("ide.allow.merge.buttons").setValue(false)
      state.allowMergeButtons = true
    }
    if (state.separateMainMenu) {
      state.mainMenuDisplayMode = MainMenuDisplayMode.SEPARATE_TOOLBAR.name
      state.separateMainMenu = false
    }
  }

  private fun migrateSearchEverywherePreview() {
    if (PropertiesComponent.getInstance().isTrueValue(SEARCH_EVERYWHERE_PREVIEW_LEGACY_STATE_KEY)) {
      state.showPreviewInSearchEverywhere = true
      state._incrementModificationCount()
      PropertiesComponent.getInstance().unsetValue(SEARCH_EVERYWHERE_PREVIEW_LEGACY_STATE_KEY)
    }
  }

  @Suppress("DEPRECATION")
  private fun migrateOldFontSettings(): Boolean {
    var migrated = false
    if (state.fontSize != 0) {
      notRoamableOptions.fontSize = restoreFontSize(state.fontSize.toFloat(), state.fontScale)
      state.fontSize = 0
      migrated = true
    }
    if (state.fontScale != 0f) {
      fontScale = state.fontScale
      state.fontScale = 0f
      migrated = true
    }
    if (state.fontFace != null) {
      notRoamableOptions.fontFace = state.fontFace
      state.fontFace = null
      migrated = true
    }
    return migrated
  }

  private fun migrateFontParameters() {
    val presentationModeFontSize = state.presentationModeFontSize
    val notRoamableOptions = notRoamableOptions
    if (notRoamableOptions.presentationModeIdeScale != 0f) {
      return
    }

    notRoamableOptions.presentationModeIdeScale = if (presentationModeFontSize == 24 || notRoamableOptions.fontSize == 0f) {
      UISettingsUtils.defaultScale(isPresentation = true)
    }
    else {
      presentationModeFontSize.toFloat() / notRoamableOptions.fontSize
    }
  }

}

private const val SEARCH_EVERYWHERE_PREVIEW_LEGACY_STATE_KEY = "SearchEverywhere.previewPropertyKey"
