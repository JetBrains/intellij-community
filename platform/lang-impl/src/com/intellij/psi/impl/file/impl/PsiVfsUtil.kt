// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ExternalChangeActionUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.impl.DebugUtil
import com.intellij.util.application

internal fun clearViewProvider(fileManagerEx: FileManagerEx, vFile: VirtualFile, why: String) {
  DebugUtil.performPsiModification<RuntimeException>(why) {
    fileManagerEx.setViewProvider(vFile, null)
  }
}

internal fun FileManagerEx.getCachedDirectoryNullable(parent: VirtualFile?): PsiDirectory? =
  if (parent == null) null else this.getCachedDirectory(parent)

internal fun runWriteActionWithExternalChange(block: () -> Unit) {
  application.runWriteAction(ExternalChangeActionUtil.externalChangeAction(block))
}
