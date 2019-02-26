// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.util.gotoByName.*
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
  override fun perform(target: String, parameters: Map<String, String>) {
    // handles URLs of the following types:

    // jetbrains://idea/navigate/reference?project=IDEA&reference=com.intellij.openapi.project.impl.JBProtocolNavigateCommand[.perform][#perform]
    // [&selectionX=25:5-26:6]+

    // jetbrains://idea/navigate/path?project=IDEA&path=com/intellij/openapi/project/impl/JBProtocolNavigateCommand.kt:23:1
    // [&selectionX=25:5-26:6]+

    val projectName = parameters[PROJECT_NAME_KEY]
    if (projectName.isNullOrEmpty()) {
      return
    }

    val model: (Project) -> ChooseByNameModel
    val pattern: String

    when (target) {
      REFERENCE_TARGET -> {
        pattern = (parameters[FQN_KEY] ?: return)
        model = { project -> GotoSymbolModel2(project) }
      }
      FILE_TARGET -> {
        pattern = (parameters[PATH_KEY] ?: return)
        model = { it -> GotoFileModel(it) }
      }
      else -> return
    }

    val recentProjectsActions = RecentProjectsManager.getInstance().getRecentProjectsActions(false)
    for (recentProjectAction in recentProjectsActions) {
      if (recentProjectAction is ReopenProjectAction) {
        if (recentProjectAction.projectName == projectName) {
          val project = ProjectManager.getInstance().openProjects.find { project -> project.name == projectName }
          val selections = parseSelections(parameters)

          if (project != null) {
            navigateToFile(project, model(project), pattern, selections)
          }
          else {
            RecentProjectsManagerBase.getInstanceEx().doOpenProject(recentProjectAction.projectPath, null, false)

            val appConnection = ApplicationManager.getApplication().messageBus.connect()
            appConnection.subscribe(ProjectManager.TOPIC, NavigatableProjectListener(model, pattern, selections, appConnection))
          }
        }
      }
    }
  }

  companion object {
    private const val PROJECT_NAME_KEY = "project"
    private const val SELECTION = "selection"

    private const val REFERENCE_TARGET = "reference"
    private const val FQN_KEY = "fqn"

    private const val FILE_TARGET = "file"
    private const val PATH_KEY = "path"

    private fun navigateToFile(project: Project,
                               gotoModel: ChooseByNameModel,
                               pattern: String,
                               selections: List<Pair<LogicalPosition, LogicalPosition>>) {
      val model = JBProtocolNavigateChooseByNameViewModel(project, gotoModel)
      DefaultChooseByNameItemProvider.filterElements(model, pattern, true, EmptyProgressIndicator(), null) {
        if (it !is NavigationItem) {
          return@filterElements true
        }

        if (it.canNavigate()) {
          IdeFocusManager.getInstance(project).doWhenFocusSettlesDown {
            it.navigate(true)
            selections.forEach { selection -> setSelection(project, selection) }
          }
        }
        true
      }
    }

    private fun setSelection(project: Project, selection: Pair<LogicalPosition, LogicalPosition>) {
      val editor = FileEditorManager.getInstance(project).selectedTextEditor
      editor?.selectionModel?.setSelection(editor.logicalPositionToOffset(selection.first),
                                           editor.logicalPositionToOffset(selection.second))
    }

    private fun parseSelections(parameters: Map<String, String>): List<Pair<LogicalPosition, LogicalPosition>> {
      return parameters.filterKeys { it.startsWith(SELECTION) }.values.mapNotNull {
        val split = it.split('-')
        if (split.size != 2) return@mapNotNull null

        val position1 = parsePosition(split[0])
        val position2 = parsePosition(split[1])

        if (position1 != null && position2 != null) {
          return@mapNotNull Pair(position1, position1)
        }
        return@mapNotNull null
      }
    }

    private fun parsePosition(range: String): LogicalPosition? {
      val position = range.split(':')
      if (position.size != 2) return null
      try {
        return LogicalPosition(Integer.parseInt(position[0]), Integer.parseInt(position[1]))
      }
      catch (e: Exception) {
        return null
      }
    }
  }

  private class NavigatableProjectListener(private val model: (Project) -> ChooseByNameModel,
                                           private val pattern: String,
                                           private val selections: List<Pair<LogicalPosition, LogicalPosition>>,
                                           private val appConnection: MessageBusConnection) : ProjectManagerListener {
    override fun projectOpened(project: Project) {
      navigateToFile(project, model(project), pattern, selections)
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