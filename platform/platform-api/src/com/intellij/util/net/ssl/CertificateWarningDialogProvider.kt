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
        val app = ApplicationManager.getApplication();
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
    selectedCertificates: MutableSet<X509Certificate>,
  ): DialogWrapper
}