// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic

/**
 * Stores default account for project
 * To register - [@State(name = SERVICE_NAME_HERE, storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)]
 *
 * @param A - account type
 */
abstract class PersistentDefaultAccountHolder<A : Account>(
  protected val project: Project,
  accountsTopic: Topic<AccountsListener<A>>,
  messageBusConnection: MessageBusConnection = ApplicationManager.getApplication().messageBus.connect(project))
  : PersistentStateComponent<PersistentDefaultAccountHolder.AccountState> {

  var account: A? = null

  init {
    messageBusConnection.subscribe(accountsTopic, object : AccountsListener<A> {
      override fun onAccountListChanged(old: Collection<A>, new: Collection<A>) {
        if (!new.contains(account)) account = null
      }
    })
  }

  override fun getState(): AccountState {
    return AccountState().apply { defaultAccountId = account?.id }
  }

  override fun loadState(state: AccountState) {
    account = state.defaultAccountId?.let { id ->
      accountManager().accounts.find { it.id == id }.also {
        if (it == null) notifyDefaultAccountMissing()
      }
    }
  }

  protected abstract fun accountManager(): AccountManager<A, *>

  protected abstract fun notifyDefaultAccountMissing()

  class AccountState {
    var defaultAccountId: String? = null
  }
}