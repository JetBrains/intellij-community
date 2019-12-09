package com.intellij.diff.editor

class DiffFileIconProvider : com.intellij.ide.FileIconProvider {

    override fun getIcon(file: com.intellij.openapi.vfs.VirtualFile, flags: Int, project: com.intellij.openapi.project.Project?): javax.swing.Icon? {
        if(file is DiffVirtualFile)
            return com.intellij.icons.AllIcons.Actions.Diff

        return null
    }

}