// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.annotations.ApiStatus
import java.security.cert.X509Certificate

@ApiStatus.Internal
interface CertificateWarningDialogProvider {
  companion object {
    fun getInstance(): CertificateWarningDialogProvider? {
      if (LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred) {
        val app = ApplicationManager.getApplication()
        if (app != null && !app.isDisposed()) {
          return app.getService(CertificateWarningDialogProvider::class.java)
        }
      }
      return null
    }
  }

  fun createCertificateWarningDialog(
    certificates: List<X509Certificate>,
    manager: ConfirmingTrustManager.MutableTrustManager,
    remoteHost: String? = null,
    authType: String,
    certificateProvider: CertificateProvider,
  ): DialogWrapper
}

@ApiStatus.Internal
class CertificateProvider() {
  var selectedCertificate: X509Certificate? = null

  /**
   * This property is `true` when the selected certificate is not enough
   * to establish trust for the entire certificate chain.
   *
   * Example scenario:
   * - User trust root certificate but signed certificate also has problem (e.g., expired),
   *
   * Typical use case:
   * - This flag can be used to determine whether additional steps (such
   *   as manually trusting a certificate) are required to proceed with the
   *   connection.
   */
  var isChainRemainUnsafe: Boolean = false
}