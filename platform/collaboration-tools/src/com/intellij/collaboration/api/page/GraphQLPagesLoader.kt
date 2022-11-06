// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.page

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.dto.GraphQLPagedResponseDataDTO

class GraphQLPagesLoader<EntityDTO>(
  private val pageLoader: suspend (GraphQLRequestPagination) -> GraphQLPagedResponseDataDTO<EntityDTO>?
) {
  suspend fun loadAll(): List<EntityDTO> {
    val firstResponse = pageLoader(GraphQLRequestPagination.DEFAULT) ?: return emptyList()
    val loadedData = firstResponse.nodes.toMutableList()

    var pageInfo = firstResponse.pageInfo
    while (pageInfo.hasNextPage) {
      val page = GraphQLRequestPagination(pageInfo.endCursor)
      val response = pageLoader(page) ?: break
      loadedData.addAll(response.nodes)
      pageInfo = response.pageInfo
    }

    return loadedData
  }

  companion object {
    fun arguments(pagination: GraphQLRequestPagination): Map<String, Any?> = mapOf(
      "pageSize" to pagination.pageSize,
      "cursor" to pagination.afterCursor
    )
  }
}