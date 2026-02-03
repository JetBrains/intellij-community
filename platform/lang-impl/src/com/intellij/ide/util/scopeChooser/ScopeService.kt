// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.find.FindBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ScopeService(
  private val project: Project,
  val scope: CoroutineScope,
) {

  @TestOnly
  fun waitForAsyncTaskCompletion() {
    val future = scope.future { coroutineContext.job.children.toList().joinAll() }
    // mimic com.intellij.openapi.application.impl.NonBlockingReadActionImpl.waitForAsyncTaskCompletion
    var iteration = 0
    while (iteration++ < 60_000) {
      UIUtil.dispatchAllInvocationEvents()
      try {
        future.get(1, TimeUnit.MILLISECONDS)
      }
      catch (e: TimeoutException) {
        continue
      }
    }
  }

  fun createModel(options: Set<ScopeOption>): AbstractScopeModel =
    if (Registry.`is`("coroutine.scope.model", true))
      CoroutineScopeModel(project, scope.childScope(), options)
    else
      LegacyScopeModel(project, options)

}

private class LegacyScopeModel(
  private val project: Project,
  options: Set<ScopeOption>,
) : AbstractScopeModel {

  private val delegate = ScopeModel(options)
  private val listeners = CopyOnWriteArrayList<ScopeModelListener>()
  private var filter: (ScopeDescriptor) -> Boolean = { true }

  init {
    delegate.init(project)
  }

  override fun dispose() {
    listeners.clear()
  }

  override fun addScopeModelListener(listener: ScopeModelListener) {
    listeners += listener
  }

  override fun removeScopeModelListener(listener: ScopeModelListener) {
    listeners -= listener
  }

  override fun setOption(option: ScopeOption, value: Boolean) {
    delegate.setOption(option, value)
  }

  override fun setFilter(filter: (ScopeDescriptor) -> Boolean) {
    this.filter = filter
  }

  override fun refreshScopes(dataContext: DataContext?) {
    (if (dataContext == null) {
      delegate.getScopeDescriptors(filter)
    }
    else {
      delegate.getScopeDescriptors(dataContext, filter)
    }).onSuccess { scopes ->
      listeners.forEach { it.scopesUpdated(scopes) }
    }
  }

  override suspend fun getScopes(dataContext: DataContext): ScopesSnapshot =
    delegate.getScopeDescriptors(dataContext, filter).await()

  @Deprecated("Slow and blocking, use getScopes() in a suspending context or addScopeModelListener() and updateScopes()")
  override fun getScopesImmediately(dataContext: DataContext): ScopesSnapshot =
    ScopeModel.getScopeDescriptors(project, dataContext, delegate.options, filter)

}

internal fun waitForPromiseWithModalProgress(project: Project, promise: Promise<*>) {
  runWithModalProgressBlocking(project, FindBundle.message("find.usages.loading.search.scopes")) {
    promise.await()
  }
}
