// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Helper method to consume accounts from withing an async context
 */
@RequiresEdt
fun <A : Account> AccountManager<A, *>.createAccountsFlow(disposable: Disposable): StateFlow<Set<A>> {
  val flow = MutableStateFlow<Set<A>>(emptySet())
  addListener(disposable, object : AccountsListener<A> {
    override fun onAccountListChanged(old: Collection<A>, new: Collection<A>) {
      flow.value = new.toSet()
    }
  })
  flow.value = accounts
  return flow
}