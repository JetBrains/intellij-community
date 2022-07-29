// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import kotlinx.coroutines.flow.MutableStateFlow

interface ReviewListSearchPanelViewModel<S : ReviewListSearchValue> {
  val searchState: MutableStateFlow<S>
  val queryState: MutableStateFlow<String?>

  val emptySearch: S
  val defaultSearch: S

  fun getSearchHistory(): List<S>
}