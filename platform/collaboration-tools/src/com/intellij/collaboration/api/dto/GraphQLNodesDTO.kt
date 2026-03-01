// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.dto

open class GraphQLNodesDTO<out T>(val nodes: List<T> = listOf()) {
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