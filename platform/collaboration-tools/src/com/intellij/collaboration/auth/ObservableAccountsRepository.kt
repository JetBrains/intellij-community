// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * The same as the [AccountsRepository] but allows propagating external changes to the [AccountManagerBase]
 */
interface ObservableAccountsRepository<A: Account> : AccountsRepository<A> {

  /**
   * A flow of accounts that is exposed by the [AccountManagerBase]. The implementing class is responsible for updating the current value
   * when the [accounts] are set and, for example, in [com.intellij.openapi.components.PersistentStateComponent.loadState]
   * and [com.intellij.openapi.components.PersistentStateComponent.noStateLoaded].
   */
  val accountsFlow: StateFlow<Set<A>>

}
