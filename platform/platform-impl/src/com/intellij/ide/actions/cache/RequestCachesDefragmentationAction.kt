// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.cache

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.VFSDefragmentationCheckerStopper
import com.intellij.util.ui.RestartDialogImpl

/**
 * Set VFS flag to run defragmentation on next startup.
 * (Currently, defragmentation is implemented as rebuild from scratch)
 */
internal class RequestCachesDefragmentationAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    //TODO RC: maybe show a confirmation dialog with all the options
    //         'defragment and restart now', 'defragment and restart later', 'cancel'
    //         Messages.showYesNoCancelDialog() ?
    FSRecords.getInstance().scheduleDefragmentation()
    VFSDefragmentationCheckerStopper.stopChecking()

    RestartDialogImpl.restartWithConfirmation()
  }
}