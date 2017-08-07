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

import org.jetbrains.annotations.NonNls;

/**
 * Identifiers for standard actions and action groups supported by IDEA.
 */
public interface IdeActions {
  @NonNls String ACTION_EDITOR_CUT = "EditorCut";
  @NonNls String ACTION_EDITOR_COPY = "EditorCopy";
  @NonNls String ACTION_EDITOR_PASTE = "EditorPaste";
  @NonNls String ACTION_EDITOR_PASTE_SIMPLE = "EditorPasteSimple";
  @NonNls String ACTION_EDITOR_DELETE = "EditorDelete";
  @NonNls String ACTION_EDITOR_DELETE_TO_WORD_START = "EditorDeleteToWordStart";
  @NonNls String ACTION_EDITOR_DELETE_TO_WORD_END = "EditorDeleteToWordEnd";
  @NonNls String ACTION_EDITOR_DELETE_LINE = "EditorDeleteLine";
  @NonNls String ACTION_EDITOR_ENTER = "EditorEnter";
  @NonNls String ACTION_EDITOR_START_NEW_LINE = "EditorStartNewLine";
  @NonNls String ACTION_EDITOR_SPLIT = "EditorSplitLine";
  @NonNls String ACTION_EDITOR_TEXT_START = "EditorTextStart";
  @NonNls String ACTION_EDITOR_TEXT_END = "EditorTextEnd";
  @NonNls String ACTION_EDITOR_FORWARD_PARAGRAPH = "EditorForwardParagraph";
  @NonNls String ACTION_EDITOR_BACKWARD_PARAGRAPH = "EditorBackwardParagraph";
  @NonNls String ACTION_EDITOR_TEXT_START_WITH_SELECTION = "EditorTextStartWithSelection";
  @NonNls String ACTION_EDITOR_TEXT_END_WITH_SELECTION = "EditorTextEndWithSelection";
  @NonNls String ACTION_EDITOR_MOVE_LINE_START = "EditorLineStart";
  @NonNls String ACTION_EDITOR_MOVE_LINE_END = "EditorLineEnd";
  @NonNls String ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION = "EditorLineStartWithSelection";
  @NonNls String ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION = "EditorLineEndWithSelection";
  @NonNls String ACTION_EDITOR_SELECT_WORD_AT_CARET = "EditorSelectWord";
  @NonNls String ACTION_EDITOR_UNSELECT_WORD_AT_CARET = "EditorUnSelectWord";
  @NonNls String ACTION_EDITOR_BACKSPACE = "EditorBackSpace";
  @NonNls String ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION = "EditorLeftWithSelection";
  @NonNls String ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION = "EditorRightWithSelection";
  @NonNls String ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION = "EditorUpWithSelection";
  @NonNls String ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION = "EditorDownWithSelection";
  @NonNls String ACTION_EDITOR_SWAP_SELECTION_BOUNDARIES = "EditorSwapSelectionBoundaries";
  @NonNls String ACTION_EDITOR_MOVE_CARET_UP = "EditorUp";
  @NonNls String ACTION_EDITOR_MOVE_CARET_LEFT = "EditorLeft";
  @NonNls String ACTION_EDITOR_MOVE_CARET_DOWN = "EditorDown";
  @NonNls String ACTION_EDITOR_MOVE_CARET_RIGHT = "EditorRight";
  @NonNls String ACTION_EDITOR_MOVE_CARET_PAGE_UP = "EditorPageUp";
  @NonNls String ACTION_EDITOR_MOVE_CARET_PAGE_DOWN = "EditorPageDown";
  @NonNls String ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION = "EditorPageUpWithSelection";
  @NonNls String ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION = "EditorPageDownWithSelection";
  @NonNls String ACTION_EDITOR_NEXT_WORD = "EditorNextWord";
  @NonNls String ACTION_EDITOR_PREVIOUS_WORD = "EditorPreviousWord";
  @NonNls String ACTION_EDITOR_NEXT_WORD_WITH_SELECTION = "EditorNextWordWithSelection";
  @NonNls String ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION = "EditorPreviousWordWithSelection";
  @NonNls String ACTION_EDITOR_TAB = "EditorTab";
  @NonNls String ACTION_EDITOR_EMACS_TAB = "EmacsStyleIndent";
  @NonNls String ACTION_EDITOR_ESCAPE = "EditorEscape";
  @NonNls String ACTION_EDITOR_JOIN_LINES = "EditorJoinLines";
  @NonNls String ACTION_EDITOR_COMPLETE_STATEMENT = "EditorCompleteStatement";
  @NonNls String ACTION_EDITOR_USE_SOFT_WRAPS = "EditorToggleUseSoftWraps";
  @NonNls String ACTION_EDITOR_ADD_OR_REMOVE_CARET= "EditorAddOrRemoveCaret";
  @NonNls String ACTION_EDITOR_CREATE_RECTANGULAR_SELECTION= "EditorCreateRectangularSelection";
  @NonNls String ACTION_EDITOR_ADD_RECTANGULAR_SELECTION_ON_MOUSE_DRAG= "EditorAddRectangularSelectionOnMouseDrag";
  @NonNls String ACTION_EDITOR_CLONE_CARET_BELOW= "EditorCloneCaretBelow";
  @NonNls String ACTION_EDITOR_CLONE_CARET_ABOVE= "EditorCloneCaretAbove";
  @NonNls String ACTION_EDITOR_TOGGLE_STICKY_SELECTION= "EditorToggleStickySelection";
  @NonNls String ACTION_EDITOR_TOGGLE_OVERWRITE_MODE= "EditorToggleInsertState";
  @NonNls String ACTION_EDITOR_TOGGLE_CASE= "EditorToggleCase";

  @NonNls String ACTION_EDITOR_NEXT_PARAMETER = "NextParameter";
  @NonNls String ACTION_EDITOR_PREV_PARAMETER = "PrevParameter";
  @NonNls String ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE = "NextTemplateVariable";
  @NonNls String ACTION_EDITOR_PREVIOUS_TEMPLATE_VARIABLE = "PreviousTemplateVariable";

  @NonNls String ACTION_EDITOR_REFORMAT = "ReformatCode";
  @NonNls String ACTION_EDITOR_AUTO_INDENT_LINES = "AutoIndentLines";

  @NonNls String ACTION_COMMENT_LINE = "CommentByLineComment";
  @NonNls String ACTION_COMMENT_BLOCK = "CommentByBlockComment";

  @NonNls String ACTION_COPY = "$Copy";
  @NonNls String ACTION_CUT = "$Cut";
  @NonNls String ACTION_DELETE = "$Delete";
  @NonNls String ACTION_PASTE = "$Paste";
  @NonNls String ACTION_SELECT_ALL = "$SelectAll";
  @NonNls String ACTION_CONTEXT_HELP = "ContextHelp";
  @NonNls String ACTION_EDIT_SOURCE = "EditSource";
  @NonNls String ACTION_VIEW_SOURCE = "ViewSource";
  @NonNls String ACTION_SHOW_INTENTION_ACTIONS = "ShowIntentionActions";
  @NonNls String ACTION_CODE_COMPLETION = "CodeCompletion";
  @NonNls String ACTION_SMART_TYPE_COMPLETION = "SmartTypeCompletion";
  @Deprecated @NonNls String ACTION_CLASS_NAME_COMPLETION = "ClassNameCompletion";
  @NonNls String ACTION_HIPPIE_COMPLETION = "HippieCompletion";
  @NonNls String ACTION_HIPPIE_BACKWARD_COMPLETION = "HippieBackwardCompletion";
  @NonNls String ACTION_CHOOSE_LOOKUP_ITEM = "EditorChooseLookupItem";
  @NonNls String ACTION_CHOOSE_LOOKUP_ITEM_REPLACE = "EditorChooseLookupItemReplace";
  @NonNls String ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT = "EditorChooseLookupItemCompleteStatement";
  @NonNls String ACTION_CHOOSE_LOOKUP_ITEM_DOT = "EditorChooseLookupItemDot";
  @NonNls String ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB = "ExpandLiveTemplateByTab";
  @NonNls String ACTION_EXPAND_LIVE_TEMPLATE_CUSTOM = "ExpandLiveTemplateCustom";
  @NonNls String ACTION_UPDATE_TAG_WITH_EMMET = "EmmetUpdateTag";

  @NonNls String ACTION_LOOKUP_UP = "EditorLookupUp";
  @NonNls String ACTION_LOOKUP_DOWN = "EditorLookupDown";

  @NonNls String GROUP_EXTERNAL_TOOLS = "ExternalToolsGroup";

  @NonNls String GROUP_MAIN_MENU = "MainMenu";
  @NonNls String GROUP_MAIN_TOOLBAR = "MainToolBar";
  @NonNls String GROUP_EDITOR_POPUP = "EditorPopupMenu";
  @NonNls String GROUP_BASIC_EDITOR_POPUP = "BasicEditorPopupMenu";
  @NonNls String GROUP_CONSOLE_EDITOR_POPUP = "ConsoleEditorPopupMenu";
  @NonNls String GROUP_CUT_COPY_PASTE = "CutCopyPasteGroup";
  @NonNls String GROUP_EDITOR_TAB_POPUP = "EditorTabPopupMenu";

  @NonNls String ACTION_CVS_ADD = "Cvs.Add";
  @NonNls String ACTION_CVS_COMMIT = "Cvs.Commit";
  @NonNls String ACTION_CVS_EDITORS = "Cvs.Editors";
  @NonNls String ACTION_CVS_LOG = "Cvs.Log";
  @NonNls String ACTION_CVS_UPDATE = "Cvs.Update";
  @NonNls String ACTION_CVS_STATUS = "Cvs.Status";
  @NonNls String ACTION_CVS_DIFF = "Cvs.Diff";
  @NonNls String ACTION_CVS_EDIT = "Cvs.Edit";
  @NonNls String ACTION_CVS_UNEDIT = "Cvs.Unedit";
  @NonNls String ACTION_CVS_CHECKOUT = "Cvs.Checkout";

  @NonNls String ACTION_CLOSE_ACTIVE_TAB = "CloseActiveTab";
  @NonNls String ACTION_PIN_ACTIVE_TAB = "PinActiveTab";
  @NonNls String ACTION_SYNCHRONIZE = "Synchronize";
  @NonNls String ACTION_NEXT_OCCURENCE = "NextOccurence";
  @NonNls String ACTION_PREVIOUS_OCCURENCE = "PreviousOccurence";
  @NonNls String ACTION_NEXT_TAB = "NextTab";
  @NonNls String ACTION_PREVIOUS_TAB = "PreviousTab";
  @NonNls String ACTION_NEXT_EDITOR_TAB = "NextEditorTab";
  @NonNls String ACTION_PREVIOUS_EDITOR_TAB = "PreviousEditorTab";
  @NonNls String ACTION_FIND = "Find";
  @NonNls String ACTION_FIND_NEXT = "FindNext";
  @NonNls String ACTION_FIND_PREVIOUS = "FindPrevious";
  @NonNls String ACTION_SELECT_NEXT_OCCURENCE = "SelectNextOccurrence";
  @NonNls String ACTION_SELECT_ALL_OCCURRENCES = "SelectAllOccurrences";
  @NonNls String ACTION_UNSELECT_PREVIOUS_OCCURENCE = "UnselectPreviousOccurrence";
  @NonNls String ACTION_REPLACE = "Replace";
  @NonNls String ACTION_COMPILE = "Compile";
  @NonNls String ACTION_COMPILE_PROJECT = "CompileProject";
  @NonNls String ACTION_MAKE_MODULE = "MakeModule";
  @NonNls String ACTION_GENERATE_ANT_BUILD = "GenerateAntBuild";
  @NonNls String ACTION_INSPECT_CODE = "InspectCode";

  @NonNls String ACTION_FIND_USAGES = "FindUsages";
  @NonNls String ACTION_FIND_IN_PATH = "FindInPath";

  @NonNls String ACTION_TYPE_HIERARCHY = "TypeHierarchy";
  @NonNls String ACTION_METHOD_HIERARCHY = "MethodHierarchy";
  @NonNls String ACTION_CALL_HIERARCHY = "CallHierarchy";

  @NonNls String ACTION_EXTERNAL_JAVADOC = "ExternalJavaDoc";

  @NonNls String ACTION_CLOSE = "CloseContent";
  @NonNls String ACTION_CLOSE_EDITOR = "CloseEditor";
  @NonNls String ACTION_CLOSE_ALL_EDITORS = "CloseAllEditors";
  @NonNls String ACTION_CLOSE_ALL_UNMODIFIED_EDITORS = "CloseAllUnmodifiedEditors";
  @NonNls String ACTION_CLOSE_ALL_EDITORS_BUT_THIS = "CloseAllEditorsButActive";

  @NonNls String ACTION_SHOW_DIFF_COMMON = "Diff.ShowDiff";
  @NonNls String ACTION_PREVIOUS_DIFF = "PreviousDiff";
  @NonNls String ACTION_NEXT_DIFF = "NextDiff";
  @NonNls String GROUP_DIFF_EDITOR_POPUP = "Diff.EditorPopupMenu";
  @NonNls String DIFF_VIEWER_POPUP = "Diff.ViewerPopupMenu";
  @NonNls String DIFF_VIEWER_TOOLBAR = "Diff.ViewerToolbar";
  @NonNls String GROUP_DIFF_EDITOR_GUTTER_POPUP = "Diff.EditorGutterPopupMenu";

  @NonNls String ACTION_EXPAND_ALL = "ExpandAll";
  @NonNls String ACTION_COLLAPSE_ALL = "CollapseAll";
  @NonNls String ACTION_EXPORT_TO_TEXT_FILE = "ExportToTextFile";

  @NonNls String ACTION_COLLAPSE_REGION = "CollapseRegion";
  @NonNls String ACTION_EXPAND_ALL_REGIONS = "ExpandAllRegions";
  @NonNls String ACTION_COLLAPSE_ALL_REGIONS = "CollapseAllRegions";
  @NonNls String ACTION_EXPAND_REGION_RECURSIVELY = "ExpandRegionRecursively";
  @NonNls String ACTION_COLLAPSE_REGION_RECURSIVELY = "CollapseRegionRecursively";
  @NonNls String ACTION_EXPAND_TO_LEVEL_1 = "ExpandToLevel1";
  @NonNls String ACTION_EXPAND_ALL_TO_LEVEL_1 = "ExpandAllToLevel1";

  @NonNls String ACTION_NEW_HORIZONTAL_TAB_GROUP = "NewHorizontalTabGroup";
  @NonNls String ACTION_NEW_VERTICAL_TAB_GROUP = "NewVerticalTabGroup";
  @NonNls String ACTION_MOVE_EDITOR_TO_OPPOSITE_TAB_GROUP = "MoveEditorToOppositeTabGroup";
  @NonNls String ACTION_CHANGE_SPLIT_ORIENTATION = "ChangeSplitOrientation";
  @NonNls String ACTION_PIN_ACTIVE_EDITOR = "PinActiveEditor";

  @NonNls String GROUP_VERSION_CONTROLS = "VersionControlsGroup";

  @NonNls String GROUP_PROJECT_VIEW_POPUP = "ProjectViewPopupMenu";
  @NonNls String GROUP_NAVBAR_POPUP = "NavbarPopupMenu";
  @NonNls String GROUP_COMMANDER_POPUP = "CommanderPopupMenu";
  @NonNls String GROUP_TESTTREE_POPUP = "TestTreePopupMenu";
  @NonNls String GROUP_TESTSTATISTICS_POPUP = "TestStatisticsTablePopupMenu";

  @NonNls String GROUP_FAVORITES_VIEW_POPUP = "FavoritesViewPopupMenu";
  @NonNls String ADD_TO_FAVORITES = "AddToFavorites";
  @NonNls String ADD_NEW_FAVORITES_LIST = "AddNewFavoritesList";
  @NonNls String REMOVE_FAVORITES_LIST = "RemoveFavoritesList";
  @NonNls String REMOVE_ALL_FAVORITES_LISTS_BUT_THIS = "RemoveAllFavoritesListsButThis";

  @NonNls String GROUP_SCOPE_VIEW_POPUP = "ScopeViewPopupMenu";

  @NonNls String GROUP_J2EE_VIEW_POPUP = "J2EEViewPopupMenu";
  @NonNls String GROUP_EJB_TRANSACTION_ATTRIBUTES_VIEW_POPUP = "EjbTransactionAttributesViewPopupMenu";
  @NonNls String GROUP_EJB_ENVIRONMENT_ENTRIES_VIEW_POPUP = "EjbEnvironmentEntriesViewPopupMenu";
  @NonNls String GROUP_EJB_REFERENCES_VIEW_POPUP = "EjbReferencesViewPopupMenu";
  @NonNls String GROUP_SECURITY_ROLES_VIEW_POPUP = "SecurityRolesViewPopupMenu";
  @NonNls String GROUP_PARAMETERS_VIEW_POPUP = "ParametersViewPopupMenu";
  @NonNls String GROUP_SERVLET_MAPPING_VIEW_POPUP = "ServletMappingViewPopupMenu";
  @NonNls String GROUP_EJB_RESOURCE_REFERENCES_VIEW_POPUP = "EjbResourceReferencesViewPopupMenu";
  @NonNls String GROUP_EJB_RESOURCE_ENVIRONMENT_REFERENCES_VIEW_POPUP = "EjbResourceEnvironmentReferencesViewPopupMenu";
  @NonNls String GROUP_ADD_SUPPORT = "AddSupportGroup";
  
  @NonNls String GROUP_STRUCTURE_VIEW_POPUP = "StructureViewPopupMenu";
  @NonNls String GROUP_TYPE_HIERARCHY_POPUP = "TypeHierarchyPopupMenu";
  @NonNls String GROUP_METHOD_HIERARCHY_POPUP = "MethodHierarchyPopupMenu";
  @NonNls String GROUP_CALL_HIERARCHY_POPUP = "CallHierarchyPopupMenu";

  @NonNls String GROUP_COMPILER_ERROR_VIEW_POPUP = "CompilerErrorViewPopupMenu";

  @NonNls String GROUP_OTHER_MENU = "OtherMenu";
  @NonNls String GROUP_EDITOR = "EditorActions";

  @Deprecated @NonNls String GROUP_DEBUGGER = "DebuggerActions";
  
  @NonNls String ACTION_TOGGLE_LINE_BREAKPOINT = "ToggleLineBreakpoint";

  @NonNls String ACTION_REFRESH = "Refresh";

  @NonNls String GROUP_GENERATE = "GenerateGroup";
  @NonNls String GROUP_NEW = "NewGroup";
  @NonNls String GROUP_WEIGHING_NEW = "WeighingNewGroup";
  @NonNls String GROUP_CHANGE_SCHEME = "ChangeScheme";

  @NonNls String GROUP_FILE = "FileMenu";
  @NonNls String ACTION_NEW_PROJECT = "NewProject";
  @NonNls String ACTION_SHOW_SETTINGS = "ShowSettings";

  @NonNls String GROUP_RUN = "RunMenu";
  @NonNls String GROUP_RUNNER_ACTIONS = "RunnerActions";
  @NonNls String ACTION_DEFAULT_RUNNER = "Run";
  @NonNls String ACTION_DEFAULT_DEBUGGER = "Debug";
  @NonNls String ACTION_EDIT_RUN_CONFIGURATIONS = "editRunConfigurations";
  @NonNls String ACTION_RERUN = "Rerun";

  @NonNls String ACTION_VCS_EDIT_SOURCE = "Vcs.EditSourceAction";
  @NonNls String ACTION_INCLUDE = "Vcs.IncludeAction";
  @NonNls String ACTION_EXCLUDE = "Vcs.ExcludeAction";
  @NonNls String ACTION_STOP_PROGRAM = "Stop";
  @NonNls String ACTION_NEW_ELEMENT = "NewElement";

  @NonNls String ACTION_QUICK_JAVADOC = "QuickJavaDoc";
  @NonNls String ACTION_QUICK_IMPLEMENTATIONS = "QuickImplementations";
  @NonNls String ACTION_CHECKIN_PROJECT = "CheckinProject";
  @NonNls String ACTION_FILE_STRUCTURE_POPUP = "FileStructurePopup";

  @NonNls String GROUP_USAGE_VIEW_POPUP = "UsageView.Popup";

  /*GUI designer actions*/
  @NonNls String GROUP_GUI_DESIGNER_EDITOR_POPUP = "GuiDesigner.EditorPopupMenu";
  @NonNls String GROUP_GUI_DESIGNER_COMPONENT_TREE_POPUP = "GuiDesigner.ComponentTreePopupMenu";
  @NonNls String GROUP_GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP = "GuiDesigner.PropertyInspectorPopupMenu";

  @NonNls String ACTION_GOTO_LAST_CHANGE    = "JumpToLastChange";
  @NonNls String ACTION_GOTO_NEXT_CHANGE    = "JumpToNextChange";

  @NonNls String ACTION_GOTO_BACK    = "Back";
  @NonNls String ACTION_GOTO_FORWARD = "Forward";
  @NonNls String ACTION_GOTO_DECLARATION = "GotoDeclaration";
  @NonNls String ACTION_GOTO_TYPE_DECLARATION = "GotoTypeDeclaration";
  @NonNls String ACTION_GOTO_IMPLEMENTATION = "GotoImplementation";
  @NonNls String ACTION_GOTO_SUPER = "GotoSuperMethod";

  @NonNls String MODULE_SETTINGS = "ModuleSettings";

  @NonNls String GROUP_WELCOME_SCREEN_QUICKSTART = "WelcomeScreen.QuickStart";
  @NonNls String GROUP_WELCOME_SCREEN_DOC = "WelcomeScreen.Documentation";
  @NonNls String GROUP_WELCOME_SCREEN_CONFIGURE = "WelcomeScreen.Configure";
  @NonNls String ACTION_KEYMAP_REFERENCE="Help.KeymapReference";
  @NonNls String ACTION_MOVE = "Move";
  @NonNls String ACTION_RENAME = "RenameElement";

  @NonNls String ACTION_ANALYZE_DEPENDENCIES = "ShowPackageDeps";
  @NonNls String ACTION_ANALYZE_BACK_DEPENDENCIES = "ShowBackwardPackageDeps";
  @NonNls String ACTION_ANALYZE_CYCLIC_DEPENDENCIES = "ShowPackageCycles";
  @NonNls String ACTION_ANALYZE_MODULE_DEPENDENCIES = "ShowModulesDependencies";
  @NonNls String GROUP_MOVE_MODULE_TO_GROUP = "MoveModuleToGroup";
  @NonNls String ACTION_CLEAR_TEXT = "TextComponent.ClearAction";
  @NonNls String ACTION_HIGHLIGHT_USAGES_IN_FILE = "HighlightUsagesInFile";
  @NonNls String ACTION_COPY_REFERENCE = "CopyReference";

  @NonNls String GROUP_ANALYZE = "AnalyzeMenu";
  @NonNls String ACTION_SHOW_ERROR_DESCRIPTION = "ShowErrorDescription";

  @NonNls String ACTION_EDITOR_DUPLICATE = "EditorDuplicate";
  @NonNls String ACTION_EDITOR_DUPLICATE_LINES = "EditorDuplicateLines";

  @NonNls String GROUP_EDITOR_GUTTER = "EditorGutterPopupMenu";

  String ACTION_MOVE_STATEMENT_UP_ACTION = "MoveStatementUp";
  String ACTION_MOVE_STATEMENT_DOWN_ACTION = "MoveStatementDown";
  String MOVE_ELEMENT_LEFT = "MoveElementLeft";
  String MOVE_ELEMENT_RIGHT = "MoveElementRight";
  
  String ACTION_MOVE_LINE_UP_ACTION = "MoveLineUp";
  String ACTION_MOVE_LINE_DOWN_ACTION = "MoveLineDown";

  String ACTION_COMPARE_CLIPBOARD_WITH_SELECTION = "CompareClipboardWithSelection";

  String ACTION_UNDO = "$Undo";
  String ACTION_REDO = "$Redo";
  String GROUP_REFACTOR = "RefactoringMenu";
  String SELECTED_CHANGES_ROLLBACK = "Vcs.RollbackChangedLines";
  String CHANGES_VIEW_ROLLBACK = "ChangesView.Revert";

  String CONSOLE_CLEAR_ALL = "ConsoleView.ClearAll";
  String MOVE_TO_ANOTHER_CHANGE_LIST = "ChangesView.Move";

  String ACTION_RECENT_FILES = "RecentFiles";
  String ACTION_SEARCH_EVERYWHERE = "SearchEverywhere";
  
  String ACTION_MARK_ALL_NOTIFICATIONS_AS_READ = "MarkNotificationsAsRead";
  String ACTION_SWITCHER = "Switcher";

  @NonNls String INSPECTION_TOOL_WINDOW_TREE_POPUP = "InspectionToolWindow.TreePopup";
}
