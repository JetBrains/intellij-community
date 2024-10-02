// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.popup.ListSeparator
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AbstractScopeModel : Disposable {

  fun addScopeModelListener(listener: ScopeModelListener)
  fun removeScopeModelListener(listener: ScopeModelListener)

  fun setOption(option: ScopeOption, value: Boolean)
  fun setFilter(filter: (ScopeDescriptor) -> Boolean)

  fun refreshScopes(dataContext: DataContext? = null)

  suspend fun getScopes(dataContext: DataContext): ScopesSnapshot

  @Deprecated("Slow and blocking, use getScopes() in a suspending context, or addScopeModelListener() and refreshScopes()")
  fun getScopesImmediately(dataContext: DataContext): ScopesSnapshot

}

@ApiStatus.Internal
interface ScopesSnapshot {
  val scopeDescriptors: List<ScopeDescriptor>
  fun getSeparatorFor(scopeDescriptor: ScopeDescriptor): ListSeparator?
}

@ApiStatus.Internal
interface ScopeModelListener {
  fun scopesUpdated(scopes: ScopesSnapshot)
}
