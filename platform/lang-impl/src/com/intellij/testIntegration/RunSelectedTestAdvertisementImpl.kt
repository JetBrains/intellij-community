// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.lang.LangBundle
import com.intellij.openapi.keymap.KeymapUtil.getPrimaryShortcut
import com.intellij.openapi.keymap.KeymapUtil.getShortcutText
import org.jetbrains.annotations.Nls

internal class RunSelectedTestAdvertisementImpl : RunSelectedTestAdvertisement {
  override fun generateAdvertisementText(): @Nls String? {
    val shortcut = getPrimaryShortcut(DefaultRunExecutor.getRunExecutorInstance().getContextActionId())
    if (shortcut != null) {
      return LangBundle.message("popup.advertisement.press.to.run.selected.tests", getShortcutText(shortcut))
    }
    return null
  }
}
