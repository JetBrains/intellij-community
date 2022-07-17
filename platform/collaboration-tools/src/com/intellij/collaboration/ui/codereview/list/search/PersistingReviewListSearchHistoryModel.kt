// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

abstract class PersistingReviewListSearchHistoryModel<S : ReviewListSearchValue>(
  private val scope: CoroutineScope,
  private val historySizeLimit: Int = 10
) : ReviewListSearchHistoryModel<S> {

  override fun getHistory(): List<S> = persistentHistory

  protected abstract var persistentHistory: List<S>

  private var delayedHistoryAdditionJob: Job? = null

  override fun add(search: S) {
    persistentHistory = persistentHistory.toMutableList().apply {
      remove(search)
      add(search)
    }.trim(historySizeLimit)
  }

  companion object {
    private fun <E> List<E>.trim(sizeLimit: Int): List<E> {
      val result = this.toMutableList()
      while (result.size > sizeLimit) {
        result.removeFirst()
      }
      return result
    }
  }
}