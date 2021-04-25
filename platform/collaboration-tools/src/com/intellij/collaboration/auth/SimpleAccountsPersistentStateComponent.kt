// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent

/**
 * If you need to just store accounts extend this, annotate with [com.intellij.openapi.components.State] and register as application service
 */
abstract class SimpleAccountsPersistentStateComponent<A : Account> :
  AccountsPersistentStateComponent<A, SimpleAccountsPersistentStateComponent.AccountsState<A>>,
  SimplePersistentStateComponent<SimpleAccountsPersistentStateComponent.AccountsState<A>>(AccountsState()) {

  class AccountsState<A : Account> : BaseState() {
    var accounts by list<A>()
  }

  override var accounts: Set<A>
    get() = state.accounts.toSet()
    set(value) {
      state.accounts = value.toMutableList()
    }
}