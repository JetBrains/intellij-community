package org.jetbrains.plugins.notebooks.visualization.r.ui

import com.intellij.openapi.editor.Editor
import com.intellij.util.ui.UIUtil

class DefaultUiCustomizer : UiCustomizer {
  override fun getTextOutputBackground(editor: Editor) = UIUtil.getPanelBackground()
}
