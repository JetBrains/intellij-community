package org.jetbrains.plugins.notebooks.visualization.ui

import org.jetbrains.plugins.notebooks.visualization.UpdateContext

interface InputComponent: HasGutterIcon {
  fun switchToEditMode(ctx: UpdateContext) {}
  fun switchToCommandMode(ctx: UpdateContext) {}
  fun updateInput(ctx: UpdateContext) {}
  fun updateFolding(ctx: UpdateContext, folded: Boolean)
  fun requestCaret()
}