// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.outputs

import com.intellij.notebooks.visualization.outputs.statistic.NotebookOutputKeyType
import kotlinx.serialization.Polymorphic

/** Merely a marker for data that can be represented via some Swing component. */
@Polymorphic
interface NotebookOutputDataKey {
  /** Get content that can be used for building diff for outputs. */
  fun getContentForDiffing(): Any

  fun getStatisticKey(): NotebookOutputKeyType = NotebookOutputKeyType.UNKNOWN
}