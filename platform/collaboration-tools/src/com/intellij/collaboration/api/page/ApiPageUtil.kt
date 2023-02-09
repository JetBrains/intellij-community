// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.page

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.dto.GraphQLPagedResponseDataDTO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold

object ApiPageUtil {
  fun <T> createGQLPagesFlow(loader: suspend (GraphQLRequestPagination) -> GraphQLPagedResponseDataDTO<T>?): Flow<List<T>> =
    flow {
      var pagination: GraphQLRequestPagination? = GraphQLRequestPagination.DEFAULT
      while (pagination != null) {
        val response: GraphQLPagedResponseDataDTO<T> = loader(pagination) ?: break
        emit(response.nodes)
        pagination = response.pageInfo.let {
          val endCursor = it.endCursor
          if (it.hasNextPage && endCursor != null) {
            GraphQLRequestPagination(endCursor)
          }
          else {
            null
          }
        }
      }
    }
}

suspend fun <T> Flow<Iterable<T>>.foldToList(): List<T> =
  fold(mutableListOf()) { acc: MutableList<T>, value: Iterable<T> ->
    acc.addAll(value)
    acc
  }