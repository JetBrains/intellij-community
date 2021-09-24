// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

/**
 * Identifiers for standard actions and action groups.
 */
public interface IdeActions {
  String ACTION_EDITOR_CUT = "EditorCut";
  String ACTION_EDITOR_COPY = "EditorCopy";
  String ACTION_EDITOR_PASTE = "EditorPaste";
  String ACTION_EDITOR_PASTE_SIMPLE = "EditorPasteSimple";
  String ACTION_EDITOR_DELETE = "EditorDelete";
  String ACTION_EDITOR_DELETE_TO_WORD_START = "EditorDeleteToWordStart";
  String ACTION_EDITOR_DELETE_TO_WORD_END = "EditorDeleteToWordEnd";
  String ACTION_EDITOR_DELETE_LINE = "EditorDeleteLine";
  String ACTION_EDITOR_ENTER = "EditorEnter";
  String ACTION_EDITOR_START_NEW_LINE = "EditorStartNewLine";
  String ACTION_EDITOR_SPLIT = "EditorSplitLine";
  String ACTION_EDITOR_TEXT_START = "EditorTextStart";
  String ACTION_EDITOR_TEXT_END = "EditorTextEnd";
  String ACTION_EDITOR_FORWARD_PARAGRAPH = "EditorForwardParagraph";
  String ACTION_EDITOR_BACKWARD_PARAGRAPH = "EditorBackwardParagraph";
  String ACTION_EDITOR_FORWARD_PARAGRAPH_WITH_SELECTION = "EditorForwardParagraphWithSelection";
  String ACTION_EDITOR_BACKWARD_PARAGRAPH_WITH_SELECTION = "EditorBackwardParagraphWithSelection";
  String ACTION_EDITOR_TEXT_START_WITH_SELECTION = "EditorTextStartWithSelection";
  String ACTION_EDITOR_TEXT_END_WITH_SELECTION = "EditorTextEndWithSelection";
  String ACTION_EDITOR_MOVE_LINE_START = "EditorLineStart";
  String ACTION_EDITOR_MOVE_LINE_END = "EditorLineEnd";
  String ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION = "EditorLineStartWithSelection";
  String ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION = "EditorLineEndWithSelection";
  String ACTION_EDITOR_SELECT_WORD_AT_CARET = "EditorSelectWord";
  String ACTION_EDITOR_UNSELECT_WORD_AT_CARET = "EditorUnSelectWord";
  String ACTION_EDITOR_BACKSPACE = "EditorBackSpace";
  String ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION = "EditorLeftWithSelection";
  String ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION = "EditorRightWithSelection";
  String ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION = "EditorUpWithSelection";
  String ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION = "EditorDownWithSelection";
  String ACTION_EDITOR_SWAP_SELECTION_BOUNDARIES = "EditorSwapSelectionBoundaries";
  String ACTION_EDITOR_MOVE_CARET_UP = "EditorUp";
  String ACTION_EDITOR_MOVE_CARET_LEFT = "EditorLeft";
  String ACTION_EDITOR_MOVE_CARET_DOWN = "EditorDown";
  String ACTION_EDITOR_MOVE_CARET_RIGHT = "EditorRight";
  String ACTION_EDITOR_MOVE_CARET_PAGE_UP = "EditorPageUp";
  String ACTION_EDITOR_MOVE_CARET_PAGE_DOWN = "EditorPageDown";
  String ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION = "EditorPageUpWithSelection";
  String ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION = "EditorPageDownWithSelection";
  String ACTION_EDITOR_NEXT_WORD = "EditorNextWord";
  String ACTION_EDITOR_PREVIOUS_WORD = "EditorPreviousWord";
  String ACTION_EDITOR_NEXT_WORD_WITH_SELECTION = "EditorNextWordWithSelection";
  String ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION = "EditorPreviousWordWithSelection";
  String ACTION_EDITOR_TAB = "EditorTab";
  String ACTION_EDITOR_INDENT_SELECTION = "EditorIndentSelection";
  String ACTION_EDITOR_UNINDENT_SELECTION = "EditorUnindentSelection";
  String ACTION_EDITOR_EMACS_TAB = "EmacsStyleIndent";
  String ACTION_EDITOR_ESCAPE = "EditorEscape";
  String ACTION_EDITOR_JOIN_LINES = "EditorJoinLines";
  String ACTION_EDITOR_COMPLETE_STATEMENT = "EditorCompleteStatement";
  String ACTION_EDITOR_USE_SOFT_WRAPS = "EditorToggleUseSoftWraps";
  String ACTION_EDITOR_ADD_OR_REMOVE_CARET= "EditorAddOrRemoveCaret";
  String ACTION_EDITOR_CREATE_RECTANGULAR_SELECTION= "EditorCreateRectangularSelection";
  String ACTION_EDITOR_ADD_RECTANGULAR_SELECTION_ON_MOUSE_DRAG= "EditorAddRectangularSelectionOnMouseDrag";
  String ACTION_EDITOR_CREATE_RECTANGULAR_SELECTION_ON_MOUSE_DRAG = "EditorCreateRectangularSelectionOnMouseDrag";
  String ACTION_EDITOR_CLONE_CARET_BELOW= "EditorCloneCaretBelow";
  String ACTION_EDITOR_CLONE_CARET_ABOVE= "EditorCloneCaretAbove";
  String ACTION_EDITOR_ADD_CARET_PER_SELECTED_LINE = "EditorAddCaretPerSelectedLine";
  String ACTION_EDITOR_TOGGLE_STICKY_SELECTION = "EditorToggleStickySelection";
  String ACTION_EDITOR_TOGGLE_OVERWRITE_MODE = "EditorToggleInsertState";
  String ACTION_EDITOR_TOGGLE_CASE = "EditorToggleCase";
  String ACTION_EDITOR_TRANSPOSE = "EditorTranspose";

  String ACTION_EDITOR_SHOW_PARAMETER_INFO = "ParameterInfo";
  String ACTION_EDITOR_NEXT_PARAMETER = "NextParameter";
  String ACTION_EDITOR_PREV_PARAMETER = "PrevParameter";
  String ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE = "NextTemplateVariable";
  String ACTION_EDITOR_PREVIOUS_TEMPLATE_VARIABLE = "PreviousTemplateVariable";

  String ACTION_EDITOR_REFORMAT = "ReformatCode";
  String ACTION_EDITOR_AUTO_INDENT_LINES = "AutoIndentLines";

  String ACTION_COMMENT_LINE = "CommentByLineComment";
  String ACTION_COMMENT_BLOCK = "CommentByBlockComment";

  String ACTION_COPY = "$Copy";
  String ACTION_CUT = "$Cut";
  String ACTION_DELETE = "$Delete";
  String ACTION_PASTE = "$Paste";
  String ACTION_SELECT_ALL = "$SelectAll";
  String ACTION_CONTEXT_HELP = "ContextHelp";
  String ACTION_EDIT_SOURCE = "EditSource";
  String ACTION_VIEW_SOURCE = "ViewSource";
  String ACTION_SHOW_INTENTION_ACTIONS = "ShowIntentionActions";
  String ACTION_CODE_COMPLETION = "CodeCompletion";
  String ACTION_SMART_TYPE_COMPLETION = "SmartTypeCompletion";
  String ACTION_HIPPIE_COMPLETION = "HippieCompletion";
  String ACTION_HIPPIE_BACKWARD_COMPLETION = "HippieBackwardCompletion";
  String ACTION_CHOOSE_LOOKUP_ITEM = "EditorChooseLookupItem";
  String ACTION_CHOOSE_LOOKUP_ITEM_REPLACE = "EditorChooseLookupItemReplace";
  String ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT = "EditorChooseLookupItemCompleteStatement";
  String ACTION_CHOOSE_LOOKUP_ITEM_DOT = "EditorChooseLookupItemDot";
  String ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB = "ExpandLiveTemplateByTab";
  String ACTION_EXPAND_LIVE_TEMPLATE_CUSTOM = "ExpandLiveTemplateCustom";
  String ACTION_UPDATE_TAG_WITH_EMMET = "EmmetUpdateTag";

  String ACTION_LOOKUP_UP = "EditorLookupUp";
  String ACTION_LOOKUP_DOWN = "EditorLookupDown";

  String GROUP_EXTERNAL_TOOLS = "ExternalToolsGroup";

  String GROUP_MAIN_MENU = "MainMenu";
  String GROUP_MAIN_TOOLBAR = "MainToolBar";
  String GROUP_EXPERIMENTAL_TOOLBAR_ACTIONS = "ExperimentalToolbarActions";
  String GROUP_EXPERIMENTAL_TOOLBAR = "NewToolbarActions";
  String GROUP_EXPERIMENTAL_TOOLBAR_WITHOUT_RIGHT_PART = "NewToolbarActionsWithoutRight";
  String GROUP_EDITOR_POPUP = "EditorPopupMenu";
  String GROUP_BASIC_EDITOR_POPUP = "BasicEditorPopupMenu";
  String GROUP_CONSOLE_EDITOR_POPUP = "ConsoleEditorPopupMenu";
  String GROUP_CUT_COPY_PASTE = "CutCopyPasteGroup";
  String GROUP_EDITOR_TAB_POPUP = "EditorTabPopupMenu";
  String GROUP_HELP_MENU = "HelpMenu";
  String GROUP_INTENTIONS = "Intentions";

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
  String ACTION_NEXT_EDITOR_TAB = "NextEditorTab";
  String ACTION_PREVIOUS_EDITOR_TAB = "PreviousEditorTab";
  String ACTION_FIND = "Find";
  String ACTION_FIND_NEXT = "FindNext";
  String ACTION_FIND_PREVIOUS = "FindPrevious";
  String ACTION_FIND_WORD_AT_CARET = "FindWordAtCaret";
  String ACTION_FIND_PREV_WORD_AT_CARET = "FindPrevWordAtCaret";
  String ACTION_SELECT_NEXT_OCCURENCE = "SelectNextOccurrence";
  String ACTION_SELECT_ALL_OCCURRENCES = "SelectAllOccurrences";
  String ACTION_UNSELECT_PREVIOUS_OCCURENCE = "UnselectPreviousOccurrence";
  String ACTION_REPLACE = "Replace";
  String ACTION_TOGGLE_FIND_IN_SELECTION_ONLY = "ToggleFindInSelection";
  String ACTION_COMPILE = "Compile";
  String ACTION_COMPILE_PROJECT = "CompileProject";
  String ACTION_MAKE_MODULE = "MakeModule";
  String ACTION_INSPECT_CODE = "InspectCode";

  String ACTION_FIND_USAGES = "FindUsages";
  String ACTION_FIND_IN_PATH = "FindInPath";

  String ACTION_TYPE_HIERARCHY = "TypeHierarchy";
  String ACTION_METHOD_HIERARCHY = "MethodHierarchy";
  String ACTION_CALL_HIERARCHY = "CallHierarchy";

  String ACTION_EXTERNAL_JAVADOC = "ExternalJavaDoc";

  String ACTION_CLOSE = "CloseContent";
  String ACTION_CLOSE_EDITOR = "CloseEditor";
  String ACTION_CLOSE_ALL_EDITORS = "CloseAllEditors";
  String ACTION_CLOSE_ALL_UNMODIFIED_EDITORS = "CloseAllUnmodifiedEditors";
  String ACTION_CLOSE_ALL_EDITORS_BUT_THIS = "CloseAllEditorsButActive";

  String ACTION_SHOW_DIFF_COMMON = "Diff.ShowDiff";
  String ACTION_PREVIOUS_DIFF = "PreviousDiff";
  String ACTION_NEXT_DIFF = "NextDiff";
  String GROUP_DIFF_EDITOR_POPUP = "Diff.EditorPopupMenu";
  String DIFF_VIEWER_POPUP = "Diff.ViewerPopupMenu";
  String DIFF_VIEWER_TOOLBAR = "Diff.ViewerToolbar";
  String GROUP_DIFF_EDITOR_GUTTER_POPUP = "Diff.EditorGutterPopupMenu";
  String GROUP_DIFF_EDITOR_SETTINGS = "Diff.EditorGutterPopupMenu.EditorSettings";

  String ACTION_EXPAND_ALL = "ExpandAll";
  String ACTION_COLLAPSE_ALL = "CollapseAll";
  String ACTION_EXPORT_TO_TEXT_FILE = "ExportToTextFile";

  String ACTION_COLLAPSE_REGION = "CollapseRegion";
  String ACTION_EXPAND_REGION = "ExpandRegion";
  String ACTION_EXPAND_ALL_REGIONS = "ExpandAllRegions";
  String ACTION_COLLAPSE_ALL_REGIONS = "CollapseAllRegions";
  String ACTION_EXPAND_REGION_RECURSIVELY = "ExpandRegionRecursively";
  String ACTION_COLLAPSE_REGION_RECURSIVELY = "CollapseRegionRecursively";
  String ACTION_EXPAND_TO_LEVEL_1 = "ExpandToLevel1";
  String ACTION_EXPAND_ALL_TO_LEVEL_1 = "ExpandAllToLevel1";

  String ACTION_NEW_HORIZONTAL_TAB_GROUP = "NewHorizontalTabGroup";
  String ACTION_NEW_VERTICAL_TAB_GROUP = "NewVerticalTabGroup";
  String ACTION_MOVE_EDITOR_TO_OPPOSITE_TAB_GROUP = "MoveEditorToOppositeTabGroup";
  String ACTION_CHANGE_SPLIT_ORIENTATION = "ChangeSplitOrientation";
  String ACTION_PIN_ACTIVE_EDITOR = "PinActiveEditor";

  String GROUP_VERSION_CONTROLS = "VersionControlsGroup";

  String GROUP_PROJECT_VIEW_POPUP = "ProjectViewPopupMenu";
  String GROUP_NAVBAR_POPUP = "NavbarPopupMenu";
  String GROUP_NAVBAR_TOOLBAR = "NavBarToolBar";
  String GROUP_COMMANDER_POPUP = "CommanderPopupMenu";
  String GROUP_TESTTREE_POPUP = "TestTreePopupMenu";
  String GROUP_TESTSTATISTICS_POPUP = "TestStatisticsTablePopupMenu";

  String GROUP_FAVORITES_VIEW_POPUP = "FavoritesViewPopupMenu";
  String ADD_TO_FAVORITES = "AddToFavorites";
  String ADD_NEW_FAVORITES_LIST = "AddNewFavoritesList";
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

  String GROUP_COMPILER_ERROR_VIEW_POPUP = "CompilerErrorViewPopupMenu";

  String GROUP_OTHER_MENU = "OtherMenu";
  String GROUP_EDITOR = "EditorActions";
  String GROUP_EDITOR_BIDI_TEXT_DIRECTION = "EditorBidiTextDirection";

  String ACTION_TOGGLE_LINE_BREAKPOINT = "ToggleLineBreakpoint";
  String ACTION_RUN_TO_CURSOR = "RunToCursor";

  String ACTION_REFRESH = "Refresh";

  String GROUP_GENERATE = "GenerateGroup";
  String GROUP_NEW = "NewGroup";
  String GROUP_WEIGHING_NEW = "WeighingNewGroup";
  String GROUP_CHANGE_SCHEME = "ChangeScheme";

  String GROUP_FILE = "FileMenu";
  String ACTION_NEW_PROJECT = "NewProject";
  String ACTION_SHOW_SETTINGS = "ShowSettings";

  String GROUP_RUN = "RunMenu";
  String GROUP_RUNNER_ACTIONS = "RunnerActions";
  String RUN_TOOLBAR_PROCESSES_ACTION_GROUP = "RunToolbarProcessesActionGroup";
  String ACTION_DEFAULT_RUNNER = "Run";
  String ACTION_DEFAULT_DEBUGGER = "Debug";
  String ACTION_EDIT_RUN_CONFIGURATIONS = "editRunConfigurations";
  String ACTION_RERUN = "Rerun";

  String ACTION_STOP_PROGRAM = "Stop";
  String ACTION_NEW_ELEMENT = "NewElement";

  String ACTION_QUICK_JAVADOC = "QuickJavaDoc";
  String ACTION_QUICK_IMPLEMENTATIONS = "QuickImplementations";
  String ACTION_CHECKIN_PROJECT = "CheckinProject";
  String ACTION_FILE_STRUCTURE_POPUP = "FileStructurePopup";
  String ACTION_TOGGLE_RENDERED_DOC = "ToggleRenderedDocPresentation";
  String ACTION_TOGGLE_RENDERED_DOC_FOR_ALL = "ToggleRenderedDocPresentationForAll";

  String GROUP_DOC_COMMENT_GUTTER_ICON_CONTEXT_MENU = "DocCommentGutterIconContextMenu";

  String GROUP_USAGE_VIEW_POPUP = "UsageView.Popup";

  /*GUI designer actions*/
  String GROUP_GUI_DESIGNER_EDITOR_POPUP = "GuiDesigner.EditorPopupMenu";
  String GROUP_GUI_DESIGNER_COMPONENT_TREE_POPUP = "GuiDesigner.ComponentTreePopupMenu";
  String GROUP_GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP = "GuiDesigner.PropertyInspectorPopupMenu";

  String ACTION_GOTO_LAST_CHANGE    = "JumpToLastChange";
  String ACTION_GOTO_NEXT_CHANGE    = "JumpToNextChange";

  String ACTION_GOTO_BACK    = "Back";
  String ACTION_GOTO_FORWARD = "Forward";
  String ACTION_GOTO_DECLARATION = "GotoDeclaration";
  String ACTION_GOTO_TYPE_DECLARATION = "GotoTypeDeclaration";
  String ACTION_GOTO_IMPLEMENTATION = "GotoImplementation";
  String ACTION_GOTO_SUPER = "GotoSuperMethod";

  String MODULE_SETTINGS = "ModuleSettings";

  String GROUP_WELCOME_SCREEN_QUICKSTART = "WelcomeScreen.QuickStart";
  String GROUP_WELCOME_SCREEN_QUICKSTART_EMPTY_STATE = "WelcomeScreen.QuickStart.EmptyState";
  String GROUP_WELCOME_SCREEN_QUICKSTART_PROJECTS_STATE = "WelcomeScreen.QuickStart.ProjectsState";
  String GROUP_WELCOME_SCREEN_DOC = "WelcomeScreen.Documentation";
  String GROUP_WELCOME_SCREEN_CONFIGURE = "WelcomeScreen.Configure";
  String GROUP_WELCOME_SCREEN_OPTIONS = "WelcomeScreen.Options";
  String GROUP_WELCOME_SCREEN_LEARN_IDE = "WelcomeScreen.LearnIdeHelp";

  /** @deprecated please use {@link #GROUP_WELCOME_SCREEN_OPTIONS} (and the corresponding action group) instead */
  @Deprecated
  String GROUP_WELCOME_SCREEN_HELP = "WelcomeScreen.Help";

  String ACTION_KEYMAP_REFERENCE="Help.KeymapReference";
  String ACTION_MOVE = "Move";
  String ACTION_RENAME = "RenameElement";

  String ACTION_ANALYZE_DEPENDENCIES = "ShowPackageDeps";
  String ACTION_ANALYZE_BACK_DEPENDENCIES = "ShowBackwardPackageDeps";
  String ACTION_ANALYZE_CYCLIC_DEPENDENCIES = "ShowPackageCycles";
  String ACTION_ANALYZE_MODULE_DEPENDENCIES = "ShowModulesDependencies";
  String GROUP_MOVE_MODULE_TO_GROUP = "MoveModuleToGroup";
  String ACTION_CLEAR_TEXT = "TextComponent.ClearAction";
  String ACTION_HIGHLIGHT_USAGES_IN_FILE = "HighlightUsagesInFile";
  String ACTION_COPY_REFERENCE = "CopyReference";

  String GROUP_ANALYZE = "AnalyzeMenu";
  String ACTION_SHOW_ERROR_DESCRIPTION = "ShowErrorDescription";

  String ACTION_EDITOR_DUPLICATE = "EditorDuplicate";
  String ACTION_EDITOR_DUPLICATE_LINES = "EditorDuplicateLines";
  String ACTION_EDITOR_SORT_LINES = "EditorSortLines";
  String ACTION_EDITOR_REVERSE_LINES = "EditorReverseLines";

  String ACTION_EDIT_PROPERTY_VALUE = "EditPropertyValue";

  String GROUP_EDITOR_GUTTER = "EditorGutterPopupMenu";

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
  String ACTION_RUN_ANYTHING = "RunAnything";
  String ACTION_OPEN_IN_RIGHT_SPLIT = "OpenInRightSplit";
  String ACTION_OPEN_IN_NEW_WINDOW = "OpenElementInNewWindow";
  String ACTION_EDIT_SOURCE_IN_NEW_WINDOW = "EditSourceInNewWindow";

  String ACTION_MARK_ALL_NOTIFICATIONS_AS_READ = "MarkNotificationsAsRead";
  String ACTION_SWITCHER = "Switcher";

  String INSPECTION_TOOL_WINDOW_TREE_POPUP = "InspectionToolWindow.TreePopup";

  String EXTRACT_METHOD_TOOL_WINDOW_TREE_POPUP = "ExtractMethodToolWindow.TreePopup";

  String ACTION_METHOD_OVERLOAD_SWITCH_UP = "MethodOverloadSwitchUp";
  String ACTION_METHOD_OVERLOAD_SWITCH_DOWN = "MethodOverloadSwitchDown";

  String ACTION_UPDATE_RUNNING_APPLICATION = "UpdateRunningApplication";

  String ACTION_BRACE_OR_QUOTE_OUT = "BraceOrQuoteOut";

  String GROUP_TOUCHBAR = "TouchBar";

  String BREADCRUMBS_OPTIONS_GROUP = "EditorBreadcrumbsSettings";
  String BREADCRUMBS_SHOW_ABOVE = "EditorBreadcrumbsShowAbove";
  String BREADCRUMBS_SHOW_BELOW = "EditorBreadcrumbsShowBelow";
  String BREADCRUMBS_HIDE_BOTH = "EditorBreadcrumbsHideBoth";

  String ACTION_RESTORE_FONT_PREVIEW_TEXT = "RestoreFontPreviewTextAction";
}
