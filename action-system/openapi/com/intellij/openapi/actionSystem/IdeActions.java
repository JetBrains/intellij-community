/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;

/**
 * Identifiers for standard actions and action groups supported by IDEA.
 */
@NonNls  
public interface IdeActions {
  String ACTION_EDITOR_CUT = "EditorCut";
  String ACTION_EDITOR_COPY = "EditorCopy";
  String ACTION_EDITOR_PASTE = "EditorPaste";
  String ACTION_EDITOR_DELETE = "EditorDelete";
  String ACTION_EDITOR_ENTER = "EditorEnter";
  String ACTION_EDITOR_START_NEW_LINE = "EditorStartNewLine";
  String ACTION_EDITOR_SPLIT = "EditorSplitLine";
  String ACTION_EDITOR_MOVE_LINE_START = "EditorLineStart";
  String ACTION_EDITOR_MOVE_LINE_END = "EditorLineEnd";
  String ACTION_EDITOR_SELECT_WORD_AT_CARET = "EditorSelectWord";
  String ACTION_EDITOR_UNSELECT_WORD_AT_CARET = "EditorUnSelectWord";
  String ACTION_EDITOR_BACKSPACE = "EditorBackSpace";
  String ACTION_EDITOR_MOVE_CARET_UP = "EditorUp";
  String ACTION_EDITOR_MOVE_CARET_LEFT = "EditorLeft";
  String ACTION_EDITOR_MOVE_CARET_DOWN = "EditorDown";
  String ACTION_EDITOR_MOVE_CARET_RIGHT = "EditorRight";
  String ACTION_EDITOR_MOVE_CARET_PAGE_UP = "EditorPageUp";
  String ACTION_EDITOR_MOVE_CARET_PAGE_DOWN = "EditorPageDown";
  String ACTION_EDITOR_TAB = "EditorTab";
  String ACTION_EDITOR_ESCAPE = "EditorEscape";
  String ACTION_EDITOR_JOIN_LINES = "EditorJoinLines";
  String ACTION_EDITOR_COMPLETE_STATEMENT = "EditorCompleteStatement";

  String ACTION_COMMENT_LINE = "CommentByLineComment";
  String ACTION_REMOVE_ENCLOSING_TAG = "RemoveEnclosingTag";
  String ACTION_COMMENT_BLOCK = "CommentByBlockComment";

  String ACTION_COPY = "$Copy";
  String ACTION_DELETE = "$Delete";
  String ACTION_PASTE = "$Paste";
  String ACTION_CONTEXT_HELP = "ContextHelp";
  String ACTION_EDIT_SOURCE = "EditSource";
  String ACTION_VIEW_SOURCE = "ViewSource";
  String ACTION_SHOW_INTENTION_ACTIONS = "ShowIntentionActions";
  String ACTION_CODE_COMPLETION = "CodeCompletion";

  String GROUP_EXTERNAL_TOOLS = "ExternalToolsGroup";

  String GROUP_MAIN_MENU = "MainMenu";
  String GROUP_MAIN_TOOLBAR = "MainToolBar";
  String GROUP_EDITOR_POPUP = "EditorPopupMenu";
  String GROUP_EDITOR_TAB_POPUP = "EditorTabPopupMenu";

  String ACTION_CVS_ADD = "Cvs.Add";
  String ACTION_CVS_COMMIT = "Cvs.Commit";
  String ACTION_CVS_EDITORS = "Cvs.Editors";
  String ACTION_CVS_LOG = "Cvs.Log";
  String ACTION_CVS_UPDATE = "Cvs.Update";
  String ACTION_CVS_STATUS = "Cvs.Status";
  String ACTION_CVS_DIFF = "Cvs.Diff";
  String ACTION_CVS_EDIT = "Cvs.Edit";
  String ACTION_CVS_UNEDIT = "Cvs.Unedit";
  String ACTION_CVS_CHECKOUT = "Cvs.Checkout";

  String ACTION_CLOSE_ACTIVE_TAB = "CloseActiveTab";
  String ACTION_PIN_ACTIVE_TAB = "PinActiveTab";
  String ACTION_SYNCHRONIZE = "Synchronize";
  String ACTION_NEXT_OCCURENCE = "NextOccurence";
  String ACTION_PREVIOUS_OCCURENCE = "PreviousOccurence";
  String ACTION_NEXT_TAB = "NextTab";
  String ACTION_PREVIOUS_TAB = "PreviousTab";
  String ACTION_FIND = "Find";
  String ACTION_FIND_NEXT = "FindNext";
  String ACTION_FIND_PREVIOUS = "FindPrevious";
  String ACTION_COMPILE = "Compile";
  String ACTION_COMPILE_PROJECT = "CompileProject";
  String ACTION_MAKE_MODULE = "MakeModule";
  String ACTION_GENERATE_ANT_BUILD = "GenerateAntBuild";
  String ACTION_INSPECT_CODE = "InspectCode";

  String ACTION_FIND_USAGES = "FindUsages";
  String ACTION_FIND_IN_PATH = "FindInPath";

  String ACTION_TYPE_HIERARCHY = "TypeHierarchy";
  String ACTION_METHOD_HIERARCHY = "MethodHierarchy";
  String ACTION_CALL_HIERARCHY = "CallHierarchy";

  String ACTION_EXTERNAL_JAVADOC = "ExternalJavaDoc";

  String ACTION_CLOSE_EDITOR = "CloseEditor";
  String ACTION_CLOSE_ALL_EDITORS = "CloseAllEditors";
  String ACTION_CLOSE_ALL_UNMODIFIED_EDITORS = "CloseAllUnmodifiedEditors";
  String ACTION_CLOSE_ALL_EDITORS_BUT_THIS = "CloseAllEditorsButActive";

  String ACTION_PREVIOUS_DIFF = "PreviousDiff";
  String ACTION_NEXT_DIFF = "NextDiff";

  String ACTION_EXPAND_ALL = "ExpandAll";
  String ACTION_COLLAPSE_ALL = "CollapseAll";
  String ACTION_EXPORT_TO_TEXT_FILE = "ExportToTextFile";

  String ACTION_NEW_HORIZONTAL_TAB_GROUP = "NewHorizontalTabGroup";
  String ACTION_NEW_VERTICAL_TAB_GROUP = "NewVerticalTabGroup";
  String ACTION_MOVE_EDITOR_TO_OPPOSITE_TAB_GROUP = "MoveEditorToOppositeTabGroup";
  String ACTION_CHANGE_SPLIT_ORIENTATION = "ChangeSplitOrientation";
  String ACTION_PIN_ACTIVE_EDITOR = "PinActiveEditor";

  String GROUP_VERSION_CONTROLS = "VersionControlsGroup";

  String GROUP_PROJECT_VIEW_POPUP = "ProjectViewPopupMenu";
  String GROUP_COMMANDER_POPUP = "CommanderPopupMenu";
  String GROUP_TESTTREE_POPUP = "TestTreePopupMenu";
  String GROUP_TESTSTATISTICS_POPUP = "TestStatisticsTablePopupMenu";

  String GROUP_FAVORITES_VIEW_POPUP = "FavoritesViewPopupMenu";
  String ADD_TO_FAVORITES = "AddToFavorites";
  String REMOVE_FROM_FAVORITES = "RemoveFromFavorites";
  String ADD_NEW_FAVORITES_LIST = "AddNewFavoritesList";
  String RENAME_FAVORITES_LIST = "RenameFavoritesList";
  String REMOVE_FAVORITES_LIST = "RemoveFavoritesList";
  String REMOVE_ALL_FAVORITES_LISTS_BUT_THIS = "RemoveAllFavoritesListsButThis";

  String GROUP_SCOPE_VIEW_POPUP = "ScopeViewPopupMenu";

  String GROUP_J2EE_VIEW_POPUP = "J2EEViewPopupMenu";
  String GROUP_EJB_TRANSACTION_ATTRIBUTES_VIEW_POPUP = "EjbTransactionAttributesViewPopupMenu";
  String GROUP_EJB_ENVIRONMENT_ENTRIES_VIEW_POPUP = "EjbEnvironmentEntriesViewPopupMenu";
  String GROUP_EJB_REFERENCES_VIEW_POPUP = "EjbReferencesViewPopupMenu";
  String GROUP_SECURITY_ROLES_VIEW_POPUP = "SecurityRolesViewPopupMenu";
  String GROUP_PARAMETERS_VIEW_POPUP = "ParametersViewPopupMenu";
  String GROUP_SERVLET_MAPPING_VIEW_POPUP = "ServletMappingViewPopupMenu";
  String GROUP_EJB_RESOURCE_REFERENCES_VIEW_POPUP = "EjbResourceReferencesViewPopupMenu";
  String GROUP_EJB_RESOURCE_ENVIRONMENT_REFERENCES_VIEW_POPUP = "EjbResourceEnvironmentReferencesViewPopupMenu";
  String GROUP_ADD_SUPPORT = "AddSupportGroup";
  
  String GROUP_STRUCTURE_VIEW_POPUP = "StructureViewPopupMenu";
  String GROUP_TYPE_HIERARCHY_POPUP = "TypeHierarchyPopupMenu";
  String GROUP_METHOD_HIERARCHY_POPUP = "MethodHierarchyPopupMenu";
  String GROUP_CALL_HIERARCHY_POPUP = "CallHierarchyPopupMenu";

  String GROUP_BOOKMARKS = "Bookmarks";

  String GROUP_COMPILER_ERROR_VIEW_POPUP = "CompilerErrorViewPopupMenu";

  String GROUP_OTHER_MENU = "OtherMenu";
  String GROUP_EDITOR = "EditorActions";
  String GROUP_DEBUGGER = "DebuggerActions";

  String ACTION_REFRESH = "Refresh";

  String GROUP_GENERATE = "GenerateGroup";
  String GROUP_NEW = "NewGroup";
  String GROUP_CHANGE_SCHEME = "ChangeScheme";

  String GROUP_FILE = "FileMenu";
  String ACTION_NEW_PROJECT = "NewProject";
  String ACTION_SHOW_SETTINGS = "ShowSettings";

  String GROUP_RUN = "RunMenu";
  String GROUP_RUNNER_ACTIONS = "RunnerActions";
  String ACTION_DEFAULT_RUNNER = "Run";
  String ACTION_DEFAULT_DEBUGGER = "Debug";
  String ACTION_EDIT_RUN_CONFIGURATIONS = "editRunConfigurations";
  String ACTION_RERUN = "Rerun";

  String ACTION_VCS_EDIT_SOURCE = "Vcs.EditSourceAction";
  String ACTION_INCLUDE = "Vcs.IncludeAction";
  String ACTION_EXCLUDE = "Vcs.ExcludeAction";
  String ACTION_STOP_PROGRAM = "Stop";
  String ACTION_NEW_ELEMENT = "NewElement";

  String ACTION_QUICK_JAVADOC = "QuickJavaDoc";
  String ACTION_CHECKIN_PROJECT = "CheckinProject";

  String GROUP_USAGE_VIEW_POPUP = "UsageView.Popup";

  /*GUI designer actions*/
  String GROUP_GUI_DESIGNER_EDITOR_POPUP = "GuiDesigner.EditorPopupMenu";
  String GROUP_GUI_DESIGNER_COMPONENT_TREE_POPUP = "GuiDesigner.ComponentTreePopupMenu";
  String GROUP_GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP = "GuiDesigner.PropertyInspectorPopupMenu";
  String ACTION_LAY_OUT_HORIZONTALLY = "GuiDesigner.LayOutHorizontally";
  String ACTION_LAY_OUT_VERTICALLY = "GuiDesigner.LayOutVertically";
  String ACTION_LAY_OUT_IN_GRID = "GuiDesigner.LayOutInGrid";
  String ACTION_BREAK_LAYOUT = "GuiDesigner.BreakLayout";
  String ACTION_PREVIEW_FORM = "GuiDesigner.PreviewForm";
  String ACTION_DATA_BINDING_WIZARD = "GuiDesigner.DataBindingWizard";

  String ACTION_GOTO_BACK    = "Back";
  String ACTION_GOTO_FORWARD = "Forward";

  String ACTION_COMMANDER_SYNC_VIEWS = "CommanderSyncViews";
  String ACTION_COMMANDER_SWAP_PANELS = "CommanderSwapPanels";

  String MODULE_SETTINGS = "ModuleSettings";

  String GROUP_WELCOME_SCREEN_QUICKSTART = "WelcomeScreen.QuickStart";
  String GROUP_WELCOME_SCREEN_DOC = "WelcomeScreen.Documentation";
  String ACTION_KEYMAP_REFERENCE="Help.KeymapReference";
  String ACTION_MOVE = "Move";
  String ACTION_RENAME = "RenameElement";

  String ACTION_ANALYZE_DEPENDENCIES = "ShowPackageDeps";
}
