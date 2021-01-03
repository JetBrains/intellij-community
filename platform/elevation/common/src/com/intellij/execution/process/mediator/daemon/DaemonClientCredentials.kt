// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.ByteString
import io.grpc.Metadata
import java.security.SecureRandom


class DaemonClientCredentials {
  val token: ByteString

  constructor(token: ByteArray) : this(ByteString.copyFrom(token))

  constructor(token: ByteString) {
    require(!token.isEmpty) { "Token must not be empty" }
    this.token = token
  }

  private constructor() {
    this.token = ByteString.EMPTY
  }

  fun asMetadata() = Metadata().apply {
    put(TOKEN_METADATA_KEY, token.toByteArray())
  }

  override fun hashCode(): Int = token.hashCode()
  override fun equals(other: Any?): Boolean = this === other || other is DaemonClientCredentials && token == other.token

  companion object {
    val EMPTY = DaemonClientCredentials()

    private val TOKEN_METADATA_KEY: Metadata.Key<ByteArray> = Metadata.Key.of("process-mediator-token" + Metadata.BINARY_HEADER_SUFFIX,
                                                                              Metadata.BINARY_BYTE_MARSHALLER)

    fun fromMetadata(headers: Metadata): DaemonClientCredentials {
      val token = headers.get(TOKEN_METADATA_KEY)?.takeUnless { it.isEmpty() } ?: return EMPTY
      return DaemonClientCredentials(token)
    }

    fun generate(length: Int = 64): DaemonClientCredentials {
      require(length > 0) { "Token length must be positive" }
      val bytes = ByteArray(length).apply(SecureRandom()::nextBytes)
      return DaemonClientCredentials(bytes)
    }
  }
}
