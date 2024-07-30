package com.intellij.notebooks.images

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.images.editor.ImageEditor
import org.intellij.images.editor.impl.ImageEditorImpl


class DefaultImageEditorFactory : ImageEditorFactory {
  override fun createImageEditor(project: Project, file: VirtualFile, graphicsPanel: GraphicsPanel): ImageEditor =
    ImageEditorImpl(project, file)
}
