// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.specialPaths

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

internal class BrowseSpecialPathsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    BrowseSpecialPathsDialog(e.project).show()
  }
}
