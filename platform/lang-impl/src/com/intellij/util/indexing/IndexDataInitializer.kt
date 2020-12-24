// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.SystemProperties
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.SequentialTaskExecutor
import java.lang.InterruptedException
import java.lang.Exception
import java.time.Duration
import java.time.Instant
import java.util.ArrayList
import java.util.concurrent.Callable
import java.util.concurrent.Future

abstract class IndexDataInitializer<T> : Callable<T?> {
  private val myNestedInitializationTasks: MutableList<ThrowableRunnable<*>> = ArrayList()

  override fun call(): T? {
    val started = Instant.now()
    return try {
      val tasks = prepareTasks()
      runParallelTasks(tasks)
      finish()
    }
    finally {
      Logger.getInstance(javaClass.name).info("Index data initialization done: ${Duration.between(started, Instant.now()).toMillis()} ms")
    }
  }

  protected abstract fun finish(): T?

  protected abstract fun prepareTasks(): Collection<ThrowableRunnable<*>>

  protected fun addNestedInitializationTask(nestedInitializationTask: ThrowableRunnable<*>) {
    myNestedInitializationTasks.add(nestedInitializationTask)
  }

  @Throws(InterruptedException::class)
  private fun runParallelTasks(tasks: Collection<ThrowableRunnable<*>>) {
    if (tasks.isEmpty()) return
    if (ourDoParallelIndicesInitialization) {
      val taskExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
        "Index Instantiation Pool",
        AppExecutorUtil.getAppExecutorService(),
        UnindexedFilesUpdater.getNumberOfIndexingThreads())

      tasks
        .map<ThrowableRunnable<*>, Future<*>?> { taskExecutor.submit { executeTask(it)} }
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
      for (callable in myNestedInitializationTasks) {
        executeTask(callable)
      }
    }
  }

  private fun executeTask(callable: ThrowableRunnable<*>) {
    val app = ApplicationManager.getApplication()
    try {
      // To correctly apply file removals in indices's shutdown hook we should process all initialization tasks
      // Todo: make processing removed files more robust because ignoring 'dispose in progress' delays application exit and
      // may cause memory leaks IDEA-183718, IDEA-169374,
      if (app.isDisposed /*|| app.isDisposeInProgress()*/) return
      callable.run()
    }
    catch (t: Throwable) {
      LOG.error(t)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(IndexDataInitializer::class.java)
    private val ourDoParallelIndicesInitialization = SystemProperties
      .getBooleanProperty("idea.parallel.indices.initialization", true)
    @JvmField
    val ourDoAsyncIndicesInitialization = SystemProperties.getBooleanProperty("idea.async.indices.initialization", true)
    private val ourGenesisExecutor = SequentialTaskExecutor
      .createSequentialApplicationPoolExecutor("Index Data Initializer Pool")

    @JvmStatic
    fun <T> submitGenesisTask(action: Callable<T>): Future<T> {
      return ourGenesisExecutor.submit(action)
    }
  }
}