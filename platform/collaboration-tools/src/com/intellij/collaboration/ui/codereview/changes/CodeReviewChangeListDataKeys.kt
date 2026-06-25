// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.changes

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.actionSystem.DataKey

object CodeReviewChangeListDataKeys {
  val SELECTED_CHANGES: DataKey<List<RefComparisonChange>> =
    DataKey.create("Code.Review.Change.List.Selected.RefComparisonChanges")
}
