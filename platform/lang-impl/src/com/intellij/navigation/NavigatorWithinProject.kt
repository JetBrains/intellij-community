// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.navigation

import com.intellij.concurrency.SensitiveProgressWrapper
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
import com.intellij.openapi.application.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiElement
import com.intellij.util.PsiNavigateUtil
import com.intellij.util.containers.ComparatorUtil.max
import com.intellij.util.text.nullize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import java.util.regex.Pattern

const val NAVIGATE_COMMAND: String = "navigate"
const val REFERENCE_TARGET: String = "reference"
const val PROJECT_NAME_KEY: String = "project"
const val ORIGIN_URL_KEY: String = "origin"
const val SELECTION: String = "selection"

suspend fun openProject(parameters: Map<String, String?>): ProtocolOpenProjectResult {
  val projectName = parameters.get(PROJECT_NAME_KEY)?.nullize(nullizeSpaces = true)
  val originUrl = parameters.get(ORIGIN_URL_KEY)?.nullize(nullizeSpaces = true)
  if (projectName == null && originUrl == null) {
    return ProtocolOpenProjectResult.Error(IdeBundle.message("jb.protocol.navigate.missing.parameters"))
  }

  val noProjectResultError = ProtocolOpenProjectResult.Error(IdeBundle.message("jb.protocol.navigate.no.project"))

  val alreadyOpenProject = ProjectUtil.getOpenProjects().find {
    projectName != null && it.name == projectName ||
    originUrl != null && areOriginsEqual(originUrl, getProjectOriginUrl(it.guessProjectDir()?.toNioPath()))
  }
  if (alreadyOpenProject != null) {
    return ProtocolOpenProjectResult.Success(alreadyOpenProject)
  }

  val recentProjectAction = RecentProjectListActionProvider.getInstance().getActions().asSequence()
                              .filterIsInstance(ReopenProjectAction::class.java)
                              .find {
                                projectName != null && it.projectName == projectName ||
                                originUrl != null && areOriginsEqual(originUrl, getProjectOriginUrl(Path.of(it.projectPath)))
                              } ?: return noProjectResultError

  val project = RecentProjectsManagerBase.getInstanceEx().openProject(Path.of(recentProjectAction.projectPath), OpenProjectTask())
                ?: return noProjectResultError
  return withContext(Dispatchers.EDT) {
    if (project.isDisposed) {
      noProjectResultError
    }
    else {
      val future = CompletableDeferred<Project>()
      StartupManager.getInstance(project).runAfterOpened {
        future.complete(project)
      }
      future.join()
      ProtocolOpenProjectResult.Success(project)
    }
  }
}

sealed interface ProtocolOpenProjectResult {
  class Success(val project: Project) : ProtocolOpenProjectResult

  class Error(val message: String) : ProtocolOpenProjectResult
}

data class LocationInFile(val line: Int, val column: Int)
typealias LocationToOffsetConverter = (LocationInFile, Editor) -> Int

private const val FILE_PROTOCOL = "file://"
private const val PATH_GROUP = "path"
private const val LINE_GROUP = "line"
private const val COLUMN_GROUP = "column"
private const val REVISION = "revision"
private val PATH_WITH_LOCATION by lazy {
  Pattern.compile("(?<${PATH_GROUP}>[^:]+)(:(?<${LINE_GROUP}>\\d+))?(:(?<${COLUMN_GROUP}>\\d+))?")
}

private fun parseLocationInFile(range: String): LocationInFile? {
  val position = range.split(':')
  return if (position.size != 2) null
  else try {
    LocationInFile(position[0].toInt(), position[1].toInt())
  }
  catch (e: Exception) {
    null
  }
}

class NavigatorWithinProject(
  val project: Project,
  val parameters: Map<String, String>,
  private val locationToOffset: LocationToOffsetConverter
) {
  companion object {
    fun parseNavigationPath(pathText: String): Triple<String?, String?, String?> {
      val matcher = PATH_WITH_LOCATION.matcher(pathText)
      return if (!matcher.matches()) Triple(null, null, null)
      else Triple(matcher.group(PATH_GROUP), matcher.group(LINE_GROUP), matcher.group(COLUMN_GROUP))
    }
  }

  enum class NavigationKeyPrefix(val prefix: String) {
    FQN("fqn"),
    PATH("path");

    override fun toString(): String = prefix
  }

  private val selections: List<Pair<LocationInFile, LocationInFile>> by lazy {
    parseSelections()
  }

  private fun convertLocationToOffset(locationInFile: LocationInFile, editor: Editor): Int {
    return max(locationToOffset.invoke(locationInFile, editor), 0)
  }

  suspend fun navigate(keysPrefixesToNavigate: List<NavigationKeyPrefix>) {
    for (keyPrefix in keysPrefixesToNavigate) {
      val path = parameters.get(keyPrefix.prefix) ?: continue
      when (keyPrefix) {
        NavigationKeyPrefix.FQN -> navigateByFqn(path)
        NavigationKeyPrefix.PATH -> navigateByPath(path)
      }
    }
  }

  private suspend fun navigateByFqn(reference: String) {
    val fqn = parameters[JBProtocolCommand.FRAGMENT_PARAM_NAME]?.let { "$reference#$it" } ?: reference
    withBackgroundProgress(project, IdeBundle.message("navigate.command.search.reference.progress.title", reference)) {
      val dataContext = SimpleDataContext.getProjectContext(project)
      val searcher = withContext(Dispatchers.EDT) {
        SymbolSearchEverywhereContributor(AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext))
      }
      Disposer.register(project, searcher)
      try {
        val element = withContext(Dispatchers.Default) {
          coroutineToIndicator {
            val wrapperIndicator = SensitiveProgressWrapper(ProgressManager.getGlobalProgressIndicator())
            searcher.search(fqn, wrapperIndicator)
              .asSequence()
              .filterIsInstance<PsiElement>()
              .firstOrNull()
          }
        } ?: return@withBackgroundProgress
        withContext(Dispatchers.EDT) {
          PsiNavigateUtil.navigate(element)
        }
        makeSelectionsVisible()
      }
      finally {
        Disposer.dispose(searcher)
      }
    }
  }

  private suspend fun navigateByPath(pathText: String) {
    var (path, line, column) = parseNavigationPath(pathText)
    if (path == null) {
      return
    }
    val locationInFile = LocationInFile(line?.toInt() ?: 0, column?.toInt() ?: 0)

    path = FileUtil.expandUserHome(path)
    withBackgroundProgress(project, IdeBundle.message("navigate.command.search.reference.progress.title", pathText)) {
      val virtualFile = findFileByStringPath(path) ?: return@withBackgroundProgress
      val textEditor = withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          FileEditorManager.getInstance(project).openFile(virtualFile, true).filterIsInstance<TextEditor>().firstOrNull()
        }
      } ?: return@withBackgroundProgress
      performEditorAction(textEditor, locationInFile)
    }
  }

  private suspend fun findFileByStringPath(path: String): VirtualFile? {
    val revision = parameters[REVISION]
    if (FileUtil.isAbsolute(path)) {
      return findFile(path, revision)
    }
    val projectPaths = listOfNotNull(project.guessProjectDir()?.path, project.basePath)
    val fileFromProjectPath = projectPaths.firstNotNullOfOrNull { findFile(File(it, path).absolutePath, revision) }
    if (fileFromProjectPath != null) {
      return fileFromProjectPath
    }
    val contentRoots = readAction {
      ProjectRootManager.getInstance(project).contentRoots
    }
    return contentRoots.firstNotNullOfOrNull { contentRootPath -> findFile(File(contentRootPath.path, path).absolutePath, revision) }
  }

  private suspend fun performEditorAction(textEditor: TextEditor, locationInFile: LocationInFile) {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val editor = textEditor.editor
        editor.caretModel.removeSecondaryCarets()
        editor.caretModel.moveToOffset(convertLocationToOffset(locationInFile, editor))
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        editor.selectionModel.removeSelection()
        IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
      }
    }
    makeSelectionsVisible()
  }

  private suspend fun makeSelectionsVisible() {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        selections.forEach {
          editor?.selectionModel?.setSelection(
            convertLocationToOffset(it.first, editor),
            convertLocationToOffset(it.second, editor)
          )
        }
      }
    }
  }

  private suspend fun findFile(absolutePath: String, revision: String?): VirtualFile? {
    return withContext(Dispatchers.IO) {
      if (revision != null) {
        val virtualFile = JBProtocolRevisionResolver.processResolvers(project, absolutePath, revision)
        if (virtualFile != null) return@withContext virtualFile
      }
      return@withContext VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL + absolutePath)
    }
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
