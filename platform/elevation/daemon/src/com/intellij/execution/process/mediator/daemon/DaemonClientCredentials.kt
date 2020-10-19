// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.ByteString
import io.grpc.*
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.Cipher


data class DaemonClientCredentials(val token: ByteString) {
  constructor(token: ByteArray) : this(ByteString.copyFrom(token))

  fun asMetadata() = Metadata().apply {
    put(TOKEN_METADATA_KEY, token.toByteArray())
  }

  fun rsaEncrypt(publicKey: PublicKey): ByteString {
    val cipher = createRsaCipher().apply {
      init(Cipher.ENCRYPT_MODE, publicKey)
    }
    val encryptedBytes = cipher.doFinal(token.toByteArray())
    return ByteString.copyFrom(encryptedBytes)
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

    fun rsaDecrypt(encryptedToken: ByteString, privateKey: PrivateKey): DaemonClientCredentials {
      val cipher = createRsaCipher().apply {
        init(Cipher.DECRYPT_MODE, privateKey)
      }
      val token = cipher.doFinal(encryptedToken.toByteArray())
      return DaemonClientCredentials(token)
    }

    private fun createRsaCipher() = Cipher.getInstance("RSA/ECB/PKCS1Padding")
  }
}

internal class CredentialsAuthServerInterceptor(private val credentials: DaemonClientCredentials) : ServerInterceptor {
  override fun <ReqT, RespT> interceptCall(call: ServerCall<ReqT, RespT>,
                                           headers: Metadata,
                                           next: ServerCallHandler<ReqT, RespT>): ServerCall.Listener<ReqT> {
    if (DaemonClientCredentials.fromMetadata(headers) != credentials) {
      call.close(Status.UNAUTHENTICATED.withDescription("Invalid token"), headers)
      return nopListener()
    }
    return next.startCall(call, headers)
  }

  companion object {
    private val NOP_LISTENER = object : ServerCall.Listener<Any>() {}

    private fun <ReqT> nopListener(): ServerCall.Listener<ReqT> {
      @Suppress("UNCHECKED_CAST")
      return NOP_LISTENER as ServerCall.Listener<ReqT>
    }
  }
}
