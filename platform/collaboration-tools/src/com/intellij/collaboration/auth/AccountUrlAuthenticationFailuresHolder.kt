// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

import com.intellij.concurrency.ConcurrentCollectionFactory
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AccountUrlAuthenticationFailuresHolder<A : Account>(
  private val cs: CoroutineScope,
  private val accountManager: () -> AccountManager<A, *>
) {
  private val storeMap = ConcurrentHashMap<A, MutableSet<String>>()

  init {
    cs.coroutineContext[Job]?.invokeOnCompletion {
      storeMap.clear()
    }
  }

  fun markFailed(account: A, url: String) {
    storeMap.computeIfAbsent(account) {
      cs.launch(CoroutineName("AccountUrlAuthenticationFailuresHolder token change listener")) {
        accountManager().getCredentialsFlow(account).first()
        storeMap.remove(account)
      }
      ConcurrentCollectionFactory.createConcurrentSet()
    }.add(url)
  }

  fun isFailed(account: A, url: String): Boolean = storeMap[account]?.contains(url) ?: false
}