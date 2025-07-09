// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * Common action place values where an action can appear.
 * <p>
 * To alter logic for an action prefer {@link AnActionEvent#getUiKind()}.
 * The {@link AnActionEvent#getPlace()} is intended for stats, logging and debugging.
 *
 * @see AnActionEvent#getPlace()
 * @see AnActionEvent#getUiKind()
 */
public abstract class ActionPlaces {
  public static final String UNKNOWN = "unknown";
  public static final String NEW_PROJECT_WIZARD = "NewProjectWizard";
  public static final String TOOLBAR = "toolbar";
  public static final String POPUP = "popup";

  // prefer isMainMenuOrActionSearch(String) for the following places
  public static final String KEYBOARD_SHORTCUT = "keyboard shortcut";
  public static final String MOUSE_SHORTCUT = "mouse shortcut";
  public static final String FORCE_TOUCH = "force touch";
  public static final String MAIN_MENU = "MainMenu";

  public static final String MAIN_TOOLBAR = "MainToolbar";
  public static final String EDITOR_POPUP = "EditorPopup";
  public static final String EDITOR_TOOLBAR = "EditorToolbar";

  public static final String CUSTOMIZE_ACTIONS_PANEL = "CustomizeActionsPanel";

  public static final String EDITOR_FLOATING_TOOLBAR = "EditorFloatingToolbar";
  public static final String EDITOR_TAB_POPUP = "EditorTabPopup";
  public static final String EDITOR_TAB = "EditorTab";
  public static final String TABS_MORE_TOOLBAR = "TabsMoreToolbar";
  public static final String EDITOR_GUTTER = "ICON_NAVIGATION";
  public static final String EDITOR_GUTTER_POPUP = "ICON_NAVIGATION_SECONDARY_BUTTON";
  public static final String EDITOR_ANNOTATIONS_AREA_POPUP = "EditorAnnotationsAreaPopup";
  public static final String EDITOR_NOTIFICATION_POPUP = "EDITOR_NOTIFICATION_POPUP";
  public static final String EDITOR_INLAY = "EditorInlay";
  public static final String RIGHT_EDITOR_GUTTER_POPUP = "RightEditorGutterPopup";
  public static final String COMMANDER_POPUP = "CommanderPopup";
  public static final String COMMANDER_TOOLBAR = "CommanderToolbar";
  public static final String CONTEXT_TOOLBAR = "ContextToolbar";
  public static final String TOOLWINDOW_POPUP = "ToolwindowPopup";
  public static final String TOOLWINDOW_TITLE = "ToolwindowTitle";
  public static final String TOOLWINDOW_CONTENT = "ToolwindowContent";
  public static final String EDITOR_INSPECTIONS_TOOLBAR = "EditorInspectionsToolbar";
  public static final String EDITOR_HINT = "EditorHint";
  public static final String LEARN_TOOLWINDOW = "LearnToolwindow";
  public static final String TOOLWINDOW_GRADLE = "Gradle tool window";
  public static final String NEW_UI_RUN_TOOLBAR = "NewUiRunToolbar";

  public static final String PROJECT_VIEW_POPUP = "ProjectViewPopup";
  public static final String PROJECT_VIEW_TOOLBAR = "ProjectViewToolbar";

  public static final String REVEAL_IN_POPUP = "RevealInPopup";

  /** @deprecated replaced by {@link #BOOKMARKS_VIEW_POPUP} */
  @Deprecated(forRemoval = true)
  public static final String FAVORITES_VIEW_POPUP = "FavoritesPopup";
  public static final String BOOKMARKS_VIEW_POPUP = "BookmarksPopup";

  public static final String STATUS_BAR_PLACE = "StatusBarPlace";

  public static final String FIND_IN_FILES_POPUP = "FindInFilesPopup";

  public static final String SCOPE_VIEW_POPUP = "ScopeViewPopup";
  public static final String ACTION_SEARCH = "GoToAction";

  public static final String TESTTREE_VIEW_POPUP = "TestTreeViewPopup";
  public static final String TESTTREE_VIEW_TOOLBAR = "TestTreeViewToolbar";

  public static final String TYPE_HIERARCHY_VIEW_POPUP = "TypeHierarchyViewPopup";
  public static final String TYPE_HIERARCHY_VIEW_TOOLBAR = "TypeHierarchyViewToolbar";
  public static final String METHOD_HIERARCHY_VIEW_POPUP = "MethodHierarchyViewPopup";
  public static final String METHOD_HIERARCHY_VIEW_TOOLBAR = "MethodHierarchyViewToolbar";
  public static final String CALL_HIERARCHY_VIEW_POPUP = "CallHierarchyViewPopup";
  public static final String CALL_HIERARCHY_VIEW_TOOLBAR = "CallHierarchyViewToolbar";
  public static final String SIMILAR_USAGES_PREVIEW_TOOLBAR = "SimilarUsagesPreviewToolbar";
  public static final String J2EE_ATTRIBUTES_VIEW_POPUP = "J2EEAttributesViewPopup";
  public static final String J2EE_VIEW_POPUP = "J2EEViewPopup";
  public static final String RUNNER_TOOLBAR = "RunnerToolbar";
  public static final String RUNNER_LAYOUT_BUTTON_TOOLBAR = "RunnerLayoutButtonToolbar";
  public static final String DEBUGGER_TOOLBAR = "DebuggerToolbar";
  public static final String ATTACH_DIALOG_TOOLBAR = "AttachDialogToolbar";
  public static final String USAGE_VIEW_POPUP = "UsageViewPopup";
  public static final String USAGE_VIEW_TOOLBAR = "UsageViewToolbar";
  public static final String SHOW_USAGES_POPUP_TOOLBAR = "ShowUsagesPopupToolbar";
  public static final String STRUCTURE_VIEW_POPUP = "StructureViewPopup";
  public static final String STRUCTURE_VIEW_TOOLBAR = "StructureViewToolbar";
  public static final String STRUCTURE_VIEW_FLOATING_TOOLBAR = "StructureViewFloatingToolbar";
  public static final String NAVIGATION_BAR_POPUP = "NavBar";
  public static final String NAVIGATION_BAR_TOOLBAR = "NavBarToolbar";
  public static final String RUN_TOOLBAR_LEFT_SIDE = "RunToolbarLeftSide";
  public static final String TOOLBAR_DECORATOR_TOOLBAR = "ToolbarDecorator";

  public static final String TODO_VIEW_POPUP = "TodoViewPopup";
  public static final String TODO_VIEW_TOOLBAR = "TodoViewToolbar";
  public static final String PROBLEMS_VIEW_POPUP = "ProblemsViewPopup";
  public static final String PROBLEMS_VIEW_TOOLBAR = "ProblemsViewToolbar";

  public static final String COPY_REFERENCE_POPUP = "CopyReferencePopup";

  public static final String COMPILER_MESSAGES_POPUP = "CompilerMessagesPopup";
  public static final String COMPILER_MESSAGES_TOOLBAR = "CompilerMessagesToolbar";
  public static final String ANT_MESSAGES_POPUP = "AntMessagesPopup";
  public static final String ANT_MESSAGES_TOOLBAR = "AntMessagesToolbar";
  public static final String ANT_EXPLORER_POPUP = "AntExplorerPopup";
  public static final String ANT_EXPLORER_TOOLBAR = "AntExplorerToolbar";
  public static final String JS_BUILD_TOOL_POPUP = "JavaScriptBuildTool";

  //todo: probably these context should be split into several contexts
  public static final String CODE_INSPECTION = "CodeInspection";
  public static final String JAVADOC_TOOLBAR = "JavadocToolbar";
  public static final String JAVADOC_INPLACE_SETTINGS = "JavadocInplaceSettings";
  public static final String FILEHISTORY_VIEW_TOOLBAR = "FileHistoryViewToolbar";
  public static final String UPDATE_POPUP = "UpdatePopup";
  public static final String FILEVIEW_POPUP = "FileViewPopup";
  public static final String CHECKOUT_POPUP = "CheckoutPopup";
  public static final String LVCS_DIRECTORY_HISTORY_POPUP = "LvcsHistoryPopup";
  public static final String GUI_DESIGNER_EDITOR_POPUP = "GuiDesigner.EditorPopup";
  public static final String GUI_DESIGNER_COMPONENT_TREE_POPUP = "GuiDesigner.ComponentTreePopup";
  public static final String GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP = "GuiDesigner.PropertyInspectorPopup";

  public static final String RUN_CONFIGURATIONS_COMBOBOX = "RunConfigurationsCombobox";
  public static final String RUN_CONFIGURATION_EDITOR = "RunConfigurationEditor";

  public static final String RUN_ANYTHING_POPUP = "RunAnythingPopup";

  public static final String WELCOME_SCREEN = "WelcomeScreen";
  public static final String WELCOME_SCREEN_QUICK_PANEL = "WelcomeScreen.QuickPanel";
  @ApiStatus.Internal
  public static final String WELCOME_SCREEN_NON_MODAL = "WelcomeScreen.NonModal";

  public static final String CHANGES_VIEW_TOOLBAR = "ChangesViewToolbar";
  public static final String CHANGES_VIEW_POPUP = "ChangesViewPopup";

  public static final String DATABASE_VIEW_TOOLBAR = "DatabaseViewToolbar";
  public static final String DATABASE_VIEW_POPUP = "DatabaseViewPopup";
  public static final String GRID_FLOATING_PAGING_TOOLBAR = "GridFloatingPagingToolbar";

  public static final String REMOTE_HOST_VIEW_POPUP = "RemoteHostPopup";
  public static final String REMOTE_HOST_DIALOG_POPUP = "RemoteHostDialogPopup";

  public static final String TFS_TREE_POPUP = "TfsTreePopup";
  public static final String ACTION_PLACE_VCS_QUICK_LIST_POPUP_ACTION = "ActionPlace.VcsQuickListPopupAction";
  public static final String ACTION_PLACE_QUICK_LIST_POPUP_ACTION = "ActionPlace.QuickListPopupAction";

  public static final String PHING_EXPLORER_POPUP = "PhingExplorerPopup";
  public static final String PHING_EXPLORER_TOOLBAR = "PhingExplorerToolbar";
  public static final String DOCK_MENU = "DockMenu";
  public static final String PHING_MESSAGES_TOOLBAR = "PhingMessagesToolbar";

  public static final String COMPOSER_EDITOR_NOTIFICATION_PANEL = "ComposerEditorNotificationPanel";
  public static final String COMPOSER_EDITOR_NOTIFICATION_PANEL_EXTRA = "ComposerEditorNotificationPanel.ExtraActions";
  public static final String COMPOSER_LOG_RERUN = "ComposerLogRerun";
  public static final String PHP_TEST_RUN_CONFIGURATION = "PhpTestRunConfiguration";

  public static final String DIFF_TOOLBAR = "DiffToolbar";
  public static final String DIFF_RIGHT_TOOLBAR = "DiffRightToolbar";

  public static final String ANALYZE_STACKTRACE_PANEL_TOOLBAR = "ANALYZE_STACKTRACE_PANEL_TOOLBAR";

  public static final String V8_CPU_PROFILING_POPUP = "V8_CPU_PROFILING_POPUP";
  public static final String V8_HEAP_PROFILING_POPUP = "V8_HEAP_PROFILING_POPUP";
  public static final String V8_HEAP_DIFF_PROFILING_POPUP = "V8_HEAP_DIFF_PROFILING_POPUP";

  public static final String RUN_DASHBOARD_POPUP = "RunDashboardPopup";
  public static final String SERVICES_POPUP = "ServicesPopup";
  public static final String SERVICES_TOOLBAR = "ServicesToolbar";
  public static final String SERVICES_TREE_TOOLBAR = "ServicesToolbar";

  public static final String TOUCHBAR_GENERAL = "TouchBarGeneral";

  public static final String REFACTORING_QUICKLIST = "RefactoringQuickList";

  public static final String INTENTION_MENU = "IntentionMenu";

  public static final String TEXT_EDITOR_WITH_PREVIEW = "TextEditorWithPreview";

  public static final String NOTIFICATION = "Notification";

  public static final String FILE_STRUCTURE_POPUP = "FileStructurePopup";

  public static final String QUICK_SWITCH_SCHEME_POPUP = "QuickSwitchSchemePopup";

  public static final String TOOLWINDOW_TOOLBAR_BAR = "ToolwindowToolbar";

  public static final String SETTINGS_HISTORY = "SettingsHistory";

  public static final String PROJECT_WIDGET_POPUP = "ProjectWidgetPopup";

  public static final String NEW_UI_ONBOARDING = "NewUiOnboarding";

  // Vcs Log
  public static final String VCS_LOG_TABLE_PLACE = "Vcs.Log.ContextMenu";
  public static final String VCS_LOG_TOOLBAR_PLACE = "Vcs.Log.Toolbar";
  public static final String VCS_HISTORY_PLACE = "Vcs.FileHistory.ContextMenu";
  public static final String VCS_HISTORY_TOOLBAR_PLACE = "Vcs.FileHistory.Toolbar";
  public static final String VCS_LOG_TOOLBAR_POPUP_PLACE = "Vcs.Log.Toolbar.Popup";
  public static final String VCS_LOG_BRANCHES_PLACE = "Vcs.Log.Branches.Tree";

  public static final String VCS_TOOLBAR_WIDGET = "Vcs.Toolbar.Widget";

  public static final String CHANGES_VIEW_EMPTY_STATE = "ChangesView.EmptyState";
  public static final String COMMIT_VIEW_EMPTY_STATE = "CommitView.EmptyState";

  /* Rider */
  public static final String RIDER_UNIT_TESTS_LEFT_TOOLBAR = "UnitTests.LeftToolbar";
  public static final String RIDER_UNIT_TESTS_TOP_TOOLBAR = "UnitTests.TopToolbar";
  public static final String RIDER_UNIT_TESTS_SESSION_POPUP = "UnitTests.SessionPopup";
  public static final String RIDER_UNIT_TESTS_EXPLORER_POPUP = "UnitTests.ExplorerPopup";
  public static final String RIDER_UNIT_TESTS_PROGRESSBAR_POPUP = "UnitTests.ProgressBarPopup";
  public static final String RIDER_UNIT_TESTS_QUICKLIST = "UnitTests.QuickList";

  // Marker to avoid executing editor action on backend for patch engine
  @ApiStatus.Internal
  public static final Key<Boolean> EXECUTE_EDITOR_ACTION_ON_FRONTEND = Key.create("EXECUTE_EDITOR_ACTION_ON_FRONTEND");

  public static final String IMPORT_SETTINGS_DIALOG = "Import.Settings.Dialog";

  public static final String JUPYTER_NOTEBOOK_CELL_OUTPUT_POPUP = "Editor.Jupyter.Cell.Output.Popup";

  /** @deprecated Use {@link AnActionEvent#getUiKind()} and {@link AnActionEvent#getInputEvent()} instead */
  @Deprecated
  public static boolean isMainMenuOrActionSearch(String place) {
    return MAIN_MENU.equals(place) || ACTION_SEARCH.equals(place) || isShortcutPlace(place) ||
           place != null && place.startsWith(POPUP_PREFIX) && isMainMenuOrActionSearch(place.substring(POPUP_PREFIX.length()));
  }

  /** @deprecated Use {@link AnActionEvent#getInputEvent()} instead */
  @Deprecated
  public static boolean isShortcutPlace(String place) {
    return KEYBOARD_SHORTCUT.equals(place) || MOUSE_SHORTCUT.equals(place) || FORCE_TOUCH.equals(place);
  }

  private static final Set<String> ourCommonPlaces;
  static {
    Set<String> set = new HashSet<>();
    for (Field field : ActionPlaces.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isPublic(field.getModifiers())) continue;
      Object value = ReflectionUtil.getFieldValue(field, null);
      if (value instanceof String) set.add((String)value);
    }
    ourCommonPlaces = set;
  }

  private static final Set<String> ourPopupPlaces = ContainerUtil.newHashSet(
    POPUP, EDITOR_POPUP, EDITOR_TAB_POPUP, QUICK_SWITCH_SCHEME_POPUP, COMMANDER_POPUP, INTENTION_MENU,
    PROJECT_VIEW_POPUP, BOOKMARKS_VIEW_POPUP, SCOPE_VIEW_POPUP, TESTTREE_VIEW_POPUP, TYPE_HIERARCHY_VIEW_POPUP,
    METHOD_HIERARCHY_VIEW_POPUP, CALL_HIERARCHY_VIEW_POPUP, J2EE_ATTRIBUTES_VIEW_POPUP, J2EE_VIEW_POPUP, USAGE_VIEW_POPUP,
    STRUCTURE_VIEW_POPUP, TODO_VIEW_POPUP, COMPILER_MESSAGES_POPUP, COPY_REFERENCE_POPUP,
    ANT_MESSAGES_POPUP, ANT_EXPLORER_POPUP, UPDATE_POPUP,
    FILEVIEW_POPUP, CHECKOUT_POPUP, LVCS_DIRECTORY_HISTORY_POPUP, GUI_DESIGNER_EDITOR_POPUP, GUI_DESIGNER_COMPONENT_TREE_POPUP,
    GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP, TOOLWINDOW_POPUP, WELCOME_SCREEN,
    ACTION_PLACE_QUICK_LIST_POPUP_ACTION, ACTION_PLACE_QUICK_LIST_POPUP_ACTION, REFACTORING_QUICKLIST,
    CHANGES_VIEW_POPUP, DATABASE_VIEW_POPUP, REMOTE_HOST_VIEW_POPUP, REMOTE_HOST_DIALOG_POPUP, TFS_TREE_POPUP,
    ACTION_PLACE_VCS_QUICK_LIST_POPUP_ACTION, PHING_EXPLORER_POPUP, NAVIGATION_BAR_POPUP, JS_BUILD_TOOL_POPUP,
    V8_CPU_PROFILING_POPUP, V8_HEAP_PROFILING_POPUP, V8_HEAP_PROFILING_POPUP, RUN_DASHBOARD_POPUP, SERVICES_POPUP, EDITOR_GUTTER_POPUP,
    EDITOR_ANNOTATIONS_AREA_POPUP, EDITOR_NOTIFICATION_POPUP,
    RUN_ANYTHING_POPUP, RUN_TOOLBAR_LEFT_SIDE,
    VCS_LOG_TABLE_PLACE, VCS_HISTORY_PLACE, VCS_LOG_TOOLBAR_POPUP_PLACE, VCS_LOG_BRANCHES_PLACE, VCS_TOOLBAR_WIDGET,
    PROJECT_WIDGET_POPUP,
    JUPYTER_NOTEBOOK_CELL_OUTPUT_POPUP,
    REVEAL_IN_POPUP, FIND_IN_FILES_POPUP
  );

  private static final String POPUP_PREFIX = "popup@";

  /** @deprecated Use {@link AnActionEvent#getUiKind()} or {@link AnActionEvent#isFromContextMenu()} instead */
  @Deprecated
  public static boolean isPopupPlace(@NotNull String place) {
    return ourPopupPlaces.contains(place) || place.startsWith(POPUP_PREFIX);
  }

  public static boolean isCommonPlace(@NotNull String place) {
    return ourPopupPlaces.contains(place) || ourCommonPlaces.contains(place) ||
           place.startsWith(POPUP_PREFIX) && isCommonPlace(place.substring(POPUP_PREFIX.length()));
  }

  public static @NotNull String getActionGroupPopupPlace(@Nullable String place) {
    return place == null ? POPUP : POPUP_PREFIX + place;
  }

  public static @NotNull String getPopupPlace(@Nullable String place) {
    return place == null ? POPUP : isPopupPlace(place) ? place : POPUP_PREFIX + place;
  }

  /**
   * Returns {@code true} if the action is invoked from the regular menu or via a shortcut on macOS.
   * Use only for actions that are registered in {@link com.intellij.ui.mac.MacOSApplicationProvider}, to avoid duplicate processing.
   */
  @ApiStatus.Internal
  public static boolean isMacSystemMenuAction(@NotNull AnActionEvent e) {
    return SystemInfo.isMac && (e.isFromMainMenu() || e.getInputEvent() != null);
  }
}
