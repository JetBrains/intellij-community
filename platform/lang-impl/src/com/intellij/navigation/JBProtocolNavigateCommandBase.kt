// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.getProjectOriginUrl
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.application.JetBrainsProtocolHandler.FRAGMENT_PARAM_NAME
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.StatusBarProgress
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.util.PsiNavigateUtil
import java.io.File
import java.nio.file.Path
import java.util.regex.Pattern


abstract class JBProtocolNavigateCommandBase(command: String) : JBProtocolCommand(command) {
  companion object {
    const val NAVIGATE_COMMAND = "navigate"
    const val PROJECT_NAME_KEY = "project"
    const val ORIGIN_URL_KEY = "origin"
    const val REFERENCE_TARGET = "reference"
    const val PATH_KEY = "path"
    const val FQN_KEY = "fqn"
    const val SELECTION = "selection"
  }

  /**
   * The handler parses the following 'navigate' command parameters:
   *
   * \\?project=(?<project>[\\w]+)
   *   (&fqn[\\d]*=(?<fqn>[\\w.\\-#]+))*
   *   (&path[\\d]*=(?<path>[\\w-_/\\\\.]+)
   *     (:(?<lineNumber>[\\d]+))?
   *     (:(?<columnNumber>[\\d]+))?)*
   *   (&selection[\\d]*=
   *     (?<line1>[\\d]+):(?<column1>[\\d]+)
   *    -(?<line2>[\\d]+):(?<column2>[\\d]+))*
   */
  fun openProject(parameters: Map<String, String>, action: (Project) -> Unit) {
    val projectName = parameters[PROJECT_NAME_KEY]
    val originUrl = parameters[ORIGIN_URL_KEY]
    if (projectName.isNullOrEmpty() && originUrl.isNullOrEmpty()) {
      return
    }

    val check = { name: String, path: Path? ->
      !projectName.isNullOrEmpty() && name == projectName || areOriginsEqual(originUrl, getProjectOriginUrl(path))
    }

    ProjectUtil.getOpenProjects().find { project -> check.invoke(project.name, project.guessProjectDir()?.toNioPath()) }?.let {
      action(it)
      return
    }

    val actions = RecentProjectListActionProvider.getInstance().getActions()
    val recentProjectAction = actions.asSequence()
                                .filterIsInstance(ReopenProjectAction::class.java)
                                .find { check.invoke(it.projectName, Path.of(it.projectPath)) }
                              ?: return

    RecentProjectsManagerBase.instanceEx.openProject(Path.of(recentProjectAction.projectPath), OpenProjectTask())
      .thenAccept { project ->
        if (project != null) {
          ApplicationManager.getApplication().invokeLater({
                                                            StartupManager.getInstance(project).runAfterOpened {
                                                              DumbService.getInstance(project).runWhenSmart {
                                                                action(project)
                                                              }
                                                            }
                                                          }, ModalityState.NON_MODAL, project.disposed)
        }
      }
  }

  fun findAndNavigateToReference(project: Project, parameters: Map<String, String>) {
    for (it in parameters) {
      if (it.key.startsWith(FQN_KEY)) {
        navigateByFqn(project, parameters, it.value)
      }
    }

    for (it in parameters) {
      if (it.key.startsWith(PATH_KEY)) {
        PathNavigator(project, parameters, it.value).navigate()
      }
    }
  }
}

private const val FILE_PROTOCOL = "file://"

private const val PATH_GROUP = "path"
private const val LINE_GROUP = "line"
private const val COLUMN_GROUP = "column"
private const val REVISION = "revision"
private val PATH_WITH_LOCATION = Pattern.compile("(?<${PATH_GROUP}>[^:]*)(:(?<${LINE_GROUP}>[\\d]+))?(:(?<${COLUMN_GROUP}>[\\d]+))?")

private fun findFile(project: Project, absolutePath: String?, revision: String?): VirtualFile? {
  absolutePath ?: return null

  if (revision != null) {
    val virtualFile = JBProtocolRevisionResolver.processResolvers(project, absolutePath, revision)
    if (virtualFile != null) return virtualFile
  }
  return VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL + absolutePath)
}


private fun runNavigateTask(reference: String, project: Project, task: (indicator: ProgressIndicator) -> Unit) {
  ProgressManager.getInstance().run(
    object : Task.Backgroundable(project, IdeBundle.message("navigate.command.search.reference.progress.title", reference), true) {
      override fun run(indicator: ProgressIndicator) {
        task.invoke(indicator)
      }

      override fun shouldStartInBackground(): Boolean = !ApplicationManager.getApplication().isUnitTestMode
      override fun isConditionalModal(): Boolean = !ApplicationManager.getApplication().isUnitTestMode
    })
}

private fun navigateByFqn(project: Project, parameters: Map<String, String>, reference: String) {
  // handle single reference to method: com.intellij.navigation.JBProtocolNavigateCommand#perform
  // multiple references are encoded and decoded properly
  val fqn = parameters[FRAGMENT_PARAM_NAME]?.let { "$reference#$it" } ?: reference

  runNavigateTask(reference, project) {
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
  return parameters.filterKeys { it.startsWith(JBProtocolNavigateCommandBase.SELECTION) }.values.mapNotNull {
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

class PathNavigator(val project: Project, val parameters: Map<String, String>, val pathText: String) {
  fun navigate() {
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

    runNavigateTask(pathText, project) {
      val virtualFile = findFile(project, path, parameters[REVISION])
      if (virtualFile == null) return@runNavigateTask

      ApplicationManager.getApplication().invokeLater {
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
          .filterIsInstance<TextEditor>().first().let { textEditor ->
            performEditorAction(textEditor, line, column)
          }
      }
    }
  }

  fun performEditorAction(textEditor: TextEditor, line: String?, column: String?) {
    val editor = textEditor.editor
    editor.caretModel.moveToOffset(editor.logicalPositionToOffset(LogicalPosition(line?.toInt() ?: 0, column?.toInt() ?: 0)))
    setSelections(parameters, project)
  }
}