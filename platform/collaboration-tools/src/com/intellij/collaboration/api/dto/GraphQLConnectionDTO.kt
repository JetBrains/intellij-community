// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.dto

open class GraphQLConnectionDTO<out T>(
  override val pageInfo: GraphQLCursorPageInfoDTO,
  nodes: List<T>
) : GraphQLNodesDTO<T>(nodes), GraphQLPagedResponseDataDTO<T>