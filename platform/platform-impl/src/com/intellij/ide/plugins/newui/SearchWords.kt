// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
enum class SearchWords(@NonNls val value: String) {
  VENDOR("/vendor:"),
  TAG("/tag:"),
  SORT_BY("/sortBy:"),
  REPOSITORY("/repository:"),
  STAFF_PICKS("/staffPicks"),
  SUGGESTED("/suggested"),
  INTERNAL("/internal");

  companion object {
    @JvmStatic
    fun find(value: String): SearchWords? = entries.find { it.value == value }
  }

}