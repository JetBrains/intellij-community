// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
class RuntimeChooserAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    RuntimeChooserUtil.showRuntimeChooserPopup()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
