// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service


class RuntimeChooserAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val model = RuntimeChooserModel()

    model.fetchAvailableJbrs()
    model.fetchCurrentRuntime()

    val result = RuntimeChooserDialog(null, model).showDialogAndGetResult()

    @Suppress("MoveVariableDeclarationIntoWhen")
    return when(result) {
      is RuntimeChooserDialogResult.Cancel -> Unit
      is RuntimeChooserDialogResult.UseDefault -> service<RuntimeChooserPaths>().resetCustomJdk()
      is RuntimeChooserDialogResult.DownloadAndUse -> service<RuntimeChooserDownloader>().downloadAndUse(result.item, result.path)
    }
  }
}
