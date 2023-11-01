// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.ui

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.lvcs.ActivityScope
import com.intellij.platform.lvcs.ActivitySelection

object ActivityViewDataKeys {
  val SELECTION: DataKey<ActivitySelection> = DataKey.create("ActivityView.Selection")
  val SCOPE: DataKey<ActivityScope> = DataKey.create("ActivityView.Scope")
}