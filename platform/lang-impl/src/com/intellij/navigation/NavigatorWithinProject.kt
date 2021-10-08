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
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.StatusBarProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiElement
import com.intellij.util.PsiNavigateUtil
import java.io.File
import java.nio.file.Path
import java.util.regex.Pattern

const val NAVIGATE_COMMAND = "navigate"
const val PROJECT_NAME_KEY = "project"
const val ORIGIN_URL_KEY = "origin"
const val REFERENCE_TARGET = "reference"

const val SELECTION = "selection"

fun openProjectWithAction(parameters: Map<String, String>, action: (Project) -> Unit) {
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
            action(project)
          }
        }, ModalityState.NON_MODAL, project.disposed)
      }
    }
}

data class LocationInFile(val line: Int, val column: Int)
typealias LocationToOffsetConverter = (LocationInFile, Editor) -> Int

class NavigatorWithinProject(val project: Project, val parameters: Map<String, String>, val locationToOffset: LocationToOffsetConverter) {
  companion object {
    private const val FILE_PROTOCOL = "file://"

    private const val PATH_GROUP = "path"
    private const val LINE_GROUP = "line"
    private const val COLUMN_GROUP = "column"
    private const val REVISION = "revision"
    private val PATH_WITH_LOCATION = Pattern.compile("(?<${PATH_GROUP}>[^:]*)(:(?<${LINE_GROUP}>[\\d]+))?(:(?<${COLUMN_GROUP}>[\\d]+))?")

    fun parseNavigationPath(pathText: String): Triple<String?, String?, String?> {
      val matcher = PATH_WITH_LOCATION.matcher(pathText)
      if (!matcher.matches()) {
        return Triple(null, null, null)
      }
      val path: String? = matcher.group(PATH_GROUP)
      val line: String? = matcher.group(LINE_GROUP)
      val column: String? = matcher.group(COLUMN_GROUP)

      return Triple(path, line, column)
    }

    private fun parseLocationInFile(range: String): LocationInFile? {
      val position = range.split(':')
      if (position.size != 2) return null
      try {
        return LocationInFile(position[0].toInt(), position[1].toInt())
      }
      catch (e: Exception) {
        return null
      }
    }
  }

  enum class NavigationKeyPrefix(val prefix: String) {
    FQN("fqn"),
    PATH("path");

    override fun toString() = prefix
  }

  private val navigatorByKeyPrefix = mapOf(
    (NavigationKeyPrefix.FQN to this::navigateByFqn),
    (NavigationKeyPrefix.PATH to this::navigateByPath)
  )

  private val selections by lazy { parseSelections() }

  fun navigate(keysPrefixesToNavigate: List<NavigationKeyPrefix>) {
    keysPrefixesToNavigate.forEach { keyPrefix ->
      parameters.filterKeys { it.startsWith(keyPrefix.prefix) }.values.forEach { navigatorByKeyPrefix[keyPrefix]?.invoke(it) }
    }
  }

  private fun navigateByFqn(reference: String) {
    // handle single reference to method: com.intellij.navigation.JBProtocolNavigateCommand#perform
    // multiple references are encoded and decoded properly
    val fqn = parameters[FRAGMENT_PARAM_NAME]?.let { "$reference#$it" } ?: reference
    runNavigateTask(reference) {

      val dataContext = SimpleDataContext.getProjectContext(project)
      SymbolSearchEverywhereContributor(AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext))
        .search(fqn, ProgressManager.getInstance().progressIndicator ?: StatusBarProgress())
        .filterIsInstance<PsiElement>()
        .forEach {
          ApplicationManager.getApplication().invokeLater {
            PsiNavigateUtil.navigate(it)
            makeSelectionsVisible()
          }
        }
    }
  }

  private fun navigateByPath(pathText: String) {
    var (path, line, column) = parseNavigationPath(pathText)
    if (path == null) {
      return
    }
    val locationInFile = LocationInFile(line?.toInt() ?: 0, column?.toInt() ?: 0)

    path = FileUtil.expandUserHome(path)
    if (!FileUtil.isAbsolute(path)) {
      path = File(project.basePath, path).absolutePath
    }

    runNavigateTask(pathText) {
      val virtualFile = findFile(path, parameters[REVISION])
      if (virtualFile == null) return@runNavigateTask

      ApplicationManager.getApplication().invokeLater {
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
          .filterIsInstance<TextEditor>().first().let { textEditor ->
            performEditorAction(textEditor, locationInFile)
          }
      }
    }
  }

  private fun runNavigateTask(reference: String, task: (indicator: ProgressIndicator) -> Unit) {
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, IdeBundle.message("navigate.command.search.reference.progress.title", reference), true) {
        override fun run(indicator: ProgressIndicator) {
          task.invoke(indicator)
        }

        override fun shouldStartInBackground(): Boolean = !ApplicationManager.getApplication().isUnitTestMode
        override fun isConditionalModal(): Boolean = !ApplicationManager.getApplication().isUnitTestMode
      }
    )
  }

  private fun performEditorAction(textEditor: TextEditor, locationInFile: LocationInFile) {
    val editor = textEditor.editor
    editor.caretModel.removeSecondaryCarets()
    editor.caretModel.moveToOffset(locationToOffset(locationInFile, editor))
    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    editor.selectionModel.removeSelection()
    IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)

    makeSelectionsVisible()
  }

  private fun makeSelectionsVisible() {
    val editor = FileEditorManager.getInstance(project).selectedTextEditor
    selections.forEach {
      editor?.selectionModel?.setSelection(
        locationToOffset(it.first, editor),
        locationToOffset(it.second, editor)
      )
    }
  }

  private fun findFile(absolutePath: String?, revision: String?): VirtualFile? {
    absolutePath ?: return null

    if (revision != null) {
      val virtualFile = JBProtocolRevisionResolver.processResolvers(project, absolutePath, revision)
      if (virtualFile != null) return virtualFile
    }
    return VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL + absolutePath)
  }

  private fun parseSelections(): List<Pair<LocationInFile, LocationInFile>> =
    parameters.filterKeys { it.startsWith(SELECTION) }.values.mapNotNull {
      val split = it.split('-')
      if (split.size != 2) return@mapNotNull null

      val startLocation = parseLocationInFile(split[0])
      val endLocation = parseLocationInFile(split[1])

      if (startLocation != null && endLocation != null) {
        return@mapNotNull Pair(startLocation, startLocation)
      }
      return@mapNotNull null
    }
}
