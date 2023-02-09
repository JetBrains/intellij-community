// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.vfs.VirtualFile

private class DefaultFileStatusManager : FileStatusManager() {
  override fun getStatus(file: VirtualFile): FileStatus = FileStatus.NOT_CHANGED
  override fun getRecursiveStatus(file: VirtualFile): FileStatus = FileStatus.NOT_CHANGED

  override fun fileStatusesChanged() = Unit
  override fun fileStatusChanged(file: VirtualFile?) = Unit
}