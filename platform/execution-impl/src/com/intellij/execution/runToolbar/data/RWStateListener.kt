// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar.data

internal interface RWStateListener {
  fun stateChanged(state: RWSlotManagerState)
}