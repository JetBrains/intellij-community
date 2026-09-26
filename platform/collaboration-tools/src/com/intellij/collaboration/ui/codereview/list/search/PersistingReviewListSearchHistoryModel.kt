// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

abstract class PersistingReviewListSearchHistoryModel<S : ReviewListSearchValue>(
  private val historySizeLimit: Int = 10
) : ReviewListSearchHistoryModel<S> {

  override fun getHistory(): List<S> = persistentHistory

  protected abstract var persistentHistory: List<S>

  override fun add(search: S) {
    persistentHistory = persistentHistory.toMutableList().apply {
      remove(search)
      add(search)
    }.takeLast(historySizeLimit)
  }
}