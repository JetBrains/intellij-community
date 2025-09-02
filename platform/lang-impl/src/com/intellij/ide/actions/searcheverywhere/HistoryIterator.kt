// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HistoryIterator(
  private val contributorID: String,
  private val list: List<String>,
) {
  private var index = -1

  fun getContributorID(): String {
    return contributorID
  }

  fun getList(): List<String> {
    return list
  }

  fun next(): String {
    if (list.isEmpty()) {
      return ""
    }
    index += 1
    if (index >= list.size) {
      index = 0
    }
    return list[index]
  }

  fun prev(): String {
    if (list.isEmpty()) {
      return ""
    }
    index -= 1
    if (index < 0) {
      index = list.size - 1
    }
    return list[index]
  }
}