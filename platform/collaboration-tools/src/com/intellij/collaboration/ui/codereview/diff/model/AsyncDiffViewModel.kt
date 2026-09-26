// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.model

import com.intellij.collaboration.util.ComputedResult
import com.intellij.diff.requests.DiffRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * A viewmodel for a diff between two changes
 * Implementations should provide an equals/hashCode implementation
 */
interface AsyncDiffViewModel {
  val request: StateFlow<ComputedResult<DiffRequest>?>

  fun reloadRequest()
}
