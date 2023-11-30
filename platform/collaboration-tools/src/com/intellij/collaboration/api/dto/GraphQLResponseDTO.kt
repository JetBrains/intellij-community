// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.api.dto

import com.intellij.collaboration.api.graphql.GraphQLErrorException

/**
 * [specification](https://spec.graphql.org/June2018/#sec-Response)
 */
class GraphQLResponseDTO<D, E : GraphQLErrorDTO>(val data: D?, val errors: List<E>?)

fun <D> GraphQLResponseDTO<D, *>.getOrThrow(): D? {
  if (data != null) return data
  if (errors != null) throw GraphQLErrorException(errors)
  return null
}