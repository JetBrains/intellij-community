// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.ByteString
import io.grpc.*
import java.security.SecureRandom


data class DaemonClientCredentials(val token: ByteString) {
  constructor(token: ByteArray) : this(ByteString.copyFrom(token))

  fun asMetadata() = Metadata().apply {
    put(TOKEN_METADATA_KEY, token.toByteArray())
  }

  companion object {
    private val EMPTY = DaemonClientCredentials(ByteString.EMPTY)

    private val TOKEN_METADATA_KEY: Metadata.Key<ByteArray> = Metadata.Key.of("process-mediator-token" + Metadata.BINARY_HEADER_SUFFIX,
                                                                              Metadata.BINARY_BYTE_MARSHALLER)

    fun fromMetadata(headers: Metadata): DaemonClientCredentials {
      val token = headers.get(TOKEN_METADATA_KEY) ?: return EMPTY
      return DaemonClientCredentials(token)
    }

    fun generate(length: Int = 64): DaemonClientCredentials {
      val bytes = ByteArray(length).apply(SecureRandom()::nextBytes)
      return DaemonClientCredentials(bytes)
    }
  }
}
