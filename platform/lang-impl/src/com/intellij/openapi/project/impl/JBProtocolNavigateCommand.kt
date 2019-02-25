// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.messages.MessageBusConnection

class JBProtocolNavigateCommand : JBProtocolCommand("navigate") {
  override fun perform(vcsId: String, parameters: Map<String, String>) {
    // handles the path of types:
    // jetbrains://idea/navigate?recent.project.name=IDEA&reference=com.intellij.openapi.project.impl.JBProtocolNavigateCommand.perform&selection=25:5

    val recentProjectName = parameters[RECENT_PROJECT_NAME_KEY]
    val referenceParameter = parameters[REFERENCE_KEY]
    val selection = parameters[SELECTION]

    if (recentProjectName.isNullOrEmpty() || referenceParameter.isNullOrEmpty()) {
      return
    }

    val recentProjectsActions = RecentProjectsManager.getInstance().getRecentProjectsActions(false)
    for (recentProjectAction in recentProjectsActions) {
      if (recentProjectAction is ReopenProjectAction) {
        if (recentProjectAction.projectName == recentProjectName) {
          val project = ProjectManager.getInstance().openProjects.find { project -> project.name == recentProjectName }

          if (project != null) {
            navigateToFile(project, referenceParameter, selection)
          }
          else {
            RecentProjectsManagerBase.getInstanceEx().doOpenProject(recentProjectAction.projectPath, null, false)

            val appConnection = ApplicationManager.getApplication().messageBus.connect()
            appConnection.subscribe(ProjectManager.TOPIC, NavigatableProjectListener(referenceParameter, selection, appConnection))
          }
        }
      }
    }
  }

  companion object {
    private const val RECENT_PROJECT_NAME_KEY = "recent.project.name"
    private const val REFERENCE_KEY = "reference"
    private const val SELECTION = "selection"

    private fun parseLineNumber(path: String): Pair<String, Int> {
      val numberString = path.substringAfterLast(':')
      var pathString = path
      var number = 0
      if (numberString.isNotBlank()) {
        number = Integer.parseInt(numberString)
        pathString = path.substringBeforeLast(':')
      }
      return Pair(pathString, number)
    }


    private fun navigateToFile(project: Project, referenceString: String, selectionParameter: String?) {
      var selection: Pair<LogicalPosition, LogicalPosition>? = null
      if (selectionParameter != null) {
        selection = parseSelection(referenceString, selectionParameter)
      }

      val model = JBProtocolNavigateChooseByNameViewModel(project, GotoSymbolModel2(project))
      DefaultChooseByNameItemProvider.filterElements(model, referenceString, true, EmptyProgressIndicator(), null) {
        if (it !is NavigationItem) {
          return@filterElements true
        }

        it.navigate(true)
        IdeFocusManager.getInstance(project)
          .doWhenFocusSettlesDown {
            val selectedTextEditor = FileEditorManager.getInstance(project).selectedTextEditor
            if (selectedTextEditor != null && selection != null) {
              val offset = selectedTextEditor.logicalPositionToOffset(selection.first)
              val offset1 = selectedTextEditor.logicalPositionToOffset(selection.second)
              selectedTextEditor.selectionModel.setSelection(offset, offset1)
            }
          }
        true
      }
    }

    private fun parseSelection(referenceParameter: String, selectionParameter: String): Pair<LogicalPosition, LogicalPosition> {
      var selection = selectionParameter
      var reference = referenceParameter

      var linesPair = parseLineNumber(reference)
      val lineNumber = linesPair.second
      reference = linesPair.first

      linesPair = parseLineNumber(reference)
      val columnNumber = linesPair.second

      var selectionPair = parseLineNumber(selection)
      val selectionLineNumber = selectionPair.second
      selection = selectionPair.first

      selectionPair = parseLineNumber(selection)
      val selectionColumnNumber = selectionPair.second

      return Pair(LogicalPosition(lineNumber, columnNumber), LogicalPosition(selectionLineNumber, selectionColumnNumber))
    }
  }

  class NavigatableProjectListener(var path: String,
                                   var selection: String?,
                                   private val appConnection: MessageBusConnection) : ProjectManagerListener {
    override fun projectOpened(project: Project) {
      navigateToFile(project, path, selection)
      appConnection.disconnect()
    }
  }

  //todo duplicates
  private class JBProtocolNavigateChooseByNameViewModel(private val project: Project,
                                                        private val model: ChooseByNameModel) : ChooseByNameViewModel {
    override fun canShowListForEmptyPattern() = false

    override fun isSearchInAnyPlace(): Boolean = false

    override fun transformPattern(pattern: String): String = pattern

    override fun getProject(): Project = project

    override fun getModel(): ChooseByNameModel = model

    override fun getMaximumListSizeLimit() = Int.MAX_VALUE
  }
}