package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColorUtil
import javax.swing.Icon

class ActionToGutterRendererAdapter(private val action: AnAction, editor: Editor) : GutterIconRenderer(), DumbAware {
  private val icon = action.templatePresentation.icon
    ?.let { IconLoader.getDarkIcon(it, ColorUtil.isDark(editor.colorsScheme.defaultBackground)) }
    ?: error("Action has no assigned icon")

  override fun equals(other: Any?): Boolean {
    return icon == (other as? ActionToGutterRendererAdapter)?.icon
  }

  override fun hashCode(): Int {
    return icon.hashCode()
  }

  override fun getAlignment(): Alignment = Alignment.RIGHT

  override fun getIcon(): Icon = icon

  override fun getClickAction(): AnAction {
    return action
  }

  override fun isNavigateAction(): Boolean = true
}