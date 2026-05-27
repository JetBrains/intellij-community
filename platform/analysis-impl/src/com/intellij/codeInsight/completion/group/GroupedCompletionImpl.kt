// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.group

import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils

internal class GroupedCompletionImpl : GroupedCompletion {
  /**
   * @see com.intellij.codeInsight.completion.command.configuration.AppCommandCompletionSettings.calculateFromRegistry
   */
  override fun isEnabled(): Boolean {
    return (AppMode.isRemoteDevHost() ||
            !AppMode.isHeadless() ||
            (ApplicationManager.getApplication().isUnitTestMode() && Registry.`is`("ide.completion.group.mode.enabled", false))
           ) &&
           PlatformUtils.isIntelliJ() &&
           Registry.`is`("ide.completion.group.enabled", false)
  }
}
