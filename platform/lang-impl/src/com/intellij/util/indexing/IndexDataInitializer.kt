// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.SystemProperties
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.SequentialTaskExecutor
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Future

abstract class IndexDataInitializer<T> : Callable<T?> {
  override fun call(): T? {
    val log = Logger.getInstance(javaClass.name)
    val started = Instant.now()
    return try {
      val tasks = prepareTasks()
      runParallelTasks(tasks)
      val result = finish()
      val message = getInitializationFinishedMessage(result)
      log.info("Index data initialization done: ${Duration.between(started, Instant.now()).toMillis()} ms. " + message)
      result
    }
    catch (t: Throwable) {
      log.error("Index data initialization failed", t)
      throw t
    }
  }

  protected abstract fun getInitializationFinishedMessage(initializationResult: T): String

  protected abstract fun finish(): T

  protected abstract fun prepareTasks(): Collection<ThrowableRunnable<*>>

  @Throws(InterruptedException::class)
  private fun runParallelTasks(tasks: Collection<ThrowableRunnable<*>>) {
    if (tasks.isEmpty()) {
      return
    }
    if (ourDoParallelIndicesInitialization) {
      val taskExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
        "Index Instantiation Pool",
        UnindexedFilesUpdater.getNumberOfIndexingThreads()
      )

      tasks
        .asSequence()
        .map<ThrowableRunnable<*>, Future<*>?> { taskExecutor.submit { executeTask(it) } }
        .forEach {
          try {
            it!!.get()
          }
          catch (e: Exception) {
            LOG.error(e)
          }
        }

      taskExecutor.shutdown()
    }
    else {
      for (callable in tasks) {
        executeTask(callable)
      }
    }
  }

  private fun executeTask(callable: ThrowableRunnable<*>) {
    val app = ApplicationManager.getApplication()
    try {
      // To correctly apply file removals in indices shutdown hook we should process all initialization tasks
      // Todo: make processing removed files more robust because ignoring 'dispose in progress' delays application exit and
      // may cause memory leaks IDEA-183718, IDEA-169374,
      if (app.isDisposed /*|| app.isDisposeInProgress()*/) {
        return
      }
      callable.run()
    }
    catch (t: Throwable) {
      LOG.error(t)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(IndexDataInitializer::class.java)
    private val ourDoParallelIndicesInitialization = SystemProperties.getBooleanProperty("idea.parallel.indices.initialization", true)

    @JvmField
    val ourDoAsyncIndicesInitialization = SystemProperties.getBooleanProperty("idea.async.indices.initialization", true)
    private val ourGenesisExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Index Data Initializer Pool")

    @JvmStatic
    fun <T> submitGenesisTask(action: Callable<T>): Future<T> {
      return ourGenesisExecutor.submit(action)
    }
  }
}