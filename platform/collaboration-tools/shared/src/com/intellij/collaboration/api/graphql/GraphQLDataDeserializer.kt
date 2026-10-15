// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.graphql

import com.intellij.collaboration.api.dto.GraphQLErrorDTO
import com.intellij.collaboration.api.dto.GraphQLResponseDTO
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.io.Reader
import java.nio.charset.Charset

@ApiStatus.Experimental
interface GraphQLDataDeserializer {
  /**
   * The reader is not closed by this function. It should be managed by the caller.
   */
  fun <T> readAndMapGQLResponse(bodyReader: Reader, pathFromData: Array<out String>, clazz: Class<T>)
    : GraphQLResponseDTO<T?, GraphQLErrorDTO>

  /**
   * The reader is not closed by this function. It should be managed by the caller.
   */
  fun <T : Any> readAndMapGQLResponse(stream: InputStream, charset: Charset, pathFromData: Array<out String>, clazz: Class<T>)
    : GraphQLResponseDTO<T?, GraphQLErrorDTO>
}
