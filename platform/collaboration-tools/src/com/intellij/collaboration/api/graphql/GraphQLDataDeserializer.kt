// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.graphql

import com.intellij.collaboration.api.dto.GraphQLErrorDTO
import com.intellij.collaboration.api.dto.GraphQLResponseDTO
import org.jetbrains.annotations.ApiStatus
import java.io.Reader

@ApiStatus.Experimental
interface GraphQLDataDeserializer {
  fun <T> readAndMapGQLResponse(bodyReader: Reader, pathFromData: Array<out String>, clazz: Class<T>)
  : GraphQLResponseDTO<T?, GraphQLErrorDTO>
}
