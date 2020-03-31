// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.LowMemoryWatcher

/**
 * @author yole
 */
class TriggerLowMemoryNotificationAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    LowMemoryWatcher.onLowMemorySignalReceived(true)
  }
}
