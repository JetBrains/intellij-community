// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.Messages
import org.jetbrains.zip.signer.verifier.InvalidSignatureResult
import org.jetbrains.zip.signer.verifier.MissingSignatureResult
import org.jetbrains.zip.signer.verifier.SuccessfulVerificationResult
import org.jetbrains.zip.signer.verifier.ZipVerifier
import java.io.File

import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object PluginSignatureChecker {

  @JvmStatic
  fun checkPluginsSignature(pluginName: String, pluginFile: File, indicator: ProgressIndicator): Boolean {
    indicator.checkCanceled()
    indicator.text2 = IdeBundle.message("plugin.signature.checker.progress", pluginName)
    indicator.isIndeterminate = true

    val errorMessage = when (val verificationResult = ZipVerifier.verify(pluginFile)) {
      is InvalidSignatureResult -> verificationResult.errorMessage
      is MissingSignatureResult -> IdeBundle.message("plugin.signature.not.signed")
      is SuccessfulVerificationResult ->
        if (!verificationResult.isSignedBy(getCertificate())) {
          IdeBundle.message("plugin.signature.not.signed.by.jetbrains")
        }
        else null
    }

    if (errorMessage != null) {
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

    return true
  }

  //TODO: this method should actually return JetBrains CA
  private fun getCertificate(): X509Certificate {
    val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(null as KeyStore?)
    return trustManagerFactory.trustManagers.filterIsInstance<X509TrustManager>().flatMap {
      it.acceptedIssuers.toList()
    }.last()
  }
}