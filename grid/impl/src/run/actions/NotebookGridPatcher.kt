package com.intellij.database.run.actions

import com.intellij.openapi.util.Key

interface NotebookGridPatcher {
  fun updateBorders()
  fun updateHeight()
}

@JvmField
val RESULTS_PATCHER: Key<NotebookGridPatcher> = Key.create("RESULTS_PATCHER")
