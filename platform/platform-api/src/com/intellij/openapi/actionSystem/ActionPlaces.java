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
package com.intellij.openapi.actionSystem;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Possible places in the IDEA user interface where an action can appear.
 */

@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class ActionPlaces {
  public static final String UNKNOWN = "unknown";
  public static final String TOOLBAR = "toolbar";

  /**
   * consider to use {@link #isMainMenuOrActionSearch(String)} instead
   */
  public static final String MAIN_MENU = "MainMenu";
  public static final String MAIN_TOOLBAR = "MainToolbar";
  public static final String EDITOR_POPUP = "EditorPopup";
  public static final String EDITOR_TOOLBAR = "EditorToolbar";
  public static final String EDITOR_TAB_POPUP = "EditorTabPopup";
  public static final String EDITOR_TAB = "EditorTab";
  public static final String COMMANDER_POPUP = "CommanderPopup";
  public static final String COMMANDER_TOOLBAR = "CommanderToolbar";
  public static final String CONTEXT_TOOLBAR = "ContextToolbar";
  public static final String TOOLWINDOW_TITLE = "ToolwindowTitle";

  public static final String PROJECT_VIEW_POPUP = "ProjectViewPopup";
  public static final String PROJECT_VIEW_TOOLBAR = "ProjectViewToolbar";

  public static final String FAVORITES_VIEW_POPUP = "FavoritesPopup";
  public static final String FAVORITES_VIEW_TOOLBAR = "FavoritesViewToolbar";

  public static final String STATUS_BAR_PLACE = "StatusBarPlace";

  public static final String SCOPE_VIEW_POPUP = "ScopeViewPopup";
  public static final String ACTION_SEARCH = "GoToAction";

  public static final String TESTTREE_VIEW_POPUP = "TestTreeViewPopup";
  public static final String TESTTREE_VIEW_TOOLBAR = "TestTreeViewToolbar";
  public static final String TESTSTATISTICS_VIEW_POPUP = "TestStatisticsViewPopup";

  public static final String TYPE_HIERARCHY_VIEW_POPUP = "TypeHierarchyViewPopup";
  public static final String TYPE_HIERARCHY_VIEW_TOOLBAR = "TypeHierarchyViewToolbar";
  public static final String METHOD_HIERARCHY_VIEW_POPUP = "MethodHierarchyViewPopup";
  public static final String METHOD_HIERARCHY_VIEW_TOOLBAR = "MethodHierarchyViewToolbar";
  public static final String CALL_HIERARCHY_VIEW_POPUP = "CallHierarchyViewPopup";
  public static final String CALL_HIERARCHY_VIEW_TOOLBAR = "CallHierarchyViewToolbar";
  public static final String J2EE_ATTRIBUTES_VIEW_POPUP = "J2EEAttributesViewPopup";
  public static final String J2EE_VIEW_POPUP = "J2EEViewPopup";
  public static final String RUNNER_TOOLBAR = "RunnerToolbar";
  public static final String DEBUGGER_TOOLBAR = "DebuggerToolbar";
  public static final String USAGE_VIEW_POPUP = "UsageViewPopup";
  public static final String USAGE_VIEW_TOOLBAR = "UsageViewToolbar";
  public static final String STRUCTURE_VIEW_POPUP = "StructureViewPopup";
  public static final String STRUCTURE_VIEW_TOOLBAR = "StructureViewToolbar";
  public static final String NAVIGATION_BAR_POPUP = "NavBar";
  public static final String NAVIGATION_BAR_TOOLBAR = "NavBarToolbar";

  public static final String TODO_VIEW_POPUP = "TodoViewPopup";
  public static final String TODO_VIEW_TOOLBAR = "TodoViewToolbar";

  public static final String COMPILER_MESSAGES_POPUP = "CompilerMessagesPopup";
  public static final String COMPILER_MESSAGES_TOOLBAR = "CompilerMessagesToolbar";
  public static final String ANT_MESSAGES_POPUP = "AntMessagesPopup";
  public static final String ANT_MESSAGES_TOOLBAR = "AntMessagesToolbar";
  public static final String ANT_EXPLORER_POPUP = "AntExplorerPopup";
  public static final String ANT_EXPLORER_TOOLBAR = "AntExplorerToolbar";
  public static final String JS_BUILD_TOOL_POPUP = "JavaScriptBuildTool";

  //todo: probably these context should be splitted into several contexts
  public static final String CODE_INSPECTION = "CodeInspection";
  public static final String JAVADOC_TOOLBAR = "JavadocToolbar";
  public static final String JAVADOC_INPLACE_SETTINGS = "JavadocInplaceSettings";
  public static final String FILEHISTORY_VIEW_TOOLBAR = "FileHistoryViewToolbar";
  public static final String UPDATE_POPUP = "UpdatePopup";
  public static final String COMBO_PAGER = "ComboBoxPager";
  public static final String FILEVIEW_POPUP = "FileViewPopup";
  public static final String FILE_VIEW = "FileViewActionToolbal";
  public static final String CHECKOUT_POPUP = "CheckoutPopup";
  public static final String FILE_HISTORY_TOOLBAR = "FileHistoryToolbar";
  public static final String LVCS_DIRECTORY_HISTORY_POPUP = "LvcsHistoryPopup";
  public static final String LVCS_DIRECTORY_HISTORY_TOOLBAR = "LvcsDirectoryHistoryToolbar";
  public static final String GUI_DESIGNER_EDITOR_POPUP = "GuiDesigner.EditorPopup";
  public static final String GUI_DESIGNER_COMPONENT_TREE_POPUP = "GuiDesigner.ComponentTreePopup";
  public static final String GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP = "GuiDesigner.PropertyInspectorPopup";

  public static final String RUN_CONFIGURATIONS_COMBOBOX = "RunConfigurationsCombobox";
  public static final String CREATE_EJB_POPUP = "CreateEjbPopup";
  public static final String WELCOME_SCREEN = "WelcomeScreen";

  public static final String CHANGES_VIEW_TOOLBAR = "ChangesViewToolbar";
  public static final String CHANGES_VIEW_POPUP = "ChangesViewPopup";

  public static final String REMOTE_HOST_VIEW_POPUP = "RemoteHostPopup";
  public static final String REMOTE_HOST_DIALOG_POPUP = "RemoteHostDialogPopup";

  public static final String TFS_TREE_POPUP = "TfsTreePopup";
  public static final String ACTION_PLACE_VCS_QUICK_LIST_POPUP_ACTION = "ActionPlace.VcsQuickListPopupAction";
  public static final String ACTION_PLACE_QUICK_LIST_POPUP_ACTION = "ActionPlace.QuickListPopupAction";

  public static final String PHING_EXPLORER_POPUP = "PhingExplorerPopup";
  public static final String PHING_EXPLORER_TOOLBAR = "PhingExplorerToolbar";
  public static final String DOCK_MENU = "DockMenu";
  public static final String PHING_MESSAGES_TOOLBAR = "PhingMessagesToolbar";

  public static final String CHANGES_LOCAL_DIFF_SETTINGS = "CHANGES_LOCAL_DIFF_SETTINGS";
  public static final String DIFF_TOOLBAR = "DiffToolbar";

  public static final String ANALYZE_STACKTRACE_PANEL_TOOLBAR = "ANALYZE_STACKTRACE_PANEL_TOOLBAR";

  public static final String V8_CPU_PROFILING_POPUP = "V8_CPU_PROFILING_POPUP";
  public static final String V8_HEAP_PROFILING_POPUP = "V8_HEAP_PROFILING_POPUP";
  public static final String V8_HEAP_DIFF_PROFILING_POPUP = "V8_HEAP_DIFF_PROFILING_POPUP";

  public static final String RUN_DASHBOARD_POPUP = "RunDashboardPopup";

  private static final Set<String> ourToolbarPlaces = ContainerUtil.newHashSet(
    EDITOR_TOOLBAR, PROJECT_VIEW_TOOLBAR, TESTTREE_VIEW_TOOLBAR, MAIN_TOOLBAR, TOOLBAR, RUNNER_TOOLBAR,
    ANT_EXPLORER_TOOLBAR, ANT_MESSAGES_TOOLBAR, COMPILER_MESSAGES_TOOLBAR, TODO_VIEW_TOOLBAR, STRUCTURE_VIEW_TOOLBAR, USAGE_VIEW_TOOLBAR,
    DEBUGGER_TOOLBAR, CALL_HIERARCHY_VIEW_TOOLBAR, METHOD_HIERARCHY_VIEW_TOOLBAR, TYPE_HIERARCHY_VIEW_TOOLBAR, JAVADOC_TOOLBAR,
    FILE_HISTORY_TOOLBAR, FILEHISTORY_VIEW_TOOLBAR, LVCS_DIRECTORY_HISTORY_TOOLBAR, CHANGES_VIEW_TOOLBAR, PHING_EXPLORER_TOOLBAR,
    PHING_MESSAGES_TOOLBAR, ANALYZE_STACKTRACE_PANEL_TOOLBAR, DIFF_TOOLBAR, NAVIGATION_BAR_TOOLBAR
  );


  /**
   * @deprecated use {@link AnActionEvent#isFromActionToolbar()}
   */
  public static boolean isToolbarPlace(@NotNull String place) {
    return ourToolbarPlaces.contains(place);
  }

  public static boolean isMainMenuOrActionSearch(String place) {
    return MAIN_MENU.equals(place) || ACTION_SEARCH.equals(place);
  }

  private static final Set<String> ourPopupPlaces = ContainerUtil.newHashSet(
    EDITOR_POPUP, EDITOR_TAB_POPUP, COMMANDER_POPUP,
    PROJECT_VIEW_POPUP, FAVORITES_VIEW_POPUP, SCOPE_VIEW_POPUP, TESTTREE_VIEW_POPUP, TESTSTATISTICS_VIEW_POPUP, TYPE_HIERARCHY_VIEW_POPUP,
    METHOD_HIERARCHY_VIEW_POPUP, CALL_HIERARCHY_VIEW_POPUP, J2EE_ATTRIBUTES_VIEW_POPUP, J2EE_VIEW_POPUP, USAGE_VIEW_POPUP,
    STRUCTURE_VIEW_POPUP, TODO_VIEW_POPUP, COMPILER_MESSAGES_POPUP, ANT_MESSAGES_POPUP, ANT_EXPLORER_POPUP, UPDATE_POPUP,
    FILEVIEW_POPUP, CHECKOUT_POPUP, LVCS_DIRECTORY_HISTORY_POPUP, GUI_DESIGNER_EDITOR_POPUP, GUI_DESIGNER_COMPONENT_TREE_POPUP,
    GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP,
    CREATE_EJB_POPUP, CHANGES_VIEW_POPUP, REMOTE_HOST_VIEW_POPUP, REMOTE_HOST_DIALOG_POPUP, TFS_TREE_POPUP,
    ACTION_PLACE_VCS_QUICK_LIST_POPUP_ACTION, PHING_EXPLORER_POPUP, NAVIGATION_BAR_POPUP, JS_BUILD_TOOL_POPUP,
    V8_CPU_PROFILING_POPUP, V8_HEAP_PROFILING_POPUP, V8_HEAP_PROFILING_POPUP, RUN_DASHBOARD_POPUP
  );

  public static boolean isPopupPlace(@NotNull String place) {
    return ourPopupPlaces.contains(place);
  }
}
