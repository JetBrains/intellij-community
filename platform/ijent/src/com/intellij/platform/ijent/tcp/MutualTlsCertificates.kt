// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.tcp

import com.intellij.platform.util.annotations.VisibleToClasses
import java.io.OutputStream

@VisibleToClasses(
  "com.intellij.platform.ijent.functional.TcpLocalBootstrapTest",
  "com.intellij.platform.ijent.impl.base.GrpcIjentSessionProviderKt",
  "com.intellij.platform.ijent.impl.base.tcp.MutualTlsCertificatesFactory",
  "com.intellij.platform.ijent.spi.IjentDeployingOverShellProcessStrategyUnitTest",
  "com.intellij.platform.ijent.spi.TestShellStrategy",
  "com.intellij.platform.ijent.ssh.daemon.SshDeployingStrategy",
  "com.intellij.platform.ijent.testFramework.functional.fixture.IjentPosixTcpLocalFixture",
  "com.intellij.platform.ijent.testFramework.functional.fixture.IjentWindowsTcpLocalFixture",
  "com.intellij.rd.eel.frontend.RdIjentDeployingStrategy",
  "com.intellij.rd.eel.frontend.RdProtocolEelMachine",
)
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
