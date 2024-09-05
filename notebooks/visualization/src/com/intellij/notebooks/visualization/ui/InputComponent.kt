package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.UpdateContext

interface InputComponent: HasGutterIcon {
  fun switchToEditMode(ctx: UpdateContext) {}
  fun switchToCommandMode(ctx: UpdateContext) {}
  fun updateInput(ctx: UpdateContext) {}
  fun updateFolding(ctx: UpdateContext, folded: Boolean)
  fun requestCaret()
}