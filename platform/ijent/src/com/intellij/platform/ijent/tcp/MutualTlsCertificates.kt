// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.tcp

import java.io.OutputStream

class MutualTlsCertificates(
  val authority: String,
  val certificateAuthorityPem: String,
  val serverCertificatePem: String,
  val serverPrivateKeyPem: String,
  val clientCertificatePem: String,
  val clientPrivateKeyPem: String,
) {
  /**
   * The PEM blocks that IJent expects to read from its stdin before it binds the TCP socket, in the exact order it reads them.
   */
  fun serverBootstrapPem(): String =
    serverCertificatePem + serverPrivateKeyPem + certificateAuthorityPem

  fun writeTLSData(stream: OutputStream) {
    stream.writer().use { writer ->
      writer.write(serverBootstrapPem())
      writer.flush()
    }
  }
}
