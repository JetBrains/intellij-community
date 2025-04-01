// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.worker

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jetbrains.jps.service.SharedThreadPool
import java.util.concurrent.*

internal object BazelSharedThreadPool : SharedThreadPool() {
  private val executor = Dispatchers.Default.asExecutor()

  // JPS uses MAX_BUILDER_THREADS as maxThreads - doesn't make sense for standalone, use coroutine dispatcher pool
  override fun createBoundedExecutor(name: String, maxThreads: Int) = this

  override fun createCustomPriorityQueueBoundedExecutor(name: String, maxThreads: Int, comparator: Comparator<in Runnable>): Executor {
    throw UnsupportedOperationException()
  }

  override fun execute(command: Runnable) {
    executor.execute(command)
  }

  override fun shutdown() {
    throw UnsupportedOperationException()
  }

  override fun shutdownNow(): List<Runnable> {
    throw UnsupportedOperationException()
  }

  override fun isShutdown(): Boolean = false

  override fun isTerminated(): Boolean = false

  override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
    throw UnsupportedOperationException()
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun <T> submit(task: Callable<T>): Future<T> = CompletableFuture.supplyAsync({ task.call() }, executor)

  override fun <T> submit(task: Runnable, result: T): Future<T> {
    return CompletableFuture.supplyAsync({
      task.run()
      result
    }, executor)
  }

  override fun submit(task: Runnable): Future<*> = CompletableFuture.runAsync(task, executor)

  override fun <T> invokeAll(tasks: Collection<Callable<T>>): List<Future<T>> {
    return tasks.map { task ->
      CompletableFuture.supplyAsync({ task.call() }, executor)
    }
  }

  override fun <T> invokeAll(tasks: Collection<Callable<T>>, timeout: Long, unit: TimeUnit): List<Future<T>> {
    throw UnsupportedOperationException()
  }

  override fun <T> invokeAny(tasks: Collection<Callable<T>>): T {
    throw UnsupportedOperationException()
  }

  override fun <T> invokeAny(tasks: Collection<Callable<T>>, timeout: Long, unit: TimeUnit): T? {
    throw UnsupportedOperationException()
  }
}