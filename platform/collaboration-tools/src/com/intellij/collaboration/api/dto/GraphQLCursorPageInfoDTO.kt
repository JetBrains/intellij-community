// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.api.dto

@GraphQLFragment("/graphql/fragment/pageInfo.graphql")
class GraphQLCursorPageInfoDTO(val startCursor: String?, val hasPreviousPage: Boolean,
                               val endCursor: String?, val hasNextPage: Boolean)