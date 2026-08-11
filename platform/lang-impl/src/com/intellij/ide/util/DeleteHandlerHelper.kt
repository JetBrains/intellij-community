// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DeleteHandlerHelper")
package com.intellij.ide.util

import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.IncorrectOperationException
import com.intellij.util.io.TrashBin
import java.util.function.Consumer

internal fun deleteLocalFiles(
  project: Project,
  fileElements: Array<PsiElement>
): Pair<Throwable?, VirtualFile?> = runWithModalProgressBlocking(project, IdeBundle.message("progress.deleting")) {
  reportRawProgress { reporter ->
    reporter.fraction(fraction = null) // indeterminate

    var counter = 0
    var aborted: VirtualFile? = null
    var error: Throwable? = null
    val toBin = TrashBin.isSupported() && GeneralSettings.getInstance().isDeletingToBin

    try {
      for (element in fileElements) {
        reporter.text(IdeBundle.message("progress.already.deleted", counter))

        val file = (element as PsiFileSystemItem).getVirtualFile()
        aborted = file

        if (toBin && TrashBin.canMoveToTrash(file)) {
          LocalFileSystem.MOVE_TO_TRASH.set(file, true)
          counter++
        }
        else {
          LocalFileSystem.DELETE_CALLBACK.set(file, Consumer {
            reporter.text(IdeBundle.message("progress.already.deleted", counter))
            counter++
          })
        }

        try {
          writeAction { element.delete() }
        }
        finally {
          LocalFileSystem.MOVE_TO_TRASH.set(file, null)
          LocalFileSystem.DELETE_CALLBACK.set(file, null)
        }

        aborted = null
      }
    }
    catch (e: IncorrectOperationException) {
      thisLogger().info(e)
      error = e.cause
    }

    Pair(error, aborted)
  }
}
