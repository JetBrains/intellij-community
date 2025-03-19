// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.Alarm.ThreadToUse
import com.intellij.util.ObjectUtils
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting

/**
 * When you queue a task, the queue waits for [delayMillis] and then runs the task.
 * If a new equal task comes during [delayMillis], the new task is ignored.
 * The tasks are executed in the same order they were queued.
 *
 * The tasks must implement proper equals&hashcode.
 */
@Internal
@VisibleForTesting
class SimpleMergingQueue<T : Runnable>(
  private val name: String,
  private val delayMillis: Int,
  @Volatile private var isActive: Boolean,
  threadToUse: ThreadToUse,
  disposableParent: Disposable
) : Disposable {
  private var taskHolder = LinkedHashSet<T>()
  private val timer = Alarm(threadToUse, this)
  private val lock = ObjectUtils.sentinel("SimpleMergingQueue($name)")

  init {
    Disposer.register(disposableParent, this)
  }

  fun start() {
    synchronized(lock) {
      if (isActive) return
      isActive = true
      if (taskHolder.isNotEmpty()) {
        waitAndRun()
      }
    }
  }

  fun stop() {
    synchronized(lock) {
      isActive = false
      timer.cancelAllRequests()
    }
  }

  fun queue(task: T) {
    queue(listOf(task))
  }

  fun queue(tasks: List<T>) {
    synchronized(lock) {
      val hasTasks = taskHolder.isNotEmpty()
      taskHolder.addAll(tasks)
      if (!hasTasks && isActive) {
        waitAndRun()
      }
    }
  }

  private fun waitAndRun() {
    timer.cancelAllRequests()
    timer.addRequest({ flush() }, delayMillis)
  }

  private fun flush() {
    val tasks: Set<T>
    synchronized(lock) {
      if (!isActive) return
      tasks = taskHolder
      taskHolder = LinkedHashSet()
    }

    for (task in tasks) {
      try {
        task.run()
      }
      catch (e: Throwable) {
        log.error(e)
      }
    }
  }

  override fun toString(): String =
    "SimpleMergingQueue(name='$name', delayMillis=$delayMillis, isActive=$isActive)"

  override fun dispose() {
    synchronized(lock) {
      stop()
      taskHolder = LinkedHashSet()
    }
  }
}

private val log = logger<SimpleMergingQueue<*>>()
