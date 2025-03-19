// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.ThrowableRunnable
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Future

@ApiStatus.Internal
abstract class IndexDataInitializer<T>(private val name: String) : Callable<T?> {
  override fun call(): T? {
    val log = thisLogger()
    val started = Instant.now()
    try {
      val tasks = prepareTasks()
      val activity = StartUpMeasurer.startActivity("$name storages initialization")
      runParallelTasks(tasks = tasks, checkAppDisposed = true)
      val result = finish()
      val message = getInitializationFinishedMessage(result)
      activity.end()
      log.info("Index data initialization done: ${Duration.between(started, Instant.now()).toMillis()} ms. " + message)
      return result
    }
    catch (t: Throwable) {
      when (t) {
        is AlreadyDisposedException, is ProcessCanceledException -> {
          log.warn("Index data initialization cancelled", t)
          throw t
        }
        else -> {
          val e = IllegalStateException("Index data initialization failed", t)
          log.error(e)
          throw e
        }
      }
    }
  }

  protected abstract fun getInitializationFinishedMessage(initializationResult: T): String

  protected abstract fun finish(): T

  protected abstract fun prepareTasks(): Collection<ThrowableRunnable<*>>

  companion object {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    @JvmStatic
    fun <T> submitGenesisTask(action: Callable<T>): Future<T> {
      return scope.submitGenesisTask(action)
    }

    fun <T> submitGenesisTask(action: suspend () -> T): Deferred<T> {
      return scope.async(dispatcher) { action() }
    }

    @JvmStatic
    fun <T> CoroutineScope.submitGenesisTask(action: Callable<T>): Future<T> {
      return async(dispatcher) { action.call() }.asCompletableFuture()
    }

    @JvmStatic
    fun runParallelTasks(tasks: Collection<ThrowableRunnable<*>>, checkAppDisposed: Boolean) {
      if (tasks.isEmpty()) {
        return
      }

      runBlocking(Dispatchers.IO.limitedParallelism(UnindexedFilesUpdater.getNumberOfIndexingThreads()) +
                  CoroutineName("Index Storage Lifecycle")) {
        for (task in tasks) {
          launch {
            executeTask(task, checkAppDisposed)
          }
        }
      }
    }

    private fun executeTask(callable: ThrowableRunnable<*>, checkAppDisposed: Boolean) {
      val app = ApplicationManager.getApplication()
      try {
        // To correctly apply file removals in indices shutdown hook we should process all initialization tasks
        // Todo: make processing removed files more robust because ignoring 'dispose in progress' delays application exit and
        // may cause memory leaks IDEA-183718, IDEA-169374,
        if (checkAppDisposed && (app.isDisposed /*|| app.isDisposeInProgress()*/)) {
          return
        }
        callable.run()
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (t: Throwable) {
        logger<IndexDataInitializer<*>>().error(t)
      }
    }
  }
}