// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

interface ReviewListSearchHistoryModel<S : ReviewListSearchValue> {
  fun getHistory(): List<S>
  fun add(search: S)
}