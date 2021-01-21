// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class UnindexedFileStatus(
  val shouldIndex: Boolean,
  val indexesWereProvidedByInfrastructureExtension: Boolean,
  val timeProcessingUpToDateFiles: Long,
  val timeUpdatingContentLessIndexes: Long,
  val timeIndexingWithoutContent: Long
) {
  val wasFullyIndexedByInfrastructureExtension: Boolean get() = !shouldIndex && indexesWereProvidedByInfrastructureExtension
}