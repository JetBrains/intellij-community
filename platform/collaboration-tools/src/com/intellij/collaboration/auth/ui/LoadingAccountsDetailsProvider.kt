// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.AccountDetails
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.Nls

interface LoadingAccountsDetailsProvider<A : Account, out D : AccountDetails> : IconsProvider<A> {
  val loadingState: StateFlow<Boolean>
  val loadingCompletionFlow: Flow<A>

  @RequiresEdt
  fun getDetails(account: A): D?

  @RequiresEdt
  fun getErrorText(account: A): @Nls String?

  @RequiresEdt
  fun checkErrorRequiresReLogin(account: A): Boolean
}