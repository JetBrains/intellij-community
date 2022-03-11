// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

private class LibraryUsageStatisticsStartupActivity : StartupActivity.Background {
  override fun runActivity(project: Project) {
    if (!LibraryUsageStatisticsProvider.isEnabled) {
      return
    }

    val libraryDescriptorFinder = service<LibraryDescriptorFinderService>().libraryDescriptorFinder() ?: return
    val processedFilesService = ProcessedFilesStorageService.getInstance(project)
    val libraryUsageService = LibraryUsageStatisticsStorageService.getInstance(project)
    project.messageBus.connect(processedFilesService).subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      LibraryUsageStatisticsProvider(project, processedFilesService, libraryUsageService, libraryDescriptorFinder),
    )
  }
}