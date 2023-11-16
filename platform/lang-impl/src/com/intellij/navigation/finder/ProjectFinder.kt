// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation.finder

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.getProjectOriginUrl
import com.intellij.navigation.NavigationKeyPrefix
import com.intellij.navigation.areOriginsEqual
import com.intellij.navigation.getNavigationKeyValue
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.StartupManager
import java.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProjectFinder(
    private val projectKey: NavigationKeyPrefix = NavigationKeyPrefix.PROJECT,
    private val originKey: NavigationKeyPrefix = NavigationKeyPrefix.ORIGIN
) {
  sealed interface FindResult {
    class Success(val project: Project) : FindResult

    class Error(val message: String) : FindResult
  }

  suspend fun find(parameters: Map<String, String?>): FindResult {
    val projectName = parameters.getNavigationKeyValue(projectKey)
    val originUrl = parameters.getNavigationKeyValue(originKey)
    if (projectName == null && originUrl == null) {
      return FindResult.Error(
          IdeBundle.message("jb.protocol.navigate.missing.two.parameters", projectKey, originKey))
    }

    val noProjectResultError =
        FindResult.Error(IdeBundle.message("jb.protocol.navigate.no.project"))

    val alreadyOpenProject =
        ProjectUtil.getOpenProjects().find {
          projectName != null && it.name == projectName ||
              originUrl != null &&
                  areOriginsEqual(originUrl, getProjectOriginUrl(it.guessProjectDir()?.toNioPath()))
        }
    if (alreadyOpenProject != null) {
      return FindResult.Success(alreadyOpenProject)
    }

    val recentProjectAction =
        RecentProjectListActionProvider.getInstance()
            .getActions()
            .asSequence()
            .filterIsInstance(ReopenProjectAction::class.java)
            .find {
              projectName != null && it.projectName == projectName ||
                  originUrl != null &&
                      areOriginsEqual(originUrl, getProjectOriginUrl(Path.of(it.projectPath)))
            } ?: return noProjectResultError

    val project =
        RecentProjectsManagerBase.getInstanceEx()
            .openProject(Path.of(recentProjectAction.projectPath), OpenProjectTask())
            ?: return noProjectResultError
    return withContext(Dispatchers.EDT) {
      if (project.isDisposed) {
        noProjectResultError
      } else {
        val future = CompletableDeferred<Project>()
        StartupManager.getInstance(project).runAfterOpened { future.complete(project) }
        future.join()
        FindResult.Success(project)
      }
    }
  }
}
