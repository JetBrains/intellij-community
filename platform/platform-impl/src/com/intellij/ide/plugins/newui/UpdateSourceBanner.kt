// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner

internal class UpdateSourceBanner private constructor(
  text: @NlsContexts.HintText String,
  status: EditorNotificationPanel.Status,
) : InlineBanner(text, status) {
  companion object {

    fun createUnknownPluginUpdateSourceWarning(action: () -> Unit): UpdateSourceBanner {
      val banner = UpdateSourceBanner(IdeBundle.message("plugins.configurable.unknown.update.source.warning.banner"),
                                      EditorNotificationPanel.Status.Warning)
      banner.addAction(IdeBundle.message("plugins.configurable.unknown.update.source.action.text"), action)
      return banner
    }

    fun createSuccessfullyUpdateSourceSetting(): UpdateSourceBanner {
      return UpdateSourceBanner(IdeBundle.message("plugins.configurable.update.source.success.banner"),
                                EditorNotificationPanel.Status.Success)
    }
  }

  init {
    showCloseButton(false)
    isVisible = false
  }
}
