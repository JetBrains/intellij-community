// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl

interface InitialUntrustedCertificateStrategyProvider {
  fun getStrategy(): UntrustedCertificateStrategy
}

class DefaultInitialUntrustedCertificateStrategyProvider: InitialUntrustedCertificateStrategyProvider {
  override fun getStrategy(): UntrustedCertificateStrategy {
    return UntrustedCertificateStrategy.ASK_USER
  }
}