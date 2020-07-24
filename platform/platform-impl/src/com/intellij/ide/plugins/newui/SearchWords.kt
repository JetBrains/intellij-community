// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui

enum class SearchWords(val value: String) {
  ORGANIZATION("/organization:"),
  TAG("/tag:"),
  SORT_BY("/sortBy:"),
  REPOSITORY("/repository:");

  companion object {
    @JvmStatic
    fun find(value: String) = values().find { it.value == value }
  }

}