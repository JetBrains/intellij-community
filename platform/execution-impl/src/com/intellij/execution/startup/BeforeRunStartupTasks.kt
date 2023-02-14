package com.intellij.execution.startup

import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.CompletionStage

/**
 * An extension point that allows to execute some code or wait for something asynchronously before executing [com.intellij.execution.RunnerAndConfigurationSettings]
 */
interface BeforeRunStartupTasks {

  /**
   * This method will be executed before [com.intellij.execution.RunnerAndConfigurationSettings].
   * */
  @Internal
  suspend fun beforeRun()

  companion object {
    private val EP_NAME = ProjectExtensionPointName<BeforeRunStartupTasks>("com.intellij.beforeRunStartupTasks")

    fun beforeRunAsync(project: Project): CompletionStage<Unit> {
      return project.coroutineScope.async {
        val logger = this@Companion.thisLogger()
        EP_NAME.getExtensions(project).forEach {
          logger.runAndLogException {
            it.beforeRun()
          }
        }
      }.asCompletableFuture()
    }
  }
}