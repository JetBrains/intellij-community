// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cellsDnD

import com.intellij.notebooks.visualization.ui.EditorCell

sealed class CellDropTarget {
  data class TargetCell(val cell: EditorCell) : CellDropTarget()
  object BelowLastCell : CellDropTarget()
  object NoCell : CellDropTarget()
}
