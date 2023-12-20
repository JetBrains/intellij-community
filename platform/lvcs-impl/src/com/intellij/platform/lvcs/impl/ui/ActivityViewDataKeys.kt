// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.ActivitySelection
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object ActivityViewDataKeys {
  val SELECTION: DataKey<ActivitySelection> = DataKey.create("ActivityView.Selection")
  val SCOPE: DataKey<ActivityScope> = DataKey.create("ActivityView.Scope")
}