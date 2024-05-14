// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.update

import com.intellij.concurrency.getContextSkeleton
import com.intellij.concurrency.installThreadContext
import com.intellij.util.concurrency.ChildContext
import kotlin.coroutines.CoroutineContext

/**
 * [Some clients][com.intellij.database.autoconfig.DatabaseConfigFileWatcher.queueUpdate]
 * [queue][com.intellij.util.ui.update.MergingUpdateQueue.queue] the same instance multiple times
 * => we have to re-capture context each time.
 */
internal class ContextAwareUpdate(
  private val original: Update,
  private val childContext: ChildContext,
) : Update(original) {

  // we have to delegate ALL overrideable methods because we don't know which ones are overridden in the original Update

  override fun isDisposed(): Boolean = original.isDisposed
  override fun isExpired(): Boolean = original.isExpired
  override fun wasProcessed(): Boolean = original.wasProcessed()
  override fun setProcessed() = original.setProcessed()
  override fun executeInWriteAction(): Boolean = original.executeInWriteAction()
  override fun isRejected(): Boolean = original.isRejected

  private val contextSkeleton: Set<CoroutineContext.Element> = getContextSkeleton(childContext.context)
  private val equalityObjects = arrayOf(*original.equalityObjects, contextSkeleton)

  override fun getEqualityObjects(): Array<Any> = equalityObjects

  override fun canEat(update: Update): Boolean {
    if (update !is ContextAwareUpdate) {
      return false
    }
    return contextSkeleton == update.contextSkeleton &&
           original.canEat(update.original)
  }

  override fun setRejected() {
    original.setRejected()
    childContext.job?.cancel(null)
  }

  override fun run() {
    childContext.runAsCoroutine {
      installThreadContext(childContext.context, true).use { _ ->
        original.run()
      }
    }
  }
}
