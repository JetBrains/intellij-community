// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

internal class PinnedPublicKeyTrustManager(
  private val delegate: X509TrustManager,
  private val expectedPublicKey: ByteArray,
) : X509ExtendedTrustManager() {
  override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

  override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
    delegate.checkClientTrusted(chain, authType)
  }

  override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket) {
    val extendedDelegate = delegate as? X509ExtendedTrustManager
    if (extendedDelegate != null) {
      extendedDelegate.checkClientTrusted(chain, authType, socket)
    }
    else {
      delegate.checkClientTrusted(chain, authType)
    }
  }

  override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine) {
    val extendedDelegate = delegate as? X509ExtendedTrustManager
    if (extendedDelegate != null) {
      extendedDelegate.checkClientTrusted(chain, authType, engine)
    }
    else {
      delegate.checkClientTrusted(chain, authType)
    }
  }

  override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
    delegate.checkServerTrusted(chain, authType)
    verifyPinnedPublicKey(chain)
  }

  override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket) {
    val extendedDelegate = delegate as? X509ExtendedTrustManager
    if (extendedDelegate != null) {
      extendedDelegate.checkServerTrusted(chain, authType, socket)
    }
    else {
      delegate.checkServerTrusted(chain, authType)
    }
    verifyPinnedPublicKey(chain)
  }

  override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine) {
    val extendedDelegate = delegate as? X509ExtendedTrustManager
    if (extendedDelegate != null) {
      extendedDelegate.checkServerTrusted(chain, authType, engine)
    }
    else {
      delegate.checkServerTrusted(chain, authType)
    }
    verifyPinnedPublicKey(chain)
  }

  private fun verifyPinnedPublicKey(certificateChain: Array<out X509Certificate>) {
    if (!matchesPinnedPublicKey(certificateChain, expectedPublicKey)) {
      throw CertificateException(DiagnosticBundle.message("error.report.endpoint.public.key.mismatch"))
    }
  }
}

internal fun matchesPinnedPublicKey(certificateChain: Array<out X509Certificate>, expectedPublicKey: ByteArray): Boolean {
  return certificateChain.firstOrNull()?.publicKey?.encoded?.contentEquals(expectedPublicKey) == true
}
