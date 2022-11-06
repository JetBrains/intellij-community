// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.data

import java.util.*

class GraphQLRequestPagination private constructor(
  val afterCursor: String? = null,
  val since: Date? = null,
  val pageSize: Int = DEFAULT_PAGE_SIZE
) {
  constructor(afterCursor: String? = null, pageSize: Int = DEFAULT_PAGE_SIZE) : this(afterCursor, null, pageSize)

  constructor(since: Date? = null, pageSize: Int = DEFAULT_PAGE_SIZE) : this(null, since, pageSize)

  override fun toString(): String {
    return "afterCursor=$afterCursor&since=$since&per_page=$pageSize"
  }

  companion object {
    private const val DEFAULT_PAGE_SIZE = 100

    val DEFAULT = GraphQLRequestPagination(afterCursor = null)
  }
}