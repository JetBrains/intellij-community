package com.intellij.notebooks.images

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.images.editor.ImageEditor


interface ImageEditorFactory {
  fun createImageEditor(project: Project,
                        file: VirtualFile,
                        graphicsPanel: GraphicsPanel): ImageEditor

  companion object {
    private val EP_NAME: ExtensionPointName<ImageEditorFactory> = ExtensionPointName("com.intellij.notebooks.images.imageEditorFactory")

    val instance: ImageEditorFactory get() = EP_NAME.extensionList.first()
  }
}
