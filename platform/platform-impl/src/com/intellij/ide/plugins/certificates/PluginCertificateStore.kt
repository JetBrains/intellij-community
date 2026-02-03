// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.certificates

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.net.ssl.ConfirmingTrustManager
import com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager
import java.security.KeyStore
import java.security.cert.X509Certificate
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.pathString

object PluginCertificateStore {
  private val LOG = Logger.getInstance(PluginCertificateStore::class.java)
  private val MANAGED_TRUSTSTORE_PATH = System.getProperty("intellij.plugin.truststore", "")
  private val DEFAULT_PATH = PathManager.getOriginalConfigDir().resolve("ssl").resolve("plugins-certs").pathString
  private const val DEFAULT_PASSWORD = "changeit"
  val customTrustManager: MutableTrustManager by lazy {
    ConfirmingTrustManager.createForStorage(DEFAULT_PATH, DEFAULT_PASSWORD).customManager
  }

  val managedTrustedCertificates : List<X509Certificate> by lazy {
    if (MANAGED_TRUSTSTORE_PATH.isNotBlank()) {
      loadCertificates(MANAGED_TRUSTSTORE_PATH)
    } else {
      emptyList()
    }
  }

  private fun loadCertificates(storePath: String) : List<X509Certificate> {
    val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
    val keystorePath = Path(storePath)
    if (keystorePath.exists()) {
      try {
        keystore.load(keystorePath.inputStream(), DEFAULT_PASSWORD.toCharArray())
        return keystore.aliases().toList().mapNotNull { keystore.getCertificate(it) as? X509Certificate }
      } catch (e: Exception) {
        LOG.warn("Failed to load managed plugin truststore", e)
      }
    }
    return emptyList()
  }
}
