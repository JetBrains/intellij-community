package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import javax.swing.Icon

class ActionToGutterRendererAdapter(private val action: AnAction) : GutterIconRenderer() {

  private val icon = action.templatePresentation.icon ?: error("Action has no assigned icon")
  override fun equals(other: Any?): Boolean {
    return icon == (other as? ActionToGutterRendererAdapter)?.icon
  }

  override fun hashCode(): Int {
    return icon.hashCode()
  }

  override fun getIcon(): Icon = icon

  override fun getClickAction(): AnAction {
    return action
  }

}