// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import java.util.*

/**
 * @param A - account type
 */
@JvmDefaultWithCompatibility
interface AccountsListener<A> : EventListener {
  fun onAccountListChanged(old: Collection<A>, new: Collection<A>) {}
  fun onAccountCredentialsChanged(account: A) {}
}