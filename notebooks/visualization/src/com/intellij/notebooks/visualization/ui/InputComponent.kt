package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.UpdateContext

interface InputComponent {
  fun updateInput(ctx: UpdateContext) {}
  fun updateFolding(ctx: UpdateContext, folded: Boolean)
  fun requestCaret()
}