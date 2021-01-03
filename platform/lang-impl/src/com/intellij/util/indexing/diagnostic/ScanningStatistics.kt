// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.intellij.util.indexing.UnindexedFileStatus

class ScanningStatistics(val fileSetName: String) {
  var numberOfScannedFiles: Int = 0
  var numberOfFilesForIndexing: Int = 0
  var numberOfFilesFullyIndexedByInfrastructureExtension: Int = 0

  var scanningTime: TimeNano = 0

  var timeProcessingUpToDateFiles: TimeNano = 0
  var timeUpdatingContentLessIndexes: TimeNano = 0
  var timeIndexingWithoutContent: TimeNano = 0

  fun addStatus(unindexedFileStatus: UnindexedFileStatus, statusTime: TimeNano) {
    numberOfScannedFiles++
    if (unindexedFileStatus.shouldIndex) {
      numberOfFilesForIndexing++
    }
    scanningTime += statusTime

    timeProcessingUpToDateFiles += unindexedFileStatus.timeProcessingUpToDateFiles
    timeUpdatingContentLessIndexes += unindexedFileStatus.timeUpdatingContentLessIndexes
    timeIndexingWithoutContent += unindexedFileStatus.timeIndexingWithoutContent

    if (unindexedFileStatus.wasFullyIndexedByInfrastructureExtension) {
      numberOfFilesFullyIndexedByInfrastructureExtension++
    }
  }
}
