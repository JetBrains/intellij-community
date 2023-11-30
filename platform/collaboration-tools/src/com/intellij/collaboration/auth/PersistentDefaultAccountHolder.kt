// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.collaboration.async.disposingScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

  private val accountManager: AccountManager<A, *>
    get() = accountManager()

  protected val project: Project

  constructor(project: Project, cs: CoroutineScope) {
    this.project = project
    cs.launch {
      accountManager.accountsState.collect {
        if (!it.contains(account)) account = null
      }
    }
  }

  @Deprecated("A service coroutine scope should be provided")
  constructor(project: Project) {
    this.project = project
    disposingScope().launch {
      accountManager.accountsState.collect {
        if (!it.contains(account)) account = null
      }
    }
  }

  override fun getState(): AccountState {
    return AccountState().apply { defaultAccountId = account?.id }
  }

  override fun loadState(state: AccountState) {
    account = state.defaultAccountId?.let { id ->
      accountManager.accountsState.value.find { it.id == id }.also {
        if (it == null) notifyDefaultAccountMissing()
      }
    }
  }

  protected abstract fun accountManager(): AccountManager<A, *>

  protected abstract fun notifyDefaultAccountMissing()

  override fun dispose() {}

  class AccountState {
    var defaultAccountId: String? = null
  }
}