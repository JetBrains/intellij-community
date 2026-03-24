// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.tcp

import java.io.DataOutputStream
import java.io.OutputStream

class MutualTlsCertificates(
  val authority: String,
  val certificateAuthorityPem: ByteArray,
  val serverCertificatePem: ByteArray,
  val serverPrivateKeyPem: ByteArray,
  val clientCertificatePem: ByteArray,
  val clientPrivateKeyPem: ByteArray,
) {
  fun writeTLSData(stream: OutputStream) {
    DataOutputStream(stream).run {
      writeBlob(serverCertificatePem)
      writeBlob(serverPrivateKeyPem)
      writeBlob(certificateAuthorityPem)
      flush()
    }
  }

  private fun DataOutputStream.writeBlob(bytes: ByteArray) {
    writeInt(bytes.size)
    write(bytes)
  }
}
