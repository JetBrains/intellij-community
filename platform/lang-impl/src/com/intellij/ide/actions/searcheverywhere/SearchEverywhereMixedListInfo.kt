// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

class SearchEverywhereMixedListInfo internal constructor(listFactory: SEResultsListFactory) {
  val isMixedList: Boolean
  val contributorPriorities: Map<String, Int>

  init {
    isMixedList = listFactory is MixedListFactory
    contributorPriorities = if (listFactory is MixedListFactory) listFactory.contributorsPriorities else emptyMap()
  }
}