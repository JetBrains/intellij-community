// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.Messages
import org.jetbrains.zip.signer.exceptions.ZipVerificationException
import org.jetbrains.zip.signer.verifier.ZipVerifier
import java.io.File

import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

sealed class PluginsSignatureVerificationResult
data class PluginsSignatureVerificationError(val errorMessage: String?) : PluginsSignatureVerificationResult()
object PluginsSignatureVerificationSuccess : PluginsSignatureVerificationResult()

object PluginSignatureChecker {

  @JvmStatic
  fun checkPluginsSignature(pluginName: String, pluginFile: File, indicator: ProgressIndicator): Boolean {
    indicator.checkCanceled()
    indicator.text2 = IdeBundle.message("plugin.signature.checker.progress", pluginName)
    indicator.isIndeterminate = true

    val verificationResult = verify(pluginFile)
    if (verificationResult is PluginsSignatureVerificationError) {
      val title = IdeBundle.message("plugin.signature.checker.title")
      val message = IdeBundle.message("plugin.signature.checker.untrusted.message", pluginName, verificationResult.errorMessage)
      val yesText = IdeBundle.message("plugin.signature.checker.yes")
      val noText = IdeBundle.message("plugin.signature.checker.no")
      var result: Int = -1
      ApplicationManager.getApplication().invokeAndWait(
        { result = Messages.showYesNoDialog(message, title, yesText, noText, Messages.getWarningIcon()) },
        ModalityState.any())
      return result == Messages.YES
    }

    return true
  }

  private fun verify(pluginFile: File): PluginsSignatureVerificationResult {
    try {
      ZipVerifier.verify(pluginFile, getCertificates().last())
      return PluginsSignatureVerificationSuccess
    } catch (e: ZipVerificationException) {
      return PluginsSignatureVerificationError(e.message)
    }
  }

  private fun getCertificates(): List<X509Certificate> {
    val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(null as KeyStore?)
    return trustManagerFactory.trustManagers.filterIsInstance<X509TrustManager>().flatMap {
      it.acceptedIssuers.toList()
    }
  }
}