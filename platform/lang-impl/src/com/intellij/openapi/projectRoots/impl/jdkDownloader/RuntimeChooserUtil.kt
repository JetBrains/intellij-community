// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.components.service
import com.intellij.util.concurrency.annotations.RequiresEdt

object RuntimeChooserUtil {
  @RequiresEdt
  fun showRuntimeChooserPopup() {
    val model = RuntimeChooserModel()

    model.fetchAvailableJbrs()
    model.fetchCurrentRuntime()

    return when (val result = RuntimeChooserDialog(null, model).showDialogAndGetResult()) {
      is RuntimeChooserDialogResult.Cancel -> Unit
      is RuntimeChooserDialogResult.UseDefault -> service<RuntimeChooserPaths>().resetCustomJdk()
      is RuntimeChooserDialogResult.UseCustomJdk -> service<RuntimeChooserPaths>().installCustomJdk(result.name) { result.path }
      is RuntimeChooserDialogResult.DownloadAndUse -> service<RuntimeChooserPaths>().installCustomJdk(result.item.fullPresentationText) { indicator ->
        service<RuntimeChooserDownloader>().downloadAndUse(indicator, result.item, result.path)
      }
    }
  }
}
