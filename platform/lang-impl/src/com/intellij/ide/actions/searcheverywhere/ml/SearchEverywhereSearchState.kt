// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchRestartReason

internal data class SearchEverywhereSearchState(
  var startTime: Long,
  var searchStartReason: SearchRestartReason,
  var tabId: String,
  var keysTyped: Int,
  var backspacesTyped: Int,
  var queryLength: Int
)