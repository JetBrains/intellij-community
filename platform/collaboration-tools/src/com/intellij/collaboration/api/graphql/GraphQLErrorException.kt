// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.graphql

import com.intellij.collaboration.api.dto.GraphQLErrorDTO
import com.intellij.collaboration.messages.CollaborationToolsBundle
class GraphQLErrorException(val errors: List<GraphQLErrorDTO>)
  : RuntimeException("GraphQL error: $errors") {
  override fun getLocalizedMessage(): String =
    CollaborationToolsBundle.message("graphql.errors", errors.toString())
}