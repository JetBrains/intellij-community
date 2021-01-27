package com.intellij.openapi.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

class DefaultFileStatusManager : FileStatusManager() {
    override fun getStatus(file: VirtualFile): FileStatus = FileStatus.NOT_CHANGED
    override fun getNotChangedDirectoryColor(file: VirtualFile): Color = Color.BLACK

    override fun fileStatusesChanged() = Unit
    override fun fileStatusChanged(file: VirtualFile?) = Unit

    override fun addFileStatusListener(listener: FileStatusListener) = Unit
    override fun addFileStatusListener(listener: FileStatusListener, parentDisposable: Disposable) = Unit
    override fun removeFileStatusListener(listener: FileStatusListener) = Unit
}

