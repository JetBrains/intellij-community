// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

interface ReviewListSearchHistoryModel<S : ReviewListSearchValue> {
  /**
   * Represent last filter selected by user.
   * It can be missed in [getHistory], since not all filters can be added to history
   */
  var lastFilter: S?

  fun getHistory(): List<S>
  fun add(search: S)
}