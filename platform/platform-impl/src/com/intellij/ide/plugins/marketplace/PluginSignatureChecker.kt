// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.certificates.PluginCertificateStore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.util.net.ssl.CertificateUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.zip.signer.verifier.InvalidSignatureResult
import org.jetbrains.zip.signer.verifier.MissingSignatureResult
import org.jetbrains.zip.signer.verifier.SuccessfulVerificationResult
import org.jetbrains.zip.signer.verifier.ZipVerifier
import java.io.File
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

@ApiStatus.Internal
object PluginSignatureChecker {

  private val LOG = Logger.getInstance(PluginSignatureChecker::class.java)

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

  private const val REQUIRED_CERTIFICATE = "required certificate"

  private val certificateStore = PluginCertificateStore.instance

  @JvmStatic
  fun isSignedByCustomCertificates(pluginName: String, pluginFile: File): Boolean {
    val certificates = certificateStore.customTrustManager.certificates
    if (certificates.isEmpty()) return true
    // TODO: check required certificates
    return certificates.any { isSignedBy(pluginName, pluginFile, it) }
  }

  @JvmStatic
  fun isSignedByJetBrains(pluginName: String, pluginFile: File): Boolean {
    val jbCert = jetbrainsCertificate ?: return processSignatureWarning(pluginName, IdeBundle.message("jetbrains.certificate.not.found"))
    return isSignedBy(pluginName, pluginFile, jbCert)
  }

  private fun isSignedBy(pluginName: String, pluginFile: File, certificate: Certificate): Boolean {
    val errorMessage = verifyPluginAndGetErrorMessage(pluginFile, certificate)
    if (errorMessage != null) {
      return processSignatureWarning(pluginName, errorMessage)
    }
    return true
  }

  private fun verifyPluginAndGetErrorMessage(file: File, certificate: Certificate): String? {
    return when (val verificationResult = ZipVerifier.verify(file)) {
      is InvalidSignatureResult -> verificationResult.errorMessage
      is MissingSignatureResult -> IdeBundle.message("plugin.signature.not.signed")
      is SuccessfulVerificationResult ->
        if (!verificationResult.isSignedBy(certificate)) {
          val certificateName = if (certificate is X509Certificate) CertificateUtil.getCommonName(certificate) else REQUIRED_CERTIFICATE
          IdeBundle.message("plugin.signature.not.signed.by.jetbrains") + certificateName
        }
        else null
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