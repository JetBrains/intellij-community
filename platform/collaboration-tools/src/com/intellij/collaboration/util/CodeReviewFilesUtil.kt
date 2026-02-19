// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus

object CodeReviewFilesUtil {
  /**
   * A utility function to close a set of files programmatically
   * Without this [FileEditorManager.closeFile] will throw an exception
   */
  @ApiStatus.Experimental
  @RequiresEdt
  @RequiresWriteLock
  fun closeFilesSafely(manager: FileEditorManager, files: Collection<VirtualFile>) {
    // otherwise the exception is thrown when removing an editor tab
    (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
      for (file in files) {
        manager.closeFile(file)
      }
    }
  }
}