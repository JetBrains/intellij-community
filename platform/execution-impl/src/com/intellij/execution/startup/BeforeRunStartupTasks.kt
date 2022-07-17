package com.intellij.execution.startup

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor

interface BeforeRunStartupTasks {

  fun beforeRunAsync(): CompletionStage<Unit>


  companion object {
    private val EP_NAME = ProjectExtensionPointName<BeforeRunStartupTasks>("com.intellij.beforeRunStartupTasks")

    fun beforeRunAsync(project: Project): CompletionStage<Unit> {
      var result: CompletableFuture<Unit>? = null
      val executor = AppExecutorUtil.getAppExecutorService()
      EP_NAME.getExtensions(project).forEach {
        val prev = result

        result = if (prev == null) {
          it.toSafeFuture(executor)
        }
        else {
          prev.thenCompose { _ -> it.toSafeFuture(executor) }
        }
      }

      return result ?: CompletableFuture.completedFuture(Unit)
    }

    private fun BeforeRunStartupTasks.toSafeFuture(executor: Executor) = CompletableFuture<Unit>().also { res ->

      fun logError(t: Throwable) = thisLogger().error(t)

      try {
        beforeRunAsync().whenCompleteAsync({ _, throwable ->
          res.complete(Unit)

          if (throwable != null)
            logError(throwable)
        }, executor)
      }
      catch (throwable: Throwable) {
        res.complete(Unit)
        logError(throwable)
      }
    }
  }
}