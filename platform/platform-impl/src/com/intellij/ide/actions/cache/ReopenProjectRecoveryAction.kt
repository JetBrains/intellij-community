// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.cache

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
abstract class ReopenProjectRecoveryAction : RecoveryAction {

  abstract suspend fun performAsync(recoveryScope: RecoveryScope): AsyncRecoveryResult

  override fun canBeApplied(recoveryScope: RecoveryScope): Boolean {
    if (recoveryScope !is ProjectRecoveryScope) return false
    val basePath = recoveryScope.project.basePath ?: return false
    return Files.isDirectory(Path.of(basePath))
  }

  final override fun perform(recoveryScope: RecoveryScope): CompletableFuture<AsyncRecoveryResult> {
    return CoroutineScopeService.coroutineScope().async {
      performAsync(recoveryScope)
    }.asCompletableFuture()
  }

  final override fun performSync(recoveryScope: RecoveryScope): List<CacheInconsistencyProblem> {
    return super.performSync(recoveryScope)
  }

  protected suspend fun closeProject(recoveryScope: RecoveryScope): Path {
    val projectPath = Path.of(recoveryScope.project.basePath!!)
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val projectManager = ProjectManager.getInstance()
        projectManager.closeAndDispose(recoveryScope.project)
      }
    }
    return projectPath
  }

  protected suspend fun openProject(projectPath: Path): RecoveryScope {
    val project = ProjectUtil.openOrImportAsync(
      file = projectPath,
      options = OpenProjectTask {
        runConfigurators = true
        isNewProject = !ProjectUtil.isValidProjectPath(projectPath)
        useDefaultProjectAsTemplate = true
        forceOpenInNewFrame = true
      }
    )!!
    return ProjectRecoveryScope(project)
  }

  @Service(Service.Level.APP)
  private class CoroutineScopeService(val coroutineScope: CoroutineScope) {
    companion object {
      fun coroutineScope(): CoroutineScope {
        return application.service<CoroutineScopeService>().coroutineScope
      }
    }
  }
}