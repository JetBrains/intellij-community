// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages

internal class DumpLibraryUsageStatisticsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val statistics = LibraryUsageStatisticsStorageService.getInstance(project).state.statistics
    val message = statistics.entries.joinToString(prefix = "Registered usages:\n", separator = "\n") { (libraryUsage, count) ->
      "$libraryUsage -> $count"
    }

    Messages.showMessageDialog(project, message, "Library Usage Statistics", null)
  }
}
