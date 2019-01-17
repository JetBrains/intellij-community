// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient
import javax.swing.SwingConstants

class UISettingsState : BaseState() {
  companion object {
    /**
     * Returns the default font size scaled by #defFontScale
     *
     * @return the default scaled font size
     */
    @JvmStatic
    val defFontSize: Int
      get() = Math.round(UIUtil.DEF_SYSTEM_FONT_SIZE * UISettings.defFontScale)
  }


  @get:OptionTag("FONT_FACE")
  @Deprecated("", replaceWith = ReplaceWith("NotRoamableUiOptions.fontFace"))
  var fontFace by string()

  @get:OptionTag("FONT_SIZE")
  @Deprecated("", replaceWith = ReplaceWith("NotRoamableUiOptions.fontSize"))
  var fontSize by property(defFontSize)

  @get:OptionTag("FONT_SCALE")
  @Deprecated("", replaceWith = ReplaceWith("NotRoamableUiOptions.fontScale"))
  var fontScale by property(0f)

  @get:OptionTag("RECENT_FILES_LIMIT")
  var recentFilesLimit by property(50)

  @get:OptionTag("CONSOLE_COMMAND_HISTORY_LIMIT")
  var consoleCommandHistoryLimit by property(300)
  @get:OptionTag("OVERRIDE_CONSOLE_CYCLE_BUFFER_SIZE")
  var overrideConsoleCycleBufferSize by property(false)
  @get:OptionTag("CONSOLE_CYCLE_BUFFER_SIZE_KB")
  var consoleCycleBufferSizeKb by property(1024)

  @get:OptionTag("EDITOR_TAB_LIMIT")
  var editorTabLimit by property(10)

  @get:OptionTag("REUSE_NOT_MODIFIED_TABS")
  var reuseNotModifiedTabs by property(false)
  @get:OptionTag("ANIMATE_WINDOWS")
  var animateWindows by property(true)
  @get:OptionTag("SHOW_TOOL_WINDOW_NUMBERS")
  var showToolWindowsNumbers by property(true)

  @get:OptionTag("HIDE_TOOL_STRIPES")
  var hideToolStripes by property(false)

  @get:OptionTag("WIDESCREEN_SUPPORT")
  var wideScreenSupport by property(false)
  @get:OptionTag("LEFT_HORIZONTAL_SPLIT")
  var leftHorizontalSplit by property(false)
  @get:OptionTag("RIGHT_HORIZONTAL_SPLIT")
  var rightHorizontalSplit by property(false)
  @get:OptionTag("SHOW_EDITOR_TOOLTIP")
  var showEditorToolTip by property(true)
  @get:OptionTag("SHOW_MEMORY_INDICATOR")
  var showMemoryIndicator by property(false)
  @get:OptionTag("ALLOW_MERGE_BUTTONS")
  var allowMergeButtons by property(true)
  @get:OptionTag("SHOW_MAIN_TOOLBAR")
  var showMainToolbar by property(false)
  @get:OptionTag("SHOW_STATUS_BAR")
  var showStatusBar by property(true)
  @get:OptionTag("SHOW_NAVIGATION_BAR")
  var showNavigationBar by property(true)
  @get:OptionTag("ALWAYS_SHOW_WINDOW_BUTTONS")
  var alwaysShowWindowsButton by property(false)
  @get:OptionTag("CYCLE_SCROLLING")
  var cycleScrolling by property(true)
  @get:OptionTag("SCROLL_TAB_LAYOUT_IN_EDITOR")
  var scrollTabLayoutInEditor by property(true)
  @get:OptionTag("HIDE_TABS_IF_NEED")
  var hideTabsIfNeed by property(true)
  @get:OptionTag("SHOW_CLOSE_BUTTON")
  var showCloseButton by property(true)
  @get:OptionTag("CLOSE_TAB_BUTTON_ON_THE_RIGHT")
  var closeTabButtonOnTheRight by property(true)
  @get:OptionTag("EDITOR_TAB_PLACEMENT")
  var editorTabPlacement: Int by property(SwingConstants.TOP)
  @get:OptionTag("HIDE_KNOWN_EXTENSION_IN_TABS")
  var hideKnownExtensionInTabs by property(false)
  @get:OptionTag("SHOW_ICONS_IN_QUICK_NAVIGATION")
  var showIconInQuickNavigation by property(true)

  @get:OptionTag("CLOSE_NON_MODIFIED_FILES_FIRST")
  var closeNonModifiedFilesFirst by property(false)
  @get:OptionTag("ACTIVATE_MRU_EDITOR_ON_CLOSE")
  var activeMruEditorOnClose by property(false)
  // TODO[anton] consider making all IDEs use the same settings
  @get:OptionTag("ACTIVATE_RIGHT_EDITOR_ON_CLOSE")
  var activeRightEditorOnClose by property(PlatformUtils.isAppCode())

  @get:OptionTag("IDE_AA_TYPE")
  @Deprecated("", replaceWith = ReplaceWith("NotRoamableUiOptions.ideAAType"))
  internal var ideAAType by property(AntialiasingType.SUBPIXEL)

  @get:OptionTag("EDITOR_AA_TYPE")
  @Deprecated("", replaceWith = ReplaceWith("NotRoamableUiOptions.editorAAType"))
  internal var editorAAType by property(AntialiasingType.SUBPIXEL)

  @get:OptionTag("COLOR_BLINDNESS")
  var colorBlindness by enum<ColorBlindness>()
  @get:OptionTag("MOVE_MOUSE_ON_DEFAULT_BUTTON")
  var moveMouseOnDefaultButton by property(false)
  @get:OptionTag("ENABLE_ALPHA_MODE")
  var enableAlphaMode by property(false)
  @get:OptionTag("ALPHA_MODE_DELAY")
  var alphaModeDelay by property(1500)
  @get:OptionTag("ALPHA_MODE_RATIO")
  var alphaModeRatio by property(0.5f)
  @get:OptionTag("MAX_CLIPBOARD_CONTENTS")
  var maxClipboardContents by property(5)
  @get:OptionTag("OVERRIDE_NONIDEA_LAF_FONTS")
  var overrideLafFonts by property(false)
  @get:OptionTag("SHOW_ICONS_IN_MENUS")
  var showIconsInMenus by property(!PlatformUtils.isAppCode())
  // IDEADEV-33409, should be disabled by default on MacOS
  @get:OptionTag("DISABLE_MNEMONICS")
  var disableMnemonics by property(SystemInfo.isMac)
  @get:OptionTag("DISABLE_MNEMONICS_IN_CONTROLS")
  var disableMnemonicsInControls by property(false)
  @get:OptionTag("USE_SMALL_LABELS_ON_TABS")
  var useSmallLabelsOnTabs by property(SystemInfo.isMac)
  @get:OptionTag("MAX_LOOKUP_WIDTH2")
  var maxLookupWidth by property(500)
  @get:OptionTag("MAX_LOOKUP_LIST_HEIGHT")
  var maxLookupListHeight by property(11)
  @get:OptionTag("HIDE_NAVIGATION_ON_FOCUS_LOSS")
  var hideNavigationOnFocusLoss by property(true)
  @get:OptionTag("DND_WITH_PRESSED_ALT_ONLY")
  var dndWithPressedAltOnly by property(false)
  @get:OptionTag("DEFAULT_AUTOSCROLL_TO_SOURCE")
  var defaultAutoScrollToSource by property(false)
  @Transient
  var presentationMode: Boolean = false
  @get:OptionTag("PRESENTATION_MODE_FONT_SIZE")
  var presentationModeFontSize by property(24)
  @get:OptionTag("MARK_MODIFIED_TABS_WITH_ASTERISK")
  var markModifiedTabsWithAsterisk by property(false)
  @get:OptionTag("SHOW_TABS_TOOLTIPS")
  var showTabsTooltips by property(true)
  @get:OptionTag("SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES")
  var showDirectoryForNonUniqueFilenames by property(true)
  var smoothScrolling by property(SystemInfo.isMac && (SystemInfo.isJetBrainsJvm || SystemInfo.IS_AT_LEAST_JAVA9))
  @get:OptionTag("NAVIGATE_TO_PREVIEW")
  var navigateToPreview by property(false)

  @get:OptionTag("SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY")
  var sortLookupElementsLexicographically by property(false)
  @get:OptionTag("MERGE_EQUAL_STACKTRACES")
  var mergeEqualStackTraces by property(true)
  @get:OptionTag("SORT_BOOKMARKS")
  var sortBookmarks by property(false)
  @get:OptionTag("PIN_FIND_IN_PATH_POPUP")
  var pinFindInPath by property(false)

  @Suppress("FunctionName")
  fun _incrementModificationCount() = incrementModificationCount()
}