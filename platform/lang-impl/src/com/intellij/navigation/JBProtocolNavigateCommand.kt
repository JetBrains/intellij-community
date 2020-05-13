// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.application.JetBrainsProtocolHandler.FRAGMENT_PARAM_NAME
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.StatusBarProgress
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.util.PsiNavigateUtil
import java.io.File
import java.nio.file.Paths
import java.util.regex.Pattern

open class JBProtocolNavigateCommand : JBProtocolCommand(NAVIGATE_COMMAND) {
  companion object {
    const val NAVIGATE_COMMAND = "navigate"
    const val PROJECT_NAME_KEY = "project"
    const val REFERENCE_TARGET = "reference"
    const val PATH_KEY = "path"
    const val FQN_KEY = "fqn"
    const val SELECTION = "selection"
  }

  override fun perform(target: String, parameters: Map<String, String>) {
    /**
     * The handler parses the following 'navigate' command parameters:
     *
     * navigate/reference
     * \\?project=(?<project>[\\w]+)
     *   (&fqn[\\d]*=(?<fqn>[\\w.\\-#]+))*
     *   (&path[\\d]*=(?<path>[\\w-_/\\\\.]+)
     *     (:(?<lineNumber>[\\d]+))?
     *     (:(?<columnNumber>[\\d]+))?)*
     *   (&selection[\\d]*=
     *     (?<line1>[\\d]+):(?<column1>[\\d]+)
     *    -(?<line2>[\\d]+):(?<column2>[\\d]+))*
     */

    val projectName = parameters[PROJECT_NAME_KEY]
    if (projectName.isNullOrEmpty()) {
      return
    }

    if (target != REFERENCE_TARGET) {
      LOG.warn("JB navigate action supports only reference target, got $target")
      return
    }

    for (recentProjectAction in RecentProjectListActionProvider.getInstance().getActions()) {
      if (recentProjectAction !is ReopenProjectAction || recentProjectAction.projectName != projectName) {
        continue
      }

      for (project in ProjectUtil.getOpenProjects()) {
        if (project.name == projectName) {
          findAndNavigateToReference(project, parameters)
          return
        }
      }

      ApplicationManager.getApplication().invokeLater(Runnable {
        val project = RecentProjectsManagerBase.instanceEx.openProject(Paths.get(recentProjectAction.projectPath), OpenProjectTask()) ?: return@Runnable
        StartupManager.getInstance(project).runAfterOpened {
          DumbService.getInstance(project).runWhenSmart {
            findAndNavigateToReference(project, parameters)
          }
        }
      }, ModalityState.NON_MODAL)
    }
  }
}

private val LOG = logger<JBProtocolNavigateCommand>()

private const val FILE_PROTOCOL = "file://"

private const val PATH_GROUP = "path"
private const val LINE_GROUP = "line"
private const val COLUMN_GROUP = "column"
private val PATH_WITH_LOCATION = Pattern.compile("(?<${PATH_GROUP}>[^:]*)(:(?<${LINE_GROUP}>[\\d]+))?(:(?<${COLUMN_GROUP}>[\\d]+))?")

private fun findAndNavigateToReference(project: Project, parameters: Map<String, String>) {
  for (it in parameters) {
    if (it.key.startsWith(JBProtocolNavigateCommand.FQN_KEY)) {
      navigateByFqn(project, parameters, it.value)
    }
  }

  for (it in parameters) {
    if (it.key.startsWith(JBProtocolNavigateCommand.PATH_KEY)) {
      navigateByPath(project, parameters, it.value)
    }
  }
}

private fun navigateByPath(project: Project, parameters: Map<String, String>, pathText: String) {
  val matcher = PATH_WITH_LOCATION.matcher(pathText)
  if (!matcher.matches()) {
    return
  }

  var path: String? = matcher.group(PATH_GROUP)
  val line: String? = matcher.group(LINE_GROUP)
  val column: String? = matcher.group(COLUMN_GROUP)

  if (path == null) {
    return
  }

  path = FileUtil.expandUserHome(path)
  if (!FileUtil.isAbsolute(path)) {
    path = File(project.basePath, path).absolutePath
  }

  val virtualFile = VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL + path) ?: return
  FileEditorManager.getInstance(project).openFile(virtualFile, true)
    .filterIsInstance<TextEditor>().first().let { textEditor ->
      val editor = textEditor.editor
      editor.caretModel.moveToOffset(editor.logicalPositionToOffset(LogicalPosition(line?.toInt() ?: 0, column?.toInt() ?: 0)))
      setSelections(parameters, project)
    }
}

private fun navigateByFqn(project: Project, parameters: Map<String, String>, reference: String) {
  // handle single reference to method: com.intellij.navigation.JBProtocolNavigateCommand#perform
  // multiple references are encoded and decoded properly
  val fqn = parameters[FRAGMENT_PARAM_NAME]?.let { "$reference#$it" } ?: reference

  ProgressManager.getInstance().run(
    object : Task.Backgroundable(project, IdeBundle.message("navigate.command.search.reference.progress.title", fqn), true) {
      override fun run(indicator: ProgressIndicator) {
        val dataContext = SimpleDataContext.getProjectContext(project)
        SymbolSearchEverywhereContributor(AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext))
          .search(fqn, ProgressManager.getInstance().progressIndicator ?: StatusBarProgress())
          .filterIsInstance<PsiElement>()
          .forEach {
            ApplicationManager.getApplication().invokeLater {
              PsiNavigateUtil.navigate(it)
              setSelections(parameters, project)
            }
          }
      }

      override fun shouldStartInBackground(): Boolean = !ApplicationManager.getApplication().isUnitTestMode
      override fun isConditionalModal(): Boolean = !ApplicationManager.getApplication().isUnitTestMode
    })
}

private fun setSelections(parameters: Map<String, String>, project: Project) {
  parseSelections(parameters).forEach { selection -> setSelection(project, selection) }
}

private fun setSelection(project: Project, selection: Pair<LogicalPosition, LogicalPosition>) {
  val editor = FileEditorManager.getInstance(project).selectedTextEditor
  editor?.selectionModel?.setSelection(editor.logicalPositionToOffset(selection.first),
                                       editor.logicalPositionToOffset(selection.second))
}

private fun parseSelections(parameters: Map<String, String>): List<Pair<LogicalPosition, LogicalPosition>> {
  return parameters.filterKeys { it.startsWith(JBProtocolNavigateCommand.SELECTION) }.values.mapNotNull {
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
