package com.intellij.ide.customize.transferSettings.providers.vsmac.mappings

import com.intellij.openapi.actionSystem.KeyboardShortcut

object KeyBindingsMappings {
  val defaultVSMacKeymap = mapOf<String, List<KeyboardShortcut>>(
    "EditorBackSpace" to listOf(KeyboardShortcut.fromString("pressed BACK_SPACE")),
    "\$Copy" to listOf(KeyboardShortcut.fromString("meta pressed C")),
    "\$Cut" to listOf(KeyboardShortcut.fromString("meta pressed X")),
    "\$Paste" to listOf(KeyboardShortcut.fromString("meta pressed V")),
    "EditorDelete" to listOf(KeyboardShortcut.fromString("meta pressed BACK_SPACE")),
    "RenameElement" to listOf(KeyboardShortcut.fromString("meta pressed R"), KeyboardShortcut.fromString("pressed F2")),
    "\$Undo" to listOf(KeyboardShortcut.fromString("meta pressed Z")),
    "\$Redo" to listOf(KeyboardShortcut.fromString("shift meta pressed Z")),
    "\$SelectAll" to listOf(KeyboardShortcut.fromString("meta pressed A")),
    "CommentByLineComment" to listOf(KeyboardShortcut.fromString("meta pressed SLASH")),
    "EditorIndentSelection" to listOf(KeyboardShortcut.fromString("meta pressed CLOSE_BRACKET")),
    "EditorUnindentSelection" to listOf(KeyboardShortcut.fromString("meta pressed OPEN_BRACKET")),
    "ShowSettings" to listOf(KeyboardShortcut.fromString("meta pressed COMMA")),
    "BuildSolutionAction" to listOf(KeyboardShortcut.fromString("meta pressed B"), KeyboardShortcut.fromString("pressed F6")),
    "RebuildSolutionAction" to listOf(KeyboardShortcut.fromString("ctrl meta pressed B")),
    "Run" to listOf(KeyboardShortcut.fromString("meta alt pressed ENTER"), KeyboardShortcut.fromString("ctrl pressed F5")),
    "Stop" to listOf(KeyboardShortcut.fromString("shift meta pressed ENTER"), KeyboardShortcut.fromString("shift pressed F5")),
    "Debug" to listOf(KeyboardShortcut.fromString("meta pressed ENTER"), KeyboardShortcut.fromString("pressed F5")),
    "RiderOpenSolution" to listOf(KeyboardShortcut.fromString("meta pressed O")),
    "FileChooser.NewFile" to listOf(KeyboardShortcut.fromString("meta pressed N")),
    "SaveDocument" to listOf(KeyboardShortcut.fromString("meta pressed S")),
    "SaveAll" to listOf(KeyboardShortcut.fromString("meta alt pressed S")),
    "RiderNewSolution" to listOf(KeyboardShortcut.fromString("shift meta pressed N")),
    "CloseContent" to listOf(KeyboardShortcut.fromString("meta pressed W")),
    "CloseAllEditors" to listOf(KeyboardShortcut.fromString("shift meta pressed W")),
    "CloseProject" to listOf(KeyboardShortcut.fromString("meta alt pressed W")),
    "Exit" to listOf(KeyboardShortcut.fromString("meta pressed Q")),
    "PinActiveTab" to listOf(KeyboardShortcut.fromString("meta alt pressed P")),
    "ToggleFullScreen" to listOf(KeyboardShortcut.fromString("ctrl meta pressed F")),
    "MoveTabRight" to listOf(KeyboardShortcut.fromString("ctrl meta pressed RIGHT")),
    "EditorIncreaseFontSize" to listOf(KeyboardShortcut.fromString("meta pressed PLUS"),
                                       KeyboardShortcut.fromString("meta pressed EQUALS")),
    "EditorDecreaseFontSize" to listOf(KeyboardShortcut.fromString("meta pressed MINUS"),
                                       KeyboardShortcut.fromString("meta pressed UNDERSCORE")),
    "EditorResetFontSize" to listOf(KeyboardShortcut.fromString("meta pressed 0")),
    "MinimizeCurrentWindow" to listOf(KeyboardShortcut.fromString("meta pressed M")),
    "Find" to listOf(KeyboardShortcut.fromString("meta pressed F")),
    "Replace" to listOf(KeyboardShortcut.fromString("meta alt pressed F")),
    "FindNext" to listOf(KeyboardShortcut.fromString("meta pressed G"), KeyboardShortcut.fromString("pressed F3")),
    "FindPrevious" to listOf(KeyboardShortcut.fromString("shift meta pressed G"), KeyboardShortcut.fromString("shift pressed F3")),
    "ReplaceInPath" to listOf(KeyboardShortcut.fromString("shift meta alt pressed F")),
    "GotoFile" to listOf(KeyboardShortcut.fromString("meta pressed P")),
    "SearchEverywhere" to listOf(KeyboardShortcut.fromString("meta pressed PERIOD")),
    "GotoLine" to listOf(KeyboardShortcut.fromString("meta pressed L")),
    "CodeCompletion" to listOf(KeyboardShortcut.fromString("ctrl pressed SPACE")),
    "EditorDeleteToLineEnd" to listOf(KeyboardShortcut.fromString("ctrl pressed K")),
    "EditorPreviousWord" to listOf(KeyboardShortcut.fromString("alt pressed LEFT")),
    "EditorNextWord" to listOf(KeyboardShortcut.fromString("alt pressed RIGHT")),
    "EditorDeleteToWordStart" to listOf(KeyboardShortcut.fromString("alt pressed BACK_SPACE")),
    "EditorDeleteToWordEnd" to listOf(KeyboardShortcut.fromString("alt pressed DELETE")),
    "EditorDuplicate" to listOf(KeyboardShortcut.fromString("shift meta pressed D")),
    "StepOver" to listOf(KeyboardShortcut.fromString("shift meta pressed O"), KeyboardShortcut.fromString("pressed F10")),
    "StepInto" to listOf(KeyboardShortcut.fromString("shift meta pressed I"), KeyboardShortcut.fromString("meta pressed F11")),
    "StepOut" to listOf(KeyboardShortcut.fromString("shift meta pressed U"), KeyboardShortcut.fromString("shift meta pressed F11")),
    "ViewBreakpoints" to listOf(KeyboardShortcut.fromString("meta alt pressed B")),
    "ToggleLineBreakpoint" to listOf(KeyboardShortcut.fromString("meta pressed BACK_SLASH"), KeyboardShortcut.fromString("pressed F9")),
    "RiderRemoveAllLineBreakpoints" to listOf(KeyboardShortcut.fromString("shift meta pressed F9")),
    "RunToCursor" to listOf(KeyboardShortcut.fromString("meta pressed F10")),
    "GotoDeclaration" to listOf(KeyboardShortcut.fromString("meta pressed D"), KeyboardShortcut.fromString("pressed F12")),
    "GotoImplementation" to listOf(KeyboardShortcut.fromString("meta pressed I")),
    "FindUsages" to listOf(KeyboardShortcut.fromString("shift meta pressed R"), KeyboardShortcut.fromString("shift pressed F12")),
    "ShowIntentionActions" to listOf(KeyboardShortcut.fromString("alt pressed ENTER"))
  )

  fun commandIdMap(commandId: String) = when (commandId) {
    "MonoDevelop.Refactoring.RefactoryCommands.FindReferences" -> "FindUsages"
    "MonoDevelop.Refactoring.RefactoryCommands.GotoDeclaration" -> "GotoDeclaration"
    "MonoDevelop.Debugger.DebugCommands.ClearAllBreakpoints" -> "RiderRemoveAllLineBreakpoints"
    "MonoDevelop.Debugger.DebugCommands.RunToCursor" -> "RunToCursor"
    "MonoDevelop.Debugger.DebugCommands.StepInto" -> "StepInto"
    "MonoDevelop.Debugger.DebugCommands.StepOut" -> "StepOut"
    "MonoDevelop.Debugger.DebugCommands.StepOver" -> "StepOver"
    "MonoDevelop.Debugger.DebugCommands.ToggleBreakpoint" -> "ToggleLineBreakpoint"
    "MonoDevelop.Debugger.DebugCommands.ShowBreakpoints" -> "ViewBreakpoints"
    "MonoDevelop.Ide.Commands.EditCommands.Copy" -> "\$Copy"
    "MonoDevelop.Ide.Commands.EditCommands.Cut" -> "\$Cut"
    "MonoDevelop.Ide.Commands.EditCommands.DeleteKey" -> "EditorBackSpace"
    "MonoDevelop.Ide.Commands.EditCommands.Delete" -> "EditorDelete"
    "MonoDevelop.Ide.Commands.EditCommands.IndentSelection" -> "EditorIndentSelection"
    "MonoDevelop.Ide.Commands.EditCommands.Paste" -> "\$Paste"
    "MonoDevelop.Ide.Commands.EditCommands.MonodevelopPreferences" -> "ShowSettings"
    "MonoDevelop.Ide.Commands.EditCommands.Redo" -> "\$Redo"
    "MonoDevelop.Ide.Commands.EditCommands.Rename" -> "RenameElement"
    "MonoDevelop.Ide.Commands.EditCommands.SelectAll" -> "\$SelectAll"
    "MonoDevelop.Refactoring.RefactoryCommands.GotoImplementation" -> "GotoImplementation"
    "MonoDevelop.Ide.Commands.EditCommands.ToggleCodeComment" -> "CommentByLineComment"
    "MonoDevelop.Ide.Commands.EditCommands.Undo" -> "\$Undo"
    "MonoDevelop.Ide.Commands.EditCommands.UnIndentSelection" -> "EditorUnindentSelection"
    "MonoDevelop.Ide.Commands.FileCommands.CloseAllFiles" -> "CloseAllEditors"
    "MonoDevelop.Ide.Commands.FileCommands.CloseFile" -> "CloseContent"
    "MonoDevelop.Ide.Commands.FileCommands.CloseWorkspace" -> "CloseProject"
    "MonoDevelop.Ide.Commands.FileCommands.NewFile" -> "FileChooser.NewFile"
    "MonoDevelop.Ide.Commands.FileCommands.NewProject" -> "RiderNewSolution"
    "MonoDevelop.Ide.Commands.FileCommands.OpenFile" -> "RiderOpenSolution"
    "MonoDevelop.Ide.Commands.FileCommands.Exit" -> "Exit"
    "MonoDevelop.Ide.Commands.FileCommands.Save" -> "SaveDocument"
    "MonoDevelop.Ide.Commands.FileCommands.SaveAll" -> "SaveAll"
    "MonoDevelop.Ide.Commands.ProjectCommands.BuildSolution" -> "BuildSolutionAction"
    "MonoDevelop.Ide.Commands.ProjectCommands.RebuildSolution" -> "RebuildSolutionAction"
    "MonoDevelop.Debugger.DebugCommands.Debug" -> "Debug"
    "MonoDevelop.Ide.Commands.ProjectCommands.Run" -> "Run"
    "MonoDevelop.Ide.Commands.ProjectCommands.Stop" -> "Stop"
    "MonoDevelop.Refactoring.RefactoryCommands.QuickFix" -> "ShowIntentionActions"
    "MonoDevelop.Ide.Commands.SearchCommands.FindNext" -> "FindNext"
    "MonoDevelop.Ide.Commands.SearchCommands.FindPrevious" -> "FindPrevious"
    "MonoDevelop.Ide.Commands.SearchCommands.Find" -> "Find"
    "MonoDevelop.Ide.Commands.SearchCommands.GotoFile" -> "GotoFile"
    "MonoDevelop.Ide.Commands.SearchCommands.GotoLineNumber" -> "GotoLine"
    "MonoDevelop.Components.MainToolbar.Commands.NavigateTo" -> "SearchEverywhere"
    "MonoDevelop.Ide.Commands.SearchCommands.ReplaceInFiles" -> "ReplaceInPath"
    "MonoDevelop.Ide.Commands.SearchCommands.Replace" -> "Replace"
    "MonoDevelop.Ide.Commands.TextEditorCommands.ShowCompletionWindow" -> "CodeCompletion"
    "MonoDevelop.Ide.Commands.TextEditorCommands.DeleteNextWord" -> "EditorDeleteToWordEnd"
    "MonoDevelop.Ide.Commands.TextEditorCommands.DeletePrevWord" -> "EditorDeleteToWordStart"
    "MonoDevelop.Ide.Commands.TextEditorCommands.DeleteToLineEnd" -> "EditorDeleteToLineEnd"
    "MonoDevelop.Ide.Commands.TextEditorCommands.MoveNextWord" -> "EditorNextWord"
    "MonoDevelop.Ide.Commands.TextEditorCommands.MovePrevWord" -> "EditorPreviousWord"
    "MonoDevelop.Ide.Commands.TextEditorCommands.DuplicateLine" -> "EditorDuplicate"
    "MonoDevelop.Ide.Commands.ViewCommands.FullScreen" -> "ToggleFullScreen"
    "MonoDevelop.Ide.Commands.NavigationCommands.NavigateForward" -> "MoveTabRight"
    "MonoDevelop.Ide.Commands.ViewCommands.ZoomReset" -> "EditorResetFontSize"
    "MonoDevelop.Ide.Commands.ViewCommands.ZoomIn" -> "EditorIncreaseFontSize"
    "MonoDevelop.Ide.Commands.ViewCommands.ZoomOut" -> "EditorDecreaseFontSize"
    "MonoDevelop.MacIntegration.MacIntegrationCommands.MinimizeWindow" -> "MinimizeCurrentWindow"
    "MonoDevelop.Ide.Commands.FileTabCommands.PinTab" -> "PinActiveTab"
    else -> null
  }

  fun shortcutMap(shortcut: String) = when (shortcut) {
    "Shift" -> "shift"
    "Alt" -> "alt"
    "Meta" -> "meta"
    "Control" -> "control"
    "Plus" -> "PLUS"
    "-" -> "MINUS"
    "=" -> "EQUALS"
    "_" -> "UNDERSCORE"
    "BackSpace" -> "BACK_SPACE"
    "," -> "COMMA"
    ";" -> "SEMICOLON"
    ":" -> "COLON"
    "." -> "PERIOD"
    "/" -> "SLASH"
    "\\" -> "BACK_SLASH"
    "*" -> "MULTIPLY"
    "Page_Down" -> "PAGE_DOWN"
    "Prior" -> "PAGE_UP"
    "[" -> "OPEN_BRACKET"
    "]" -> "CLOSE_BRACKET"
    "(" -> "LEFT_PARENTHESIS"
    ")" -> "RIGHT_PARENTHESIS"
    "Up" -> "UP"
    "Down" -> "DOWN"
    "Left" -> "LEFT"
    "Right" -> "RIGHT"
    "'" -> "QUOTE"
    "`" -> "BACK_QUOTE"
    "Return" -> "ENTER"
    "Space" -> "SPACE"
    "Delete" -> "DELETE"
    "Tab" -> "TAB"
    "{" -> "BRACELEFT"
    "}" -> "BRACERIGHT"
    else -> shortcut
  }
}