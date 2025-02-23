// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.internal

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.indexing.FileBasedIndexTumbler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class InternalIndexingActionsService(private val project: Project, private val scope: CoroutineScope) {
  private val indexesTumbler = FileBasedIndexTumbler("From InternalIndexingActionsService")
  private var indexesOn = true

  fun toggleIndexes() {
    synchronized(indexesTumbler) {
      try {
        if (indexesOn) {
          indexesTumbler.turnOff()
        }
        else {
          indexesTumbler.turnOn()
        }
      }
      finally {
        indexesOn = !indexesOn
      }
    }
  }

  @Suppress("DialogTitleCapitalization")
  fun pauseScanningAndIndexingAndRunEmptyTask() {
    scope.launch(Dispatchers.IO) {
      @Suppress("HardCodedStringLiteral")
      val activityName = "From InternalIndexingActionsService"

      withBackgroundProgress(project, activityName) {
        blockingContext {
          UnindexedFilesScannerExecutor.getInstance(project).suspendScanningAndIndexingThenRun(activityName) {
            while (true) {
              ProgressManager.checkCanceled()
              Thread.sleep(100)
            }
          }
        }
      }
    }
  }
}