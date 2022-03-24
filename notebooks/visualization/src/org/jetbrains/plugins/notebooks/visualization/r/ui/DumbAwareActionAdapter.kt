/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

abstract class DumbAwareActionAdapter : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    // Nothing to do here
  }
}
