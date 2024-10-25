package com.intellij.notebooks.ui.jupyterToolbar

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class JupyterAdditionalToolbar(  // PY-72283
  actionGroup: ActionGroup,
  target: JComponent,
  place: String = ActionPlaces.EDITOR_INLAY
) : JupyterAbstractAboveCellToolbar(actionGroup, target, place) {

  init {
    background = EditorColorsManager.getInstance().globalScheme.defaultBackground
  }

  override fun getArcSize(): Int = JBUI.scale(8)
  override fun getHorizontalPadding() = JBUI.scale(4)
  override fun getVerticalPadding() = JBUI.scale(1)
}