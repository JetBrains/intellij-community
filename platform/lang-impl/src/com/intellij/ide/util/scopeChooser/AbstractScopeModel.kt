// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.popup.ListSeparator

interface AbstractScopeModel {

  fun addScopeModelListener(listener: ScopeModelListener)
  fun removeScopeModelListener(listener: ScopeModelListener)

  fun setOption(option: ScopeOption, value: Boolean)
  fun setFilter(filter: (ScopeDescriptor) -> Boolean)

  fun refreshScopes(dataContext: DataContext? = null)

  suspend fun getScopes(dataContext: DataContext): ScopesSnapshot

  @Deprecated("Slow and blocking, use getScopes() in a suspending context, or addScopeModelListener() and refreshScopes()")
  fun getScopesImmediately(dataContext: DataContext): ScopesSnapshot

}

interface ScopesSnapshot {
  val scopeDescriptors: List<ScopeDescriptor>
  fun getSeparatorFor(scopeDescriptor: ScopeDescriptor): ListSeparator?
}

interface ScopeModelListener {
  fun scopesUpdated(scopes: ScopesSnapshot)
}
