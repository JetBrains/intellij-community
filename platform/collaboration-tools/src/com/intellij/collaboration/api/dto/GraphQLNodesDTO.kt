// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.dto

import org.jetbrains.annotations.ApiStatus

open class GraphQLNodesDTO<out T>(val nodes: List<T> = listOf()) {
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Removed totalCount, use constructor without totalCount")
  constructor(nodes: List<T> = listOf(), totalCount: Int? = null) : this(nodes)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GraphQLNodesDTO<*>) return false

    if (nodes != other.nodes) return false

    return true
  }

  override fun hashCode(): Int {
    return nodes.hashCode()
  }
}