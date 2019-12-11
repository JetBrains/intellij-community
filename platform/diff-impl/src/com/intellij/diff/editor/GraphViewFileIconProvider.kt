package com.intellij.diff.editor
import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

class GraphViewFileIconProvider : FileIconProvider {

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    if (file is VCSContentVirtualFile) {
      return AllIcons.Vcs.Branch
    }

    return null
  }

}

