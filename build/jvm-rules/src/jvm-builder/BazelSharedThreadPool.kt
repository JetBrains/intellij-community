// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.worker

import kotlinx.coroutines.DelicateCoroutinesApi
import org.jetbrains.jps.service.SharedThreadPool
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal object BazelSharedThreadPool : SharedThreadPool() {
  // JPS uses MAX_BUILDER_THREADS as maxThreads - doesn't make sense for standalone, use coroutine dispatcher pool
  override fun createBoundedExecutor(name: String, maxThreads: Int) = throw UnsupportedOperationException()

  override fun createCustomPriorityQueueBoundedExecutor(name: String, maxThreads: Int, comparator: Comparator<in Runnable>): Executor {
    throw UnsupportedOperationException()
  }

  override fun execute(command: Runnable) {
    throw UnsupportedOperationException()
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
  override fun <T> submit(task: Callable<T>): Future<T> = throw UnsupportedOperationException()

  override fun <T> submit(task: Runnable, result: T): Future<T> {
    throw UnsupportedOperationException()
  }

  override fun submit(task: Runnable): Future<*> = throw UnsupportedOperationException()

  override fun <T> invokeAll(tasks: Collection<Callable<T>>): List<Future<T>> {
    throw UnsupportedOperationException()
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