/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
  @NonNls String ACTION_EDITOR_DELETE = "EditorDelete";
  @NonNls String ACTION_EDITOR_DELETE_TO_WORD_START = "EditorDeleteToWordStart";
  @NonNls String ACTION_EDITOR_DELETE_TO_WORD_END = "EditorDeleteToWordEnd";
  @NonNls String ACTION_EDITOR_ENTER = "EditorEnter";
  @NonNls String ACTION_EDITOR_START_NEW_LINE = "EditorStartNewLine";
  @NonNls String ACTION_EDITOR_SPLIT = "EditorSplitLine";
  @NonNls String ACTION_EDITOR_MOVE_LINE_START = "EditorLineStart";
  @NonNls String ACTION_EDITOR_MOVE_LINE_END = "EditorLineEnd";
  @NonNls String ACTION_EDITOR_SELECT_WORD_AT_CARET = "EditorSelectWord";
  @NonNls String ACTION_EDITOR_UNSELECT_WORD_AT_CARET = "EditorUnSelectWord";
  @NonNls String ACTION_EDITOR_BACKSPACE = "EditorBackSpace";
  @NonNls String ACTION_EDITOR_MOVE_CARET_UP = "EditorUp";
  @NonNls String ACTION_EDITOR_MOVE_CARET_LEFT = "EditorLeft";
  @NonNls String ACTION_EDITOR_MOVE_CARET_DOWN = "EditorDown";
  @NonNls String ACTION_EDITOR_MOVE_CARET_RIGHT = "EditorRight";
  @NonNls String ACTION_EDITOR_MOVE_CARET_PAGE_UP = "EditorPageUp";
  @NonNls String ACTION_EDITOR_MOVE_CARET_PAGE_DOWN = "EditorPageDown";
  @NonNls String ACTION_EDITOR_TAB = "EditorTab";
  @NonNls String ACTION_EDITOR_ESCAPE = "EditorEscape";
  @NonNls String ACTION_EDITOR_JOIN_LINES = "EditorJoinLines";
  @NonNls String ACTION_EDITOR_COMPLETE_STATEMENT = "EditorCompleteStatement";
  @NonNls String ACTION_EDITOR_USE_SOFT_WRAPS = "EditorToggleUseSoftWraps";

  @NonNls String ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE = "NextTemplateVariable";
  @NonNls String ACTION_EDITOR_PREVIOUS_TEMPLATE_VARIABLE = "PreviousTemplateVariable";



  @NonNls String ACTION_COMMENT_LINE = "CommentByLineComment";
  @NonNls String ACTION_COMMENT_BLOCK = "CommentByBlockComment";

  @NonNls String ACTION_COPY = "$Copy";
  @NonNls String ACTION_DELETE = "$Delete";
  @NonNls String ACTION_PASTE = "$Paste";
  @NonNls String ACTION_CONTEXT_HELP = "ContextHelp";
  @NonNls String ACTION_EDIT_SOURCE = "EditSource";
  @NonNls String ACTION_VIEW_SOURCE = "ViewSource";
  @NonNls String ACTION_SHOW_INTENTION_ACTIONS = "ShowIntentionActions";
  @NonNls String ACTION_CODE_COMPLETION = "CodeCompletion";
  @NonNls String ACTION_SMART_TYPE_COMPLETION = "SmartTypeCompletion";
  @NonNls String ACTION_CLASS_NAME_COMPLETION = "ClassNameCompletion";
  @NonNls String ACTION_CHOOSE_LOOKUP_ITEM = "EditorChooseLookupItem";
  @NonNls String ACTION_CHOOSE_LOOKUP_ITEM_REPLACE = "EditorChooseLookupItemReplace";
  @NonNls String ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT = "EditorChooseLookupItemCompleteStatement";

  @NonNls String GROUP_EXTERNAL_TOOLS = "ExternalToolsGroup";

  @NonNls String GROUP_MAIN_MENU = "MainMenu";
  @NonNls String GROUP_MAIN_TOOLBAR = "MainToolBar";
  @NonNls String GROUP_EDITOR_POPUP = "EditorPopupMenu";
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

  @NonNls String ACTION_PREVIOUS_DIFF = "PreviousDiff";
  @NonNls String ACTION_NEXT_DIFF = "NextDiff";

  @NonNls String ACTION_EXPAND_ALL = "ExpandAll";
  @NonNls String ACTION_COLLAPSE_ALL = "CollapseAll";
  @NonNls String ACTION_EXPORT_TO_TEXT_FILE = "ExportToTextFile";

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
  @NonNls String REMOVE_FROM_FAVORITES = "RemoveFromFavorites";
  @NonNls String ADD_NEW_FAVORITES_LIST = "AddNewFavoritesList";
  @NonNls String RENAME_FAVORITES_LIST = "RenameFavoritesList";
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
  @NonNls String GROUP_DEBUGGER = "DebuggerActions";

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

  @NonNls String GROUP_USAGE_VIEW_POPUP = "UsageView.Popup";

  /*GUI designer actions*/
  @NonNls String GROUP_GUI_DESIGNER_EDITOR_POPUP = "GuiDesigner.EditorPopupMenu";
  @NonNls String GROUP_GUI_DESIGNER_COMPONENT_TREE_POPUP = "GuiDesigner.ComponentTreePopupMenu";
  @NonNls String GROUP_GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP = "GuiDesigner.PropertyInspectorPopupMenu";

  @NonNls String ACTION_GOTO_BACK    = "Back";
  @NonNls String ACTION_GOTO_FORWARD = "Forward";
  @NonNls String ACTION_GOTO_DECLARATION = "GotoDeclaration";
  @NonNls String ACTION_GOTO_TYPE_DECLARATION = "GotoTypeDeclaration";
  @NonNls String ACTION_GOTO_IMPLEMENTATION = "GotoImplementation";

  @NonNls String ACTION_COMMANDER_SYNC_VIEWS = "CommanderSyncViews";
  @NonNls String ACTION_COMMANDER_SWAP_PANELS = "CommanderSwapPanels";

  @NonNls String MODULE_SETTINGS = "ModuleSettings";

  @NonNls String GROUP_WELCOME_SCREEN_QUICKSTART = "WelcomeScreen.QuickStart";
  @NonNls String GROUP_WELCOME_SCREEN_DOC = "WelcomeScreen.Documentation";
  @NonNls String ACTION_KEYMAP_REFERENCE="Help.KeymapReference";
  @NonNls String ACTION_MOVE = "Move";
  @NonNls String ACTION_RENAME = "RenameElement";

  @NonNls String ACTION_ANALYZE_DEPENDENCIES = "ShowPackageDeps";
  @NonNls String GROUP_MOVE_MODULE_TO_GROUP = "MoveModuleToGroup";
  @NonNls String ACTION_CLEAR_TEXT = "TextComponent.ClearAction";
  @NonNls String ACTION_HIGHLIGHT_USAGES_IN_FILE = "HighlightUsagesInFile";
  @NonNls String ACTION_COPY_REFERENCE = "CopyReference";

  @NonNls String GROUP_ANALYZE = "AnalyzeMenu";
  @NonNls String ACTION_SHOW_ERROR_DESCRIPTION = "ShowErrorDescription";

  @NonNls String ACTION_EDITOR_DUPLICATE = "EditorDuplicate";
}
