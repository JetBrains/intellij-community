// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.AccountDetails
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.Nls
import java.awt.Image
import javax.swing.Icon

internal interface AccountsDetailsProvider<in A : Account, out D : AccountDetails> {
  @RequiresEdt
  fun getDetails(account: A): D?

  @RequiresEdt
  @Nls
  fun getErrorText(account: A): String?

  @RequiresEdt
  fun checkErrorRequiresReLogin(account: A): Boolean
}