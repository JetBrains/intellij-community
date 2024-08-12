// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.idea.AppMode
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.ReportValue
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.PlatformUtils
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.SwingConstants

class UISettingsState : BaseState() {
  @get:OptionTag("FONT_FACE")
  @Deprecated("", replaceWith = ReplaceWith("NotRoamableUiOptions.fontFace"))
  var fontFace: String? by string()

  @get:OptionTag("FONT_SIZE")
  @Deprecated("", replaceWith = ReplaceWith("NotRoamableUiOptions.fontSize"))
  var fontSize: Int by property(0)

  @get:OptionTag("FONT_SCALE")
  @Deprecated("", replaceWith = ReplaceWith("NotRoamableUiOptions.fontScale"))
  var fontScale: Float by property(0f)

  @get:ReportValue
  @get:OptionTag("RECENT_FILES_LIMIT")
  var recentFilesLimit: Int by property(50)

  @get:ReportValue
  @get:OptionTag("RECENT_LOCATIONS_LIMIT")
  var recentLocationsLimit: Int by property(25)

  @get:OptionTag("CONSOLE_COMMAND_HISTORY_LIMIT")
  var consoleCommandHistoryLimit: Int by property(300)
  @get:OptionTag("OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE")
  var overrideConsoleCycleBufferSize: Boolean by property(false)
  @get:OptionTag("CONSOLE_CYCLE_BUFFER_SIZE_KB")
  var consoleCycleBufferSizeKb: Int by property(1024)

  @get:ReportValue
  @get:OptionTag("EDITOR_TAB_LIMIT")
  var editorTabLimit: Int by property(10)

  @get:OptionTag("REUSE_NOT_MODIFIED_TABS")
  var reuseNotModifiedTabs: Boolean by property(false)
  @get:OptionTag("OPEN_TABS_IN_MAIN_WINDOW")
  var openTabsInMainWindow: Boolean by property(false)
  @get:OptionTag("OPEN_IN_PREVIEW_TAB_IF_POSSIBLE")
  var openInPreviewTabIfPossible: Boolean by property(false)
  @get:OptionTag("SHOW_TOOL_WINDOW_NUMBERS")
  var showToolWindowsNumbers: Boolean by property(false)

  @get:OptionTag("SHOW_TOOL_WINDOW_NAMES")
  var showToolWindowsNames: Boolean by property(false)
  @get:OptionTag("TOOL_WINDOW_LEFT_SIDE_CUSTOM_WIDTH")
  var toolWindowLeftSideCustomWidth: Int by property(0)
  @get:OptionTag("TOOL_WINDOW_RIGHT_SIDE_CUSTOM_WIDTH")
  var toolWindowRightSideCustomWidth: Int by property(0)

  @get:OptionTag("HIDE_TOOL_STRIPES")
  var hideToolStripes: Boolean by property(false)

  @get:OptionTag("WIDESCREEN_SUPPORT")
  var wideScreenSupport: Boolean by property(false)
  @get:OptionTag("REMEMBER_SIZE_FOR_EACH_TOOL_WINDOW_OLD_UI")
  var rememberSizeForEachToolWindowOldUI: Boolean by property(true)
  @get:OptionTag("REMEMBER_SIZE_FOR_EACH_TOOL_WINDOW_NEW_UI")
  var rememberSizeForEachToolWindowNewUI: Boolean by property(false)
  @get:OptionTag("LEFT_HORIZONTAL_SPLIT")
  var leftHorizontalSplit: Boolean by property(false)
  @get:OptionTag("RIGHT_HORIZONTAL_SPLIT")
  var rightHorizontalSplit: Boolean by property(false)
  @get:OptionTag("SHOW_EDITOR_TOOLTIP")
  var showEditorToolTip: Boolean by property(true)
  @get:OptionTag("SHOW_WRITE_THREAD_INDICATOR")
  var showWriteThreadIndicator: Boolean by property(false)
  @get:OptionTag("ALLOW_MERGE_BUTTONS")
  var allowMergeButtons: Boolean by property(true)
  @get:OptionTag("SHOW_MAIN_TOOLBAR")
  var showMainToolbar: Boolean by property(false)
  @get:OptionTag("SHOW_NEW_MAIN_TOOLBAR")
  var showNewMainToolbar: Boolean by property(true)
  @get:OptionTag("SHOW_STATUS_BAR")
  var showStatusBar: Boolean by property(true)
  @get:OptionTag("SHOW_MAIN_MENU")
  var showMainMenu: Boolean by property(true)
  @get:OptionTag("SHOW_NAVIGATION_BAR")
  var showNavigationBar: Boolean by property(true)
  @get:OptionTag("NAVIGATION_BAR_LOCATION")
  var navigationBarLocation: NavBarLocation by enum(NavBarLocation.BOTTOM)
  @get:OptionTag("SHOW_NAVIGATION_BAR_MEMBERS")
  var showMembersInNavigationBar: Boolean by property(true)
  @get:OptionTag("SCROLL_TAB_LAYOUT_IN_EDITOR")
  var scrollTabLayoutInEditor: Boolean by property(true)
  @get:OptionTag("HIDE_TABS_IF_NEED")
  var hideTabsIfNeeded: Boolean by property(true)
  @get:OptionTag("SHOW_PINNED_TABS_IN_A_SEPARATE_ROW")
  var showPinnedTabsInASeparateRow: Boolean by property(false)
  @get:OptionTag("SHOW_CLOSE_BUTTON")
  var showCloseButton: Boolean by property(true)
  @get:OptionTag("CLOSE_TAB_BUTTON_ON_THE_RIGHT")
  var closeTabButtonOnTheRight: Boolean by property(true)
  @get:OptionTag("EDITOR_TAB_PLACEMENT")
  @get:ReportValue
  var editorTabPlacement: Int by property(SwingConstants.TOP)
  @get:OptionTag("SHOW_FILE_ICONS_IN_TABS")
  var showFileIconInTabs: Boolean by property(true)
  @get:OptionTag("HIDE_KNOWN_EXTENSION_IN_TABS")
  var hideKnownExtensionInTabs: Boolean by property(false)
  var showTreeIndentGuides: Boolean by property(false)
  var compactTreeIndents: Boolean by property(false)
  var expandNodesWithSingleClick: Boolean by property(false)
  @get:ReportValue
  @get:OptionTag("UI_DENSITY")
  var uiDensity: UIDensity by enum(UIDensity.DEFAULT)

  @get:OptionTag("DIFFERENTIATE_PROJECTS")
  var differentiateProjects: Boolean by property(true)

  @get:OptionTag("SORT_TABS_ALPHABETICALLY")
  var sortTabsAlphabetically: Boolean by property(false)
  @get:OptionTag("KEEP_TABS_ALPHABETICALLY_SORTED")
  var alwaysKeepTabsAlphabeticallySorted: Boolean by property(false)
  @get:OptionTag("OPEN_TABS_AT_THE_END")
  var openTabsAtTheEnd: Boolean by property(false)

  @get:OptionTag("CLOSE_NON_MODIFIED_FILES_FIRST")
  var closeNonModifiedFilesFirst: Boolean by property(false)
  @get:OptionTag("ACTIVATE_MRU_EDITOR_ON_CLOSE")
  var activeMruEditorOnClose: Boolean by property(false)
  // TODO[anton] consider making all IDEs use the same settings
  @get:OptionTag("ACTIVATE_RIGHT_EDITOR_ON_CLOSE")
  var activeRightEditorOnClose: Boolean by property(PlatformUtils.isAppCode())

  @get:OptionTag("IDE_AA_TYPE")
  @Deprecated("", replaceWith = ReplaceWith("NotRoamableUiOptions.ideAAType"))
  internal var ideAAType: AntialiasingType by enum(AntialiasingType.SUBPIXEL)

  @get:OptionTag("EDITOR_AA_TYPE")
  @Deprecated("", replaceWith = ReplaceWith("NotRoamableUiOptions.editorAAType"))
  internal var editorAAType: AntialiasingType by enum(AntialiasingType.SUBPIXEL)

  @get:OptionTag("COLOR_BLINDNESS")
  var colorBlindness: ColorBlindness? by enum<ColorBlindness>()
  @get:OptionTag("CONTRAST_SCROLLBARS")
  var useContrastScrollBars: Boolean by property(false)

  @get:OptionTag("MOVE_MOUSE_ON_DEFAULT_BUTTON")
  var moveMouseOnDefaultButton: Boolean by property(false)
  @get:OptionTag("ENABLE_ALPHA_MODE")
  var enableAlphaMode: Boolean by property(false)
  @get:OptionTag("ALPHA_MODE_DELAY")
  var alphaModeDelay: Int by property(1500)
  @get:OptionTag("ALPHA_MODE_RATIO")
  var alphaModeRatio: Float by property(0.5f)
  @get:OptionTag("SHOW_ICONS_IN_MENUS")
  var showIconsInMenus: Boolean by property(true)
  @get:OptionTag("KEEP_POPUPS_FOR_TOGGLES")
  var keepPopupsForToggles: Boolean by property(true)
  // IDEADEV-33409, should be disabled by default on MacOS
  @get:OptionTag("DISABLE_MNEMONICS")
  var disableMnemonics: Boolean by property(SystemInfoRt.isMac)
  @get:OptionTag("DISABLE_MNEMONICS_IN_CONTROLS")
  var disableMnemonicsInControls: Boolean by property(false)
  @get:OptionTag("USE_SMALL_LABELS_ON_TABS")
  var useSmallLabelsOnTabs: Boolean by property(SystemInfoRt.isMac)
  @get:OptionTag("MAX_LOOKUP_WIDTH2")
  var maxLookupWidth: Int by property(500)
  @get:OptionTag("MAX_LOOKUP_LIST_HEIGHT")
  var maxLookupListHeight: Int by property(11)
  @get:OptionTag("DND_WITH_PRESSED_ALT_ONLY")
  var dndWithPressedAltOnly: Boolean by property(false)
  @get:OptionTag("SEPARATE_MAIN_MENU")
  var separateMainMenu: Boolean by property(false)
  @get:OptionTag("DEFAULT_AUTOSCROLL_TO_SOURCE")
  var defaultAutoScrollToSource: Boolean by property(false)
  @get:Transient
  var presentationMode: Boolean = false
  @get:OptionTag("PRESENTATION_MODE_FONT_SIZE")
  var presentationModeFontSize: Int by property(24)
  @get:OptionTag("MARK_MODIFIED_TABS_WITH_ASTERISK")
  var markModifiedTabsWithAsterisk: Boolean by property(false)
  @get:OptionTag("SHOW_TABS_TOOLTIPS")
  var showTabsTooltips: Boolean by property(true)
  @get:OptionTag("SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES")
  var showDirectoryForNonUniqueFilenames: Boolean by property(true)
  var smoothScrolling: Boolean by property(true)
  @get:OptionTag("NAVIGATE_TO_PREVIEW")
  var navigateToPreview: Boolean by property(false)
  @get:OptionTag("FULL_PATHS_IN_TITLE_BAR")
  var fullPathsInWindowHeader: Boolean by property(false)
  @get:OptionTag("BORDERLESS_MODE")
  var mergeMainMenuWithWindowTitle: Boolean by property(
    (SystemInfo.isWin10OrNewer || (SystemInfo.isUnix && !SystemInfo.isMac)) && SystemInfo.isJetBrainsJvm)

  var animatedScrolling: Boolean by property(!AppMode.isRemoteDevHost() && (!SystemInfoRt.isMac || !SystemInfo.isJetBrainsJvm))
  var animatedScrollingDuration: Int by property(getDefaultAnimatedScrollingDuration())

  var animatedScrollingCurvePoints: Int by property(
    when {
      SystemInfoRt.isWindows -> 1684366536
      SystemInfoRt.isMac -> 845374563
      else -> 729434056
    }
  )

  @get:OptionTag("SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY")
  var sortLookupElementsLexicographically: Boolean by property(false)
  @get:OptionTag("MERGE_EQUAL_STACKTRACES")
  var mergeEqualStackTraces: Boolean by property(true)
  @get:OptionTag("SORT_BOOKMARKS")
  var sortBookmarks: Boolean by property(false)
  @get:OptionTag("PIN_FIND_IN_PATH_POPUP")
  var pinFindInPath: Boolean by property(false)
  @get:OptionTag("SHOW_INPLACE_COMMENTS")
  var showInplaceComments: Boolean by property(false)
  @get:Internal
  @set:Internal
  @get:OptionTag("SHOW_INPLACE_COMMENTS_INTERNAL")
  var showInplaceCommentsInternal: Boolean by property(false)

  @get:OptionTag("SHOW_VISUAL_FORMATTING_LAYER")
  var showVisualFormattingLayer: Boolean by property(false)

  @get:OptionTag("SHOW_BREAKPOINTS_OVER_LINE_NUMBERS")
  var showBreakpointsOverLineNumbers: Boolean by property(true)

  @get:OptionTag("SHOW_PREVIEW_IN_SEARCH_EVERYWHERE")
  var showPreviewInSearchEverywhere: Boolean by property(false)

  @Suppress("FunctionName")
  fun _incrementModificationCount(): Unit = incrementModificationCount()
}

fun getDefaultAnimatedScrollingDuration(): Int {
  return when {
    SystemInfoRt.isWindows -> 200
    SystemInfoRt.isMac -> 50
    else -> 150
  }
}