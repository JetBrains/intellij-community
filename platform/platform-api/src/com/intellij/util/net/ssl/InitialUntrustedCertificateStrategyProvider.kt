// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl

data class UntrustedCertificateStrategyWithReason(
  val strategy: UntrustedCertificateStrategy,
  val reason: String?
)

interface InitialUntrustedCertificateStrategyProvider {
  fun getStrategy(): UntrustedCertificateStrategyWithReason
}

class DefaultInitialUntrustedCertificateStrategyProvider: InitialUntrustedCertificateStrategyProvider {
  override fun getStrategy(): UntrustedCertificateStrategyWithReason {
    return UntrustedCertificateStrategyWithReason(UntrustedCertificateStrategy.ASK_USER, null)
  }
}