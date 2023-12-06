// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.page

import com.intellij.collaboration.api.data.GraphQLRequestPagination
import com.intellij.collaboration.api.dto.GraphQLPagedResponseDataDTO
import com.intellij.collaboration.api.util.LinkHttpHeaderValue
import com.intellij.collaboration.util.URIUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold
import java.net.URI
import java.net.http.HttpResponse

object ApiPageUtil {
  fun <T> createGQLPagesFlow(reversed: Boolean = false,
                             loader: suspend (GraphQLRequestPagination) -> GraphQLPagedResponseDataDTO<T>?): Flow<GraphQLPagedResponseDataDTO<T>> =
    flow {
      var pagination: GraphQLRequestPagination? = GraphQLRequestPagination.DEFAULT
      while (pagination != null) {
        val response: GraphQLPagedResponseDataDTO<T> = loader(pagination) ?: break
        emit(response)
        pagination = response.pageInfo.let {
          if (!reversed) {
            val endCursor = it.endCursor
            if (it.hasNextPage && endCursor != null) {
              GraphQLRequestPagination(endCursor)
            }
            else {
              null
            }
          }
          else {
            val startCursor = it.startCursor
            if (it.hasPreviousPage && startCursor != null) {
              GraphQLRequestPagination(startCursor)
            }
            else {
              null
            }
          }
        }
      }
    }

  fun <T> createPagesFlowByLinkHeader(initialURI: URI, request: suspend (URI) -> HttpResponse<T>): Flow<HttpResponse<T>> =
    flow {
      var loadPage: (suspend () -> HttpResponse<T>)? = { request(initialURI) }
      while (loadPage != null) {
        val response: HttpResponse<T> = loadPage()
        val linkHeader = response.headers().firstValue(LinkHttpHeaderValue.HEADER_NAME).orElse(null)?.let(LinkHttpHeaderValue::parse)
        emit(response)
        loadPage = linkHeader?.nextLink?.let {
          {
            val uriWithInitialScheme = URIUtil.createUriWithCustomScheme(it, initialURI.scheme)
            request(uriWithInitialScheme)
          }
        }
      }
    }
}

suspend fun <T> Flow<Iterable<T>>.foldToList(): List<T> = foldToList { it }

suspend fun <T, R> Flow<Iterable<T>>.foldToList(mapper: (T) -> R): List<R> =
  fold(mutableListOf()) { acc: MutableList<R>, value: Iterable<T> ->
    value.mapTo(acc, mapper)
    acc
  }