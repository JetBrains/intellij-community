// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.AccountDetails
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.Nls
import java.awt.Image

interface AccountsDetailsLoader<in A : Account, out D : AccountDetails> {

  suspend fun loadDetails(account: A): Result<D>

  suspend fun loadAvatar(account: A, url: String): Image?

  sealed class Result<out D : AccountDetails> {
    class Success<out D : AccountDetails>(val details: D) : Result<D>()
    class Error<out D : AccountDetails>(val error: @Nls String?, val needReLogin: Boolean) : Result<D>()
  }
}