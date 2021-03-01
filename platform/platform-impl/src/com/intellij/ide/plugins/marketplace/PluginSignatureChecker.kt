// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.certificates.PluginCertificateStore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.zip.signer.verifier.InvalidSignatureResult
import org.jetbrains.zip.signer.verifier.MissingSignatureResult
import org.jetbrains.zip.signer.verifier.SuccessfulVerificationResult
import org.jetbrains.zip.signer.verifier.ZipVerifier
import java.io.File
import java.security.cert.Certificate
import java.security.cert.CertificateFactory

@ApiStatus.Internal
internal object PluginSignatureChecker {
  private val LOG = logger<PluginSignatureChecker>()

  private val jetbrainsCertificate: Certificate? by lazy {
    val cert = PluginSignatureChecker.javaClass.classLoader.getResourceAsStream("ca.crt")
    if (cert == null) {
      LOG.warn(IdeBundle.message("jetbrains.certificate.not.found"))
      null
    }
    else {
      CertificateFactory.getInstance("X.509").generateCertificate(cert)
    }
  }

  @JvmStatic
  fun isSignedByAnyCertificates(pluginName: String, pluginFile: File): Boolean {
    val jbCert = jetbrainsCertificate ?: return processSignatureWarning(pluginName, IdeBundle.message("jetbrains.certificate.not.found"))
    val certificates = PluginCertificateStore.getInstance().customTrustManager.certificates.orEmpty() + jbCert
    return isSignedBy(pluginName, pluginFile, *certificates.toTypedArray())
  }

  @JvmStatic
  fun isSignedByCustomCertificates(pluginName: String, pluginFile: File): Boolean {
    val certificates = PluginCertificateStore.getInstance().customTrustManager.certificates
    if (certificates.isEmpty()) return true
    return isSignedBy(pluginName, pluginFile, *certificates.toTypedArray())
  }

  @JvmStatic
  fun isSignedByJetBrains(pluginName: String, pluginFile: File): Boolean {
    val jbCert = jetbrainsCertificate ?: return processSignatureWarning(pluginName, IdeBundle.message("jetbrains.certificate.not.found"))
    return isSignedBy(pluginName, pluginFile, jbCert)
  }

  private fun isSignedBy(pluginName: String, pluginFile: File, vararg certificate: Certificate): Boolean {
    val errorMessage = verifyPluginAndGetErrorMessage(pluginFile, *certificate)
    if (errorMessage != null) {
      return processSignatureWarning(pluginName, errorMessage)
    }
    return true
  }

  private fun verifyPluginAndGetErrorMessage(file: File, vararg certificates: Certificate): String? {
    return when (val verificationResult = ZipVerifier.verify(file)) {
      is InvalidSignatureResult -> verificationResult.errorMessage
      is MissingSignatureResult -> IdeBundle.message("plugin.signature.not.signed")
      is SuccessfulVerificationResult -> {
        val isSigned = certificates.any { certificate -> verificationResult.isSignedBy(certificate) }
        if (!isSigned) {
          IdeBundle.message("plugin.signature.not.signed.by")
        }
        else null
      }
    }
  }

  private fun processSignatureWarning(pluginName: String, errorMessage: String): Boolean {
    val title = IdeBundle.message("plugin.signature.checker.title")
    val message = IdeBundle.message("plugin.signature.checker.untrusted.message", pluginName, errorMessage)
    val yesText = IdeBundle.message("plugin.signature.checker.yes")
    val noText = IdeBundle.message("plugin.signature.checker.no")
    var result: Int = -1
    ApplicationManager.getApplication().invokeAndWait(
      { result = Messages.showYesNoDialog(message, title, yesText, noText, Messages.getWarningIcon()) },
      ModalityState.any()
    )
    return result == Messages.YES
  }

}