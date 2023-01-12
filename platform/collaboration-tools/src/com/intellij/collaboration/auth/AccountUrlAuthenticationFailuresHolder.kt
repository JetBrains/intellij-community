// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

import com.intellij.openapi.Disposable
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AccountUrlAuthenticationFailuresHolder<A : Account>(
  private val cs: CoroutineScope,
  private val accountManager: () -> AccountManager<A, *>
) : Disposable {
  private val storeMap = ConcurrentHashMap<A, MutableSet<String>>()

  fun markFailed(account: A, url: String) {
    storeMap.computeIfAbsent(account) {
      cs.launch {
        accountManager().getCredentialsFlow(account).first()
        storeMap.remove(account)
      }
      ContainerUtil.newConcurrentSet()
    }.add(url)
  }

  fun isFailed(account: A, url: String): Boolean = storeMap[account]?.contains(url) ?: false

  override fun dispose() {
    storeMap.clear()
  }
}