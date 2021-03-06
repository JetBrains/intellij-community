// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.components.service
import com.intellij.util.concurrency.annotations.RequiresEdt

object RuntimeChooserUtil {
  @RequiresEdt
  fun showRuntimeChooserPopup() {
    val model = RuntimeChooserModel()

    model.fetchAvailableJbrs()
    model.fetchCurrentRuntime()

    val result = RuntimeChooserDialog(null, model).showDialogAndGetResult()

    @Suppress("MoveVariableDeclarationIntoWhen")
    return when (result) {
      is RuntimeChooserDialogResult.Cancel -> Unit
      is RuntimeChooserDialogResult.UseDefault -> service<RuntimeChooserPaths>().resetCustomJdk()
      is RuntimeChooserDialogResult.UseCustomJdk -> service<RuntimeChooserPaths>().installCustomJdk(result.name) { result.path }
      is RuntimeChooserDialogResult.DownloadAndUse -> service<RuntimeChooserPaths>().installCustomJdk(result.item.fullPresentationText) { indicator ->
        service<RuntimeChooserDownloader>().downloadAndUse(indicator, result.item, result.path)
      }
    }
  }
}
