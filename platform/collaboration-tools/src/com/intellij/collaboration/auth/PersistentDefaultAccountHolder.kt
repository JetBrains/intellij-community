// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * Stores default account for project
 * To register - [@State(name = SERVICE_NAME_HERE, storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)]
 *
 * @param A - account type
 */
abstract class PersistentDefaultAccountHolder<A : Account>
  : PersistentStateComponent<PersistentDefaultAccountHolder.AccountState>, Disposable, DefaultAccountHolder<A> {

  @Volatile
  override var account: A? = null
    get() =
      if (!accountManager.accountsState.value.contains(field)) null
      else field
    set(value) {
      if (value == null || accountManager.accountsState.value.contains(value)) {
        field = value
      }
    }

  private val accountManager: AccountManager<A, *>
    get() = accountManager()

  protected val project: Project

  constructor(project: Project, @Suppress("unused") cs: CoroutineScope) {
    this.project = project
  }

  @Deprecated("A service coroutine scope should be provided")
  constructor(project: Project) {
    this.project = project
  }

  override fun getState(): AccountState {
    return AccountState().apply {
      defaultAccountId = account?.id
    }
  }

  override fun loadState(state: AccountState) {
    account = state.defaultAccountId?.let { id ->
      accountManager.accountsState.value.find { it.id == id }
    }
  }

  protected abstract fun accountManager(): AccountManager<A, *>

  protected abstract fun notifyDefaultAccountMissing()

  override fun dispose() {}

  class AccountState {
    var defaultAccountId: String? = null
  }
}