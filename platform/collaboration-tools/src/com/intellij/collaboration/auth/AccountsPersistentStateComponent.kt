// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.openapi.components.PersistentStateComponent

interface AccountsPersistentStateComponent<A: Account, S>: PersistentStateComponent<S> {
  var accounts: Set<A>
}