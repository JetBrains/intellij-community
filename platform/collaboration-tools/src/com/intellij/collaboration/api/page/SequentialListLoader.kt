// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.page

/**
 * Loads the list of data in a sequential manner - one batch after the other
 * Processes only one request at a time
 */
interface SequentialListLoader<T> {
  /**
   * Loads the next batch of data
   * If no more data can be retrieved this should return an empty list
   *
   * @return pair of retrieved data and if this loader can load more data
   */
  suspend fun loadNext(): ListBatch<T>

  data class ListBatch<T>(val data: List<T>, val hasMore: Boolean)
}