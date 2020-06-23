package com.intellij.diff.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

@Deprecated("Unused, to be removed")
class GraphViewFileType private constructor() : FileType {
  override fun getName(): String = "GraphView"
  override fun getDescription(): String = DiffBundle.message("filetype.description.graphview")
  override fun getDefaultExtension(): String = "graph"
  override fun getIcon(): Icon? = AllIcons.Vcs.Branch

  override fun isBinary(): Boolean = true
  override fun isReadOnly(): Boolean = false
  override fun getCharset(file: VirtualFile, content: ByteArray): String? = CharsetToolkit.UTF8

  companion object {
    val INSTANCE: FileType = GraphViewFileType()
  }
}

