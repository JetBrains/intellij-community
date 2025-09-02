// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.ActivitySelection
import com.intellij.platform.lvcs.impl.DirectoryDiffMode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object ActivityViewDataKeys {
  val SELECTION: DataKey<ActivitySelection> = DataKey.create("ActivityView.Selection")
  val SCOPE: DataKey<ActivityScope> = DataKey.create("ActivityView.Scope")
  val SELECTED_DIFFERENCES: DataKey<Iterable<PresentableChange>> = DataKey.create("ActivityView.SelectedDifferences")
  val DIRECTORY_DIFF_MODE: DataKey<DirectoryDiffMode> = DataKey.create("ActivityView.DirectoryDiffMode")
  internal val ACTIVITY_VIEW_MODEL: DataKey<ActivityViewModel> = DataKey.create("ActivityView.ActivityViewModel")
}
