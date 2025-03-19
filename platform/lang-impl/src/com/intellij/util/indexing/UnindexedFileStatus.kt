// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class UnindexedFileStatus(
  val shouldIndex: Boolean,
  val indexesWereProvidedByInfrastructureExtension: Boolean,
  val timeProcessingUpToDateFiles: Long,
  val timeUpdatingContentLessIndexes: Long,
  val timeIndexingWithoutContentViaInfrastructureExtension: Long,
  val timeTotal: Long
) {
  val wasFullyIndexedByInfrastructureExtension: Boolean get() = !shouldIndex && indexesWereProvidedByInfrastructureExtension
}