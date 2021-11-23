// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark

import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap


class ManagerState {
  @get:XCollection
  val groups = mutableListOf<GroupState>()
}


class GroupState {
  var name: String = ""
  var isDefault: Boolean = false

  @get:XCollection
  val bookmarks = mutableListOf<BookmarkState>()
}


class BookmarkState {
  var provider: String? = null
  var description: String? = null
  var type: BookmarkType = BookmarkType.DEFAULT

  @get:XMap
  val attributes = mutableMapOf<String, String>()

  override fun toString() = StringBuilder("BookmarkState").apply {
    append(": provider=").append(provider)
    append(", description=").append(description)
    append(", type=").append(type)
    attributes.forEach { append(", ").append(it.key).append("=").append(it.value) }
  }.toString()
}
