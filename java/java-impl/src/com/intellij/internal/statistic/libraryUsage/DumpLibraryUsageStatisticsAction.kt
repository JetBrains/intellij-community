// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages

class DumpLibraryUsageStatisticsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val statistics = LibraryUsageStatisticsStorageService[project].state.statistics
    val message = statistics.entries.joinToString(prefix = "Registered usages:\n", separator = "\n") { (libraryUsage, count) ->
      "$libraryUsage -> $count"
    }

    Messages.showMessageDialog(project, message, "Library Usage Statistics", null)
  }
}
