// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity

private class LibraryUsageStatisticsStartupActivity : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    if (!LibraryUsageStatisticsProvider.isEnabled) {
      return
    }

    val libraryDescriptorFinder = service<LibraryDescriptorFinderService>().libraryDescriptorFinder() ?: return
    val processedFilesService = ProcessedFilesStorageService.getInstance(project)
    val libraryUsageService = LibraryUsageStatisticsStorageService.getInstance(project)

    val connection = project.messageBus.connect(processedFilesService)
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
                         LibraryUsageStatisticsProvider(project, processedFilesService, libraryUsageService, libraryDescriptorFinder))
  }
}