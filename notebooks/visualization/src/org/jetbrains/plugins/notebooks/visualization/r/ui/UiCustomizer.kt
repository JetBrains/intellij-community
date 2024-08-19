package org.jetbrains.plugins.notebooks.visualization.r.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import java.awt.Color

interface UiCustomizer {
  fun getTextOutputBackground(editor: Editor): Color

  val isResizeOutputToPreviewHeight: Boolean
    get() = true

  companion object {
    val EP_NAME: ExtensionPointName<UiCustomizer> = ExtensionPointName("org.jetbrains.plugins.notebooks.visualization.r.inlays.visualisation.uiCustomizer")

    val instance: UiCustomizer get() = EP_NAME.extensionList.first()
  }
}
