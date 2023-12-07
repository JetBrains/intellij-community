// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.concurrency.await
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class ScopeService(
  private val project: Project,
  private val scope: CoroutineScope,
) {

  fun createModel(options: Set<ScopeOption>): AbstractScopeModel =
    if (Registry.`is`("coroutine.scope.model", true))
      CoroutineScopeModel(project, scope, options)
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
