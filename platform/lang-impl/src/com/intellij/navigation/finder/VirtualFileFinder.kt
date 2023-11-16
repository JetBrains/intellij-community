// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation.finder

import com.intellij.ide.IdeBundle
import com.intellij.navigation.*
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import java.io.File
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocationInFile(val line: Int, val column: Int)

class VirtualFileFinder(
    private val pathKey: NavigationKeyPrefix = NavigationKeyPrefix.PATH,
    private val revisionKey: NavigationKeyPrefix = NavigationKeyPrefix.REVISION,
) {
  private companion object {
    const val FILE_PROTOCOL = "file://"
    const val PATH_GROUP = "path"
    const val LINE_GROUP = "line"
    const val COLUMN_GROUP = "column"
    val PATH_WITH_LOCATION by lazy {
      Pattern.compile(
          "(?<${PATH_GROUP}>[^:]+)(:(?<${LINE_GROUP}>[\\d]+))?(:(?<${COLUMN_GROUP}>[\\d]+))?")
    }
  }

  sealed interface FindResult {
    class Success(val virtualFile: VirtualFile, val locationInFile: LocationInFile) : FindResult

    class Error(val message: String) : FindResult
  }

  suspend fun find(project: Project, parameters: Map<String, String?>): FindResult {
    val pathText =
        parameters.getNavigationKeyValue(pathKey)
            ?: return FindResult.Error(
                IdeBundle.message("jb.protocol.navigate.missing.parameter", pathKey))
    val revision = parameters.getNavigationKeyValue(revisionKey)

    var (path, line, column) = parseNavigationPath(pathText)
    if (path == null) {
      return FindResult.Error(
          IdeBundle.message(
              "jb.protocol.navigate.wrong.path.parameter",
          ))
    }

    val locationInFile = LocationInFile(line?.toInt() ?: 0, column?.toInt() ?: 0)

    path = FileUtil.expandUserHome(path)
    val virtualFile =
        withBackgroundProgress(
            project,
            IdeBundle.message("navigate.command.search.reference.progress.title", pathText)) {
              findFileByStringPath(project, path, revision)
            }
            ?: return FindResult.Error(
                IdeBundle.message("jb.protocol.navigate.problem.virtual.file", path))

    return FindResult.Success(virtualFile, locationInFile)
  }

  private fun parseNavigationPath(pathText: String): Triple<String?, String?, String?> {
    val matcher = PATH_WITH_LOCATION.matcher(pathText)
    return if (!matcher.matches()) Triple(null, null, null)
    else Triple(matcher.group(PATH_GROUP), matcher.group(LINE_GROUP), matcher.group(COLUMN_GROUP))
  }

  private suspend fun findFileByStringPath(
      project: Project,
      path: String,
      revision: String?
  ): VirtualFile? {
    if (FileUtil.isAbsolute(path)) {
      return findFile(project, path, revision)
    }
    val projectPaths = listOfNotNull(project.guessProjectDir()?.path, project.basePath)
    val fileFromProjectPath =
        projectPaths.firstNotNullOfOrNull {
          findFile(project, File(it, path).absolutePath, revision)
        }
    if (fileFromProjectPath != null) {
      return fileFromProjectPath
    }
    val contentRoots = readAction { ProjectRootManager.getInstance(project).contentRoots }
    return contentRoots.firstNotNullOfOrNull { contentRootPath ->
      findFile(project, File(contentRootPath.path, path).absolutePath, revision)
    }
  }

  private suspend fun findFile(
      project: Project,
      absolutePath: String,
      revision: String?
  ): VirtualFile? {
    return withContext(Dispatchers.IO) {
      if (revision != null) {
        val virtualFile =
            JBProtocolRevisionResolver.processResolvers(project, absolutePath, revision)
        if (virtualFile != null) return@withContext virtualFile
      }
      return@withContext VirtualFileManager.getInstance()
          .findFileByUrl(FILE_PROTOCOL + absolutePath)
    }
  }
}
