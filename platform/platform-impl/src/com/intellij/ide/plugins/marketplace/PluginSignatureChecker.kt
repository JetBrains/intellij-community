// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.certificates.PluginCertificateStore
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.DialogAcceptanceResultEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.SignatureVerificationResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.zip.signer.signer.CertificateUtils
import org.jetbrains.zip.signer.verifier.InvalidSignatureResult
import org.jetbrains.zip.signer.verifier.MissingSignatureResult
import org.jetbrains.zip.signer.verifier.SuccessfulVerificationResult
import org.jetbrains.zip.signer.verifier.ZipVerifier
import java.nio.file.Path
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit

@ApiStatus.Internal
internal object PluginSignatureChecker {
  private val LOG = logger<PluginSignatureChecker>()

  private val jetBrainsCertificateRevokedCache = Caffeine
    .newBuilder()
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build<String, Optional<Boolean>>()

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
  fun verifyIfRequired(descriptor: IdeaPluginDescriptor, pluginFile: Path, isMarketplace: Boolean, showAcceptDialog: Boolean): Boolean {
    val key = if (isMarketplace) "marketplace.certificate.signature.check" else "custom-repository.certificate.signature.check"
    if (!RegistryManager.getInstance().`is`(key)) {
      return true
    }
    val certificates = PluginCertificateStore.customTrustManager.certificates + PluginCertificateStore.managedTrustedCertificates
    return if (showAcceptDialog) isSignedInWithAcceptDialog(descriptor, pluginFile, certificates)
           else isSignedInBackground(descriptor, pluginFile, certificates)
  }

  @Suppress("LoggingSimilarMessage")
  private fun isSignedInBackground(descriptor: IdeaPluginDescriptor, pluginFile: Path, certificates: List<Certificate>): Boolean {
    val jbCert = jetbrainsCertificate ?: return false
    val isRevoked = runCatching { isJetBrainsCertificateRevoked() }.getOrNull() ?: return false
    if (isRevoked) {
      LOG.info("Plugin ${pluginFile.fileName} has revoked JetBrains certificate")
      return false
    }
    val allCerts = certificates + jbCert
    return isSignedBy(descriptor, pluginFile, showAcceptDialog = false, allCerts)
  }

  @Suppress("LoggingSimilarMessage")
  private fun isSignedInWithAcceptDialog(descriptor: IdeaPluginDescriptor, pluginFile: Path, certificates: List<Certificate>): Boolean {
    val jbCert = jetbrainsCertificate
                 ?: return processSignatureCheckerVerdict(descriptor, IdeBundle.message("jetbrains.certificate.not.found"))
    val isRevoked = try {
      isJetBrainsCertificateRevoked()
    }
    catch (e: IllegalArgumentException) {
      val message = e.message ?: IdeBundle.message("jetbrains.certificate.invalid")
      return processSignatureCheckerVerdict(descriptor, message)
    }
    if (isRevoked) {
      LOG.info("Plugin ${pluginFile.fileName} has revoked JetBrains certificate")
      val message = IdeBundle.message("plugin.signature.checker.revoked.cert", descriptor.name)
      return processSignatureCheckerVerdict(descriptor, message)
    }
    val allCerts = certificates + jbCert
    return isSignedBy(descriptor, pluginFile, showAcceptDialog = true, allCerts)
  }

  private fun isJetBrainsCertificateRevoked(): Boolean {
    val isRevokedCached = jetBrainsCertificateRevokedCache.getIfPresent(this.javaClass.name)?.get()
    if (isRevokedCached != null) return isRevokedCached
    val cert509Lists = listOfNotNull(jetbrainsCertificate as? X509Certificate)
    val lists = getRevocationLists(cert509Lists)
    val revokedCertificates = CertificateUtils.findRevokedCertificate(cert509Lists, lists)
    val isRevoked = revokedCertificates != null
    jetBrainsCertificateRevokedCache.put(this.javaClass.name, Optional.of(isRevoked))
    return isRevoked
  }

  private fun getRevocationLists(certs: List<X509Certificate>): List<X509CRL> {
    val certsExceptCA = certs.subList(0, certs.size - 1)
    return certsExceptCA.mapNotNull { certificate ->
      val crlUris = CertificateUtils.getCrlUris(certificate)
      if (crlUris.isEmpty()) {
        LOG.error("CRL not found for certificate")
        throw IllegalArgumentException("CRL not found for certificate")
      }
      if (crlUris.size > 1) {
        LOG.error("Multiple CRL URI found in certificate")
        throw IllegalArgumentException("Multiple CRL URI found in certificate")
      }
      val crlURI = crlUris.first()
      val certificateFactory = CertificateFactory.getInstance("X.509")
      val inputStream = HttpRequests.request(crlURI.toURL().toExternalForm())
        .throwStatusCodeException(false)
        .productNameAsUserAgent()
        .connect { it.inputStream }
      certificateFactory.generateCRL(inputStream) as? X509CRL
    }
  }

  private fun isSignedBy(descriptor: IdeaPluginDescriptor, pluginFile: Path, showAcceptDialog: Boolean, certificates: List<Certificate>): Boolean {
    val errorMessage = verifyPluginAndGetErrorMessage(descriptor, pluginFile, certificates)
    if (errorMessage != null && showAcceptDialog) {
      return processSignatureCheckerVerdict(descriptor, errorMessage)
    }
    if (errorMessage != null) {
      return false
    }
    return true
  }

  @OptIn(ExperimentalUnsignedTypes::class)
  private fun verifyPluginAndGetErrorMessage(descriptor: IdeaPluginDescriptor, file: Path, certificates: List<Certificate>): @Nls String? =
    when (val verificationResult = ZipVerifier.verify(file)) {
      is InvalidSignatureResult -> {
        PluginManagerUsageCollector.signatureCheckResult(descriptor, SignatureVerificationResult.INVALID_SIGNATURE)
        IdeBundle.message("plugin.invalid.signature.result", descriptor.name, verificationResult.errorMessage)
      }
      is MissingSignatureResult -> {
        PluginManagerUsageCollector.signatureCheckResult(descriptor, SignatureVerificationResult.MISSING_SIGNATURE)
        getSignatureWarningMessage(descriptor)
      }
      is SuccessfulVerificationResult -> {
        val isSigned = certificates.any { certificate ->
          certificate is X509Certificate && verificationResult.isSignedBy(certificate)
        }
        if (!isSigned) {
          PluginManagerUsageCollector.signatureCheckResult(descriptor, SignatureVerificationResult.WRONG_SIGNATURE)
          getSignatureWarningMessage(descriptor)
        }
        else {
          PluginManagerUsageCollector.signatureCheckResult(descriptor, SignatureVerificationResult.SUCCESSFUL)
          null
        }
      }
    }

  private fun getSignatureWarningMessage(descriptor: IdeaPluginDescriptor): @Nls String {
    val vendor = if (descriptor.organization.isNullOrBlank()) descriptor.vendor else descriptor.organization
    val vendorMessage = if (vendor.isNullOrBlank()) vendor else IdeBundle.message("jetbrains.certificate.vendor", vendor)
    return IdeBundle.message(
      "plugin.signature.not.signed",
      descriptor.name,
      descriptor.pluginId.idString,
      descriptor.version,
      vendorMessage
    )
  }

  private fun processSignatureCheckerVerdict(descriptor: IdeaPluginDescriptor, @Nls message: String): Boolean {
    val title = IdeBundle.message("plugin.signature.checker.title")
    val yesText = IdeBundle.message("plugin.signature.checker.yes")
    val noText = IdeBundle.message("plugin.signature.checker.no")
    var result: Int = -1
    ApplicationManager.getApplication().invokeAndWait(
      { result = Messages.showYesNoDialog(message, title, yesText, noText, Messages.getWarningIcon()) },
      ModalityState.any()
    )
    PluginManagerUsageCollector.signatureWarningShown(
      descriptor,
      if (result == Messages.YES) DialogAcceptanceResultEnum.ACCEPTED else DialogAcceptanceResultEnum.DECLINED
    )
    return result == Messages.YES
  }
}
