package com.intellij.ide.customize.transferSettings.providers.vswin.mappings

object KeyBindingsMappings {
  val newTokens = hashMapOf(
    "," to "COMMA",
    "`" to "BACK_QUOTE",
    "/" to "SLASH",
    "\\" to "BACK_SLASH",
    "[" to "OPEN_BRACKET",
    "]" to "CLOSE_BRACKET",
    "·" to "PERIOD",
    "." to "PERIOD",

    "Ctrl" to "control",
    "Shift" to "shift",
    "Alt" to "alt",

    "PgDn" to "PAGE_DOWN",
    "PgUp" to "PAGE_UP",

    "End" to "END",
    "Home" to "HOME",
    "Break" to "PAUSE", // meh

    "Num ." to "PERIOD",

    "Down Arrow" to "DOWN",
    "Up Arrow" to "UP",
    "Right Arrow" to "RIGHT",
    "Left Arrow" to "LEFT",

    "Space" to "SPACE",
    "Ins" to "INSERT",
    "Bkspce" to "BACK_SPACE",
    "Enter" to "ENTER",
    "Del" to "DELETE",
    "Tab" to "TAB"
  )

  fun vsCommandToIdeaAction(command: String): String? {
    return when (command) {
      "Edit.Copy" -> "\$Copy"
      "Edit.Cut" -> "\$Cut"
      "Edit.Delete" -> "\$Delete"
      "Edit.Paste" -> "\$Paste"
      "Edit.Redo" -> "\$Redo"
      "Edit.SelectAll" -> "\$SelectAll"
      "Edit.Undo" -> "\$Undo"
      "View.Output" -> "ActivateBuildToolWindow"
      "View.SQLServerObjectExplorer" -> "ActivateDatabaseToolWindow"
      "Debug.Locals" -> "ActivateDebugToolWindow"
      "ReSharper_ShowErrorsView" -> "ActivateErrorsInSolutionToolWindow"
      "ReSharper.ReSharper_ShowFindResults" -> "ActivateFindToolWindow"
      "ReSharper.ReSharper_ShowInspectionWindow"
      -> "ActivateInspectionResultsToolWindow"
      "Project.ManageNugetPackages" -> "ActivateNuGetToolWindow"
      "View.ErrorList" -> "ActivateProblemsViewToolWindow"
      "View.SolutionExplorer" -> "ActivateProjectToolWindow"
      "ReSharper.ReSharper_ShowCodeStructure" -> "ActivateStructureToolWindow"
      "View.Terminal" -> "ActivateTerminalToolWindow"
      "TestExplorer.ShowTestExplorer" -> "ActivateUnitTestsToolWindow"
      "Team.Git.ManageBranches" -> "ActivateVersionControlToolWindow"
      "Project.AddExistingItem" -> "AddRiderItem"
      "ReSharper_Reindent" -> "AutoIndentLines"
      "View.NavigateBackward" -> "Back"
      "Build.BuildSelection" -> "BuildCurrentProject"
      "Build.BuildSolution" -> "BuildSolutionAction"
      "Build.Cancel" -> "CancelBuildAction"
      "Refactor.RemoveParameters",
      "Refactor.ReorderParameters",
      "ReSharper.ReSharper_ChangeSignature" -> "ChangeSignature"
      "Git.CommitOrStash",
      "Team.Git.GoToGitChanges" -> "CheckinProject"
      "ReSharper.ReSharper_RunConfigSettings" -> "ChooseRunConfiguration"
      "ReSharper.ReSharper_CompleteCodeTypeName" -> "ClassNameCompletion"
      "Edit.LineUpExtendColumn" -> "CloneCaretAboveWithVirtualSpace"
      "Edit.LineDownExtendColumn" -> "CloneCaretBelowWithVirtualSpace"
      "Window.CloseDocumentWindow" -> "CloseContent"
      "EditorContextMenus.FileHealthIndicator.RunDefaultCodeCleanup",
      "ReSharper.ReSharper_CleanupCode" -> "CodeCleanup"
      "Edit.CompleteWord",
      "Edit.ListMembers",
      "ReSharper_CompleteCodeBasic" -> "CodeCompletion"
      "Edit.CollapseAllOutlining" -> "CollapseAllRegions"
      "Edit.CollapseCurrentRegion" -> "CollapseRegion"
      "Edit.HideSelection" -> "CollapseSelection"
      "ReSharper_BlockComment" -> "CommentByBlockComment"
      "Edit.CommentSection",
      "Edit.UncommentSelection",
      "ReSharper_LineComment" -> "CommentByLineComment"
      "Build.Compile" -> "Compile"
      "Debug.Start",
      "ReSharper_CurrentConfigDebugAlt" -> "Debug"
      "EditorContextMenus.CodeWindow.Breakpoint.BreakpointEditlabels"
      -> "EditBreakpoint"
      "Edit.DeleteBackwards" -> "EditorBackSpace"
      "ReSharper.ReSharper_CompleteStatement" -> "EditorCompleteStatement"
      "Edit.LineDelete" -> "EditorDeleteLine"
      "Edit.LineDown" -> "EditorDown"
      "Edit.LineDownExtend" -> "EditorDownWithSelection"
      "Edit.Duplicate" -> "EditorDuplicate"
      "Edit.BreakLine" -> "EditorEnter"
      "Edit.SelectionCancel" -> "EditorEscape"
      //"Edit.InsertTab" -> "EditorIndentSelection"
      "ReSharper_JoinLines" -> "EditorJoinLines"
      "Edit.CharLeft" -> "EditorLeft"
      "Edit.CharLeftExtend" -> "EditorLeftWithSelection"
      "Edit.LineEnd" -> "EditorLineEnd"
      "Edit.LineEndExtend" -> "EditorLineEndWithSelection"
      "Edit.LineStart" -> "EditorLineStart"
      "Edit.LineStartExtend" -> "EditorLineStartWithSelection"
      "Edit.GotoBrace" -> "EditorMatchBrace"
      "Edit.ViewBottom" -> "EditorMoveToPageBottom"
      "Edit.ViewBottomExtend" -> "EditorMoveToPageBottomWithSelection"
      "Edit.ViewTop" -> "EditorMoveToPageTop"
      "Edit.ViewTopExtend" -> "EditorMoveToPageTopWithSelection"
      "Edit.MoveControlRight",
      "Edit.WordNext" -> "EditorNextWord"
      "Edit.WordNextExtend" -> "EditorNextWordWithSelection"
      "Edit.PageDown" -> "EditorPageDown"
      "Edit.PageDownExtend" -> "EditorPageDownWithSelection"
      "Edit.PageUp" -> "EditorPageUp"
      "Edit.PageUpExtend" -> "EditorPageUpWithSelection"
      "Edit.MoveControlLeft",
      "Edit.WordPrevious" -> "EditorPreviousWord"
      "Edit.WordPreviousExtend" -> "EditorPreviousWordWithSelection"
      "Edit.CharRight" -> "EditorRight"
      "Edit.CharRightExtend" -> "EditorRightWithSelection"
      "Edit.ScrollLineDown" -> "EditorScrollDown"
      "Edit.ScrollLineUp" -> "EditorScrollUp"
      "Edit.ExpandSelection",
      "ReSharper_ExtendSelection",
      "Edit.SelectCurrentWord" -> "EditorSelectWord"
      "Edit.LineOpenBelow" -> "EditorStartNewLine"
      "Edit.LineOpenAbove" -> "EditorStartNewLineBefore"
      "Edit.InsertTab" -> "EditorTab"
      "Edit.DocumentEnd" -> "EditorTextEnd"
      "Edit.DocumentEndExtend" -> "EditorTextEndWithSelection"
      "Edit.DocumentStart" -> "EditorTextStart"
      "Edit.DocumentStartExtend" -> "EditorTextStartWithSelection"
      "Edit.MakeLowercase",
      "Edit.MakeUppercase" -> "EditorToggleCase"
      "Edit.ViewWhiteSpace" -> "EditorToggleShowWhitespaces"
      "Edit.ToggleWordWrap" -> "EditorToggleUseSoftWraps"
      "Edit.TabLeft" -> "EditorUnindentSelection"
      "Edit.ContractSelection",
      "ReSharper.ReSharper_ShrinkSelection" -> "EditorUnSelectWord"
      "Edit.LineUp" -> "EditorUp"
      "Edit.LineUpExtend" -> "EditorUpWithSelection"
      "ReSharper_EnableDaemon" -> "EnableDaemon"
      "Debug.QuickWatch" -> "EvaluateExpression"
      "Edit.ExpandAllOutlining" -> "ExpandAllRegions"
      "Edit.ExpandCurrentRegion" -> "ExpandRegion"
      "Refactor.ExtractMethod",
      "ReSharper.ReSharper_ExtractMethod" -> "ExtractMethod"
      "Edit.GoToMember",
      "ReSharper_GotoFileMember" -> "FileStructurePopup"
      "Edit.Find" -> "Find"
      "Edit.FindinFiles" -> "FindInPath"
      "Edit.FindNext",
      "Edit.NextHighlightedReference",
      "ReSharper_ResultListGoToNextLocation" -> "FindNext"
      "Edit.FindPrevious",
      "Edit.PreviousHighlightedReference",
      "ReSharper_ResultListGoToPrevLocation" -> "FindPrevious"
      "Edit.FindAllReferences",
      "ReSharper.ReSharper_FindUsages" -> "FindUsages"
      "Edit.FindNextSelected" -> "FindWordAtCaret"
      "Window.ActivateDocumentWindow" -> "FocusEditor"
      "View.NavigateForward" -> "Forward"
      "ReSharper_Generate" -> "Generate"
      "Team.Git.GoToGitBranches" -> "Git.Branches"
      "Team.Git.CommitAndPush" -> "Git.Commit.And.Push.Executor"
      "ReSharper_BookmarksGoToBookmark0" -> "GotoBookmark0"
      "ReSharper_BookmarksGoToBookmark1" -> "GotoBookmark1"
      "ReSharper_BookmarksGoToBookmark2" -> "GotoBookmark2"
      "ReSharper_BookmarksGoToBookmark3" -> "GotoBookmark3"
      "ReSharper_BookmarksGoToBookmark4" -> "GotoBookmark4"
      "ReSharper_BookmarksGoToBookmark5" -> "GotoBookmark5"
      "ReSharper_BookmarksGoToBookmark6" -> "GotoBookmark6"
      "ReSharper_BookmarksGoToBookmark7" -> "GotoBookmark7"
      "ReSharper_BookmarksGoToBookmark8" -> "GotoBookmark8"
      "ReSharper_BookmarksGoToBookmark9" -> "GotoBookmark9"
      "Edit.GoToType" -> "GotoClass"
      "Edit.GotoDefinition",
      "ReSharper_GotoDeclaration" -> "GotoDeclaration"
      "Edit.GoToFile",
      "ReSharper.ReSharper_GotoFile" -> "GotoFile"
      "Edit.GoToDeclaration",
      "ReSharper_GotoImplementations" -> "GotoImplementation"
      "Edit.GoTo" -> "GotoLine"
      "Edit.NextBookmark" -> "GotoNextBookmark"
      "Edit.GotoNextIssueinFile",
      "ReSharper.ReSharper_GotoNextHighlight" -> "GotoNextError"
      "Edit.PreviousBookmark" -> "GotoPreviousBookmark"
      "Edit.GotoPreviousIssueinFile",
      "ReSharper.ReSharper_GotoPrevHighlight" -> "GotoPreviousError"
      "ReSharper.ReSharper_GotoRelatedFiles" -> "GotoRelated"
      "ReSharper.ReSharper_GotoBase" -> "GotoSuperMethod"
      "Edit.GoToSymbol",
      "ReSharper.ReSharper_GotoSymbol" -> "GotoSymbol"
      "Edit.GoToTypeDefinition",
      "ReSharper.ReSharper_GotoTypeDeclaration" -> "GotoTypeDeclaration"
      "Window.CloseToolWindow" -> "HideActiveWindow"
      "Window.AutoHideAll" -> "HideAllWindows"
      "Resharper.ReSharper_HighlightUsages" -> "HighlightUsagesInFile"
      "Edit.IncrementalSearch" -> "IncrementalSearch"
      "ReSharper.ReSharper_InlineVariable" -> "Inline"
      "Edit.InsertSnippet",
      "ReSharper.ReSharper_LiveTemplatesInsert" -> "InsertLiveTemplate"
      "ReSharper.ReSharper_InspectThis" -> "InspectThis"
      "ReSharper.ReSharper_IntroduceField" -> "IntroduceField"
      "ReSharper.ReSharper_IntroduceParameter" -> "IntroduceParameter"
      "ReSharper.ReSharper_IntroVariable" -> "IntroduceVariable"
      "Edit.GoToLastEditLocation",
      "ReSharper.ReSharper_GotoLastEditLocation" -> "JumpToLastChange"
      "Window.PreviousPane",
      "ReSharper.WindowManagerActivateRecentTool" -> "JumpToLastWindow"
      "Debug.SetNextStatement" -> "JumpToStatement"
      "ReSharper_LocateInSolutionOrAssemblyExplorer" -> "LocateInSolutionView"
      "Edit.NextMethod" -> "MethodDown"
      "Edit.PreviousMethod" -> "MethodUp"
      "ReSharper.ReSharper_Move" -> "Move"
      "ReSharper_MoveLeft" -> "MoveElementLeft"
      "ReSharper_MoveRight" -> "MoveElementRight"
      "Edit.MoveSelectedLinesDown" -> "MoveLineDown"
      "Edit.MoveSelectedLinesUp" -> "MoveLineUp"
      "ReSharper.ReSharper_MoveDown" -> "MoveStatementDown"
      "ReSharper.ReSharper_MoveUp" -> "MoveStatementUp"
      "Debug.Immediate" -> "NavigateToImmediateWindow"
      "File.NewProject" -> "NewRiderProject"
      "Window.NextTab" -> "NextTab"
      "File.OpenFile" -> "OpenFile"
      "Edit.ParameterInfo",
      "ReSharper_ParameterInfoShow" -> "ParameterInfo"
      "Edit.CycleClipboardRing",
      "ReSharper_PasteMultiple" -> "PasteMultiple"
      "Debug.BreakAll" -> "Pause"
      "Window.PreviousTab" -> "PreviousTab"
      "File.Print" -> "Print"
      "Edit.PeekDefinition" -> "QuickImplementations"
      "Edit.QuickInfo",
      "ReSharper.ReSharper_QuickDoc" -> "QuickJavaDoc"
      "ReSharper.ReSharper_GotoRecentEdits" -> "RecentChangedFiles"
      "Edit.GoToRecentFile",
      "Edit.GoToRecent",
      "ReSharper.ReSharper_GotoRecentFiles" -> "RecentFiles"
      "ReSharper.ReSharper_RefactorThis" -> "Refactorings.QuickListPopupAction"
      "Edit.FormatSelection",
      "Edit.FormatDocument",
      "Resharper.ReSharper_ReformatCode" -> "ReformatCode"
      "Refactor.Rename",
      "ReSharper.ReSharper_Rename" -> "RenameElement"
      "Edit.Replace" -> "Replace"
      "Edit.ReplaceinFiles" -> "ReplaceInPath"
      "Debug.Restart" -> "Rerun"
      "ReSharper_UnitTestSessionRepeatPreviousRun" -> "RerunTests"
      "EditorContextMenus.Navigate.GoToContainingBlock",
      "ReSharper_GotoContainingDeclaration"
      -> "ReSharperGotoContainingDeclaration"
      "ReSharper_GotoInheritors" -> "ReSharperGotoImplementation"
      "ReSharper_GotoNextErrorInSolution" -> "ReSharperGotoNextErrorInSolution"
      "ReSharper_GotoPrevErrorInSolution" -> "ReSharperGotoPrevErrorInSolution"
      "ReSharper_NavigateTo" -> "ReSharperNavigateTo"
      "Edit.ExpandSelectiontoContainingBlock",
      "ReSharper_SelectContainingDeclaration"
      -> "ReSharperSelectContainingDeclaration"
      "Refactor.EncapsulateField",
      "ReSharper.ReSharper_EncapsulateField"
      -> "RiderBackendAction-EncapsulateField"
      "Edit.CollapseToDefinitions" -> "RiderCollapseToDefinitions"
      "Help.F1Help" -> "RiderOnlineHelpAction"
      "File.OpenProject" -> "RiderOpenSolution"
      "Debug.DeleteAllBreakpoints" -> "RiderRemoveAllLineBreakpoints"
      "ReSharper.ReSharper_UnitTestSessionAppendTests"
      -> "RiderUnitTestAppendTestsAction"
      "ReSharper_CoverAllTestsFromSolution" -> "RiderUnitTestCoverSolutionAction"
      "TestExplorer.DebugAllTests",
      "ReSharper.ReSharper_UnitTestDebugContext"
      -> "RiderUnitTestDebugContextAction"
      "TestExplorer.DebugAllTestsInContext" -> "RiderUnitTestDebugContextTwAction"
      "ReSharper_ProfileAllTestsFromCurrentSessionWithMemoryUnitAction"
      -> "RiderUnitTestDotMemoryUnitContextAction"
      "ReSharper_ProfileAllTestsFromSolutionWithMemoryUnitAction"
      -> "RiderUnitTestDotMemoryUnitSolutionAction"
      "ReSharper.ReSharper_ShowUnitTestExplorer"
      -> "RiderUnitTestFocusExplorerAction"
      "ReSharper_ShowUnitTestSessions" -> "RiderUnitTestFocusSessionAction"
      "ReSharper.ReSharper_UnitTestSessionNewSession"
      -> "RiderUnitTestNewSessionAction"
      "ReSharper_UnitTestSessionRemoveSelectedNodes"
      -> "RiderUnitTestRemoveElementsFromSessionTwAction"
      "TestExplorer.RepeatLastRun",
      "ReSharper.ReSharper_UnitTestSessionRepeatPreviousRun"
      -> "RiderUnitTestRepeatPreviousRunAction"
      "TestExplorer.RunAllTests",
      "ReSharper.ReSharper_UnitTestRunFromContext"
      -> "RiderUnitTestRunContextAction"
      "TestExplorer.RunAllTestsInContext" -> "RiderUnitTestRunContextTwAction"
      "ReSharper_UnitTestRunFromContextUntilFail"
      -> "RiderUnitTestRunContextUntilFailAction"
      "ReSharper.ReSharper_UnitTestRunCurrentSession"
      -> "RiderUnitTestRunCurrentSessionAction"
      "ReSharper_UnitTestRunSolution" -> "RiderUnitTestRunSolutionAction"
      "ReSharper_UnitTestSessionAbort" -> "RiderUnitTestSessionAbortAction"
      "Debug.StartWithoutDebugging",
      "CurrentConfigRunAlt" -> "Run"
      "Debug.RunToCursor" -> "RunToCursor"
      "ReSharper.ReSharper_SafeDelete" -> "SafeDelete"
      "File.SaveAll" -> "SaveAll"
      "File.SaveSelectedItems" -> "SaveDocument"
      "Edit.GoToAll",
      "ReSharper.ReSharper_GotoType" -> "SearchEverywhere"
      "Edit.InsertCaretsatAllMatching" -> "SelectAllOccurrences"
      "Edit.InsertNextMatchingCaret" -> "SelectNextOccurrence"
      "View.BookmarkWindow",
      "ReSharper_BookmarksBookmarksMenu" -> "ShowBookmarks"
      "Debug.ShowNextStatement" -> "ShowExecutionPoint"
      "EditorContextMenus.CodeWindow.QuickActionsForPosition",
      "ReSharper_AltEnter",
      "View.ShowSmartTag" -> "ShowIntentionActions"
      "Window.MovetoNavigationBar" -> "ShowNavBar"
      "ReSharper.ReSharper_FindUsagesAdvanced" -> "ShowSettingsAndFindUsages"
      "ReSharper.ReSharper_GotoUsage" -> "ShowUsages"
      "ReSharper.ReSharper_SilentCleanupCode" -> "SilentCodeCleanup"
      "ReSharper.ReSharper_CompleteCodeSmart" -> "SmartTypeCompletion"
      "Debug.StepInto" -> "StepInto"
      "Debug.StepOut" -> "StepOut"
      "Debug.StepOver" -> "StepOver"
      "Debug.StopDebugging" -> "Stop"
      "ReSharper.ReSharper_SurroundWith" -> "SurroundWithLiveTemplate"
      "Window.NextDocumentWindowNav" -> "Switcher"
      "Edit.ToggleBookmark" -> "ToggleBookmark"
      "ReSharper_BookmarksToggleBookmark0" -> "ToggleBookmark0"
      "ReSharper_BookmarksToggleBookmark1" -> "ToggleBookmark1"
      "ReSharper_BookmarksToggleBookmark2" -> "ToggleBookmark2"
      "ReSharper_BookmarksToggleBookmark3" -> "ToggleBookmark3"
      "ReSharper_BookmarksToggleBookmark4" -> "ToggleBookmark4"
      "ReSharper_BookmarksToggleBookmark5" -> "ToggleBookmark5"
      "ReSharper_BookmarksToggleBookmark6" -> "ToggleBookmark6"
      "ReSharper_BookmarksToggleBookmark7" -> "ToggleBookmark7"
      "ReSharper_BookmarksToggleBookmark8" -> "ToggleBookmark8"
      "ReSharper_BookmarksToggleBookmark9" -> "ToggleBookmark9"
      "Debug.EnableBreakpoint" -> "ToggleBreakpointEnabled"
      "View.FullScreen" -> "ToggleFullScreen"
      "Debug.ToggleBreakpoint" -> "ToggleLineBreakpoint"
      "EditorContextMenus.CodeWindow.ViewTypeHierarchy",
      "ReSharper.ReSharper_TypeHierarchyBrowse" -> "TypeHierarchy"
      "ReSharper.ReSharper_ExploreStackTrace" -> "Unscramble"
      "Edit.RemoveLastCaret" -> "UnselectPreviousOccurrence"
      "Debug.Breakpoints" -> "ViewBreakpoints"
      "Debug.AttachtoProcess",
      "Tools.AttachtoProcess" -> "XDebugger.AttachToProcess"
      "ReSharper_ShowInspectionWindow" -> "ActivateInspectionResultsToolWindow"
      else -> null
    }
  }
}