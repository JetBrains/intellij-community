// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * IJent implementation should use this thread pool for running every coroutine inside.
 * Moreover, IJent implementation should use neither [kotlinx.coroutines.Dispatchers.Default] nor [kotlinx.coroutines.Dispatchers.IO].
 *
 * The purpose of IJent is not only to provide API for working with remote machines,
 * but also to seamlessly replace very deep internals like [java.nio.file.FileSystems.getDefault] and [java.lang.Process].
 *
 * It's known that the explosive mixture of [kotlinx.coroutines.Dispatchers.Default], [kotlinx.coroutines.runBlocking] and
 * the legacy code in Java sometimes leads to thread starvation, and no absolutely robust and universal method of fixing
 * the thread starvation has been invented yet.
 *
 * IJent replaces internals that by their nature can't suffer from starvation of any thread pool. Therefore, IJent itself with all its
 * internals must be protected from starvation of any thread pool, even [kotlinx.coroutines.Dispatchers.IO].
 * Also, IJent should be protected from possible bugs in various approaches for thread compensation,
 * that can hypothetically affect [com.intellij.util.io.blockingDispatcher].
 *
 * All the threads of the thread pool are not daemonic, and it brings no harm in IntelliJ platform.
 * When IntelliJ decides to exit, it uses [System.exit], which runs shutdown hooks and halts the JVM.
 * When JVM is halted, all threads vanish out, even if they're not daemonic.
 *
 * Also, this is an unlimited thread pool. Since IJent replaces deep internals, it's crucial to never have deadlocks.
 * Choosing between having thousands of threads and deadlocks, the first is better,
 * at least because it can finish at some time and it is easier to investigate and fix.
 */
@ApiStatus.Internal
object IjentThreadPool : ExecutorService by Executors.newCachedThreadPool(IjentThreadFactory) {
  /** To be used when anxious and when want to be 100% sure that some potentially blocking code runs on the right dispatcher. */
  fun checkCurrentThreadIsInPool() {
    val currentThread = Thread.currentThread()
    if (currentThread !in threads) {
      thisLogger().error("Not in IJent thread pool: $currentThread")
    }
  }

  private val threads: MutableSet<Thread> = Collections.newSetFromMap(ContainerUtil.createConcurrentWeakMap())
  private val threadCounter = AtomicInteger()

  override fun shutdown() {
    throw IllegalStateException("Should never be called")
  }

  override fun shutdownNow(): List<Runnable?> {
    throw IllegalStateException("Should never be called")
  }

  private object IjentThreadFactory : ThreadFactory {
    override fun newThread(r: Runnable): Thread {
      val thread = object : Thread() {
        init {
          name = "${IjentThreadPool::class.java.name}-${threadCounter.incrementAndGet()}"
        }

        override fun run() {
          try {
            r.run()
          }
          finally {
            threads.remove(this)
          }
        }
      }

      threads.add(thread)
      return thread
    }
  }
}