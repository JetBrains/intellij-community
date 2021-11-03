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
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
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
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiElement
import com.intellij.util.PsiNavigateUtil
import com.intellij.util.text.nullize
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

const val NAVIGATE_COMMAND = "navigate"
const val REFERENCE_TARGET = "reference"
const val PROJECT_NAME_KEY = "project"
const val ORIGIN_URL_KEY = "origin"
const val SELECTION = "selection"

fun openProject(parameters: Map<String, String>): CompletableFuture<Project?> {
  val projectName = parameters[PROJECT_NAME_KEY]?.nullize(nullizeSpaces = true)
  val originUrl = parameters[ORIGIN_URL_KEY]?.nullize(nullizeSpaces = true)
  if (projectName == null && originUrl == null) {
    return CompletableFuture.failedFuture(IllegalArgumentException(IdeBundle.message("jb.protocol.navigate.missing.parameters")))
  }

  val openProject = ProjectUtil.getOpenProjects().find {
    projectName != null && it.name == projectName ||
    originUrl != null && areOriginsEqual(originUrl, getProjectOriginUrl(it.guessProjectDir()?.toNioPath()))
  }
  if (openProject != null) {
    return CompletableFuture.completedFuture(openProject)
  }

  val recentProjectAction = RecentProjectListActionProvider.getInstance().getActions().asSequence()
    .filterIsInstance(ReopenProjectAction::class.java)
    .find {
      projectName != null && it.projectName == projectName ||
      originUrl != null && areOriginsEqual(originUrl, getProjectOriginUrl(Path.of(it.projectPath)))
    }
  if (recentProjectAction == null) {
    return CompletableFuture.completedFuture(null)
  }

  val result = CompletableFuture<Project?>()
  RecentProjectsManagerBase.instanceEx
    .openProject(Path.of(recentProjectAction.projectPath), OpenProjectTask())
    .thenAccept { project ->
      when (project) {
        null -> result.complete(null)
        else -> {
          ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) result.complete(null)
            else {
              StartupManager.getInstance(project).runAfterOpened {
                result.complete(project)
              }
            }
          }, ModalityState.NON_MODAL)
        }
      }
    }
  return result
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
      return if (!matcher.matches()) Triple(null, null, null)
             else Triple(matcher.group(PATH_GROUP), matcher.group(LINE_GROUP), matcher.group(COLUMN_GROUP))
    }

    private fun parseLocationInFile(range: String): LocationInFile? {
      val position = range.split(':')
      return if (position.size != 2) null else try {
        LocationInFile(position[0].toInt(), position[1].toInt())
      }
      catch (e: Exception) {
        null
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
    val fqn = parameters[JBProtocolCommand.FRAGMENT_PARAM_NAME]?.let { "$reference#$it" } ?: reference
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
