// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.certificates

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.DigestUtil.sha256Hex
import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.net.ssl.CertificateUtil
import com.intellij.util.net.ssl.ConfirmingTrustManager
import com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager
import org.jetbrains.annotations.NonNls
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
import java.security.*
import javax.crypto.BadPaddingException
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

class CertificateManager() {
  private val myTrustManager = NotNullLazyValue.atomicLazy {
    ConfirmingTrustManager.createForStorage(DEFAULT_PATH, DEFAULT_PASSWORD)
  }
  private val mySslContext = NotNullLazyValue.atomicLazy { calcSslContext() }

  /**
   * Creates special kind of `SSLContext`, which X509TrustManager first checks certificate presence in
   * in default system-wide trust store (usually located at `${JAVA_HOME}/lib/security/cacerts` or specified by
   * `javax.net.ssl.trustStore` property) and when in the one specified by the constant [.DEFAULT_PATH].
   * If certificate wasn't found in either, manager will ask user, whether it can be
   * accepted (like web-browsers do) and then, if it does, certificate will be added to specified trust store.
   *
   *
   * If any error occurred during creation its message will be logged and system default SSL context will be returned
   * so clients don't have to deal with awkward JSSE errors.
   *
   * This method may be used for transition to HttpClient 4.x (see `HttpClientBuilder#setSslContext(SSLContext)`)
   * and `org.apache.http.conn.ssl.SSLConnectionSocketFactory()`.
   *
   * @return instance of SSLContext with described behavior or default SSL context in case of error
   */
  @get:Synchronized
  val sslContext: SSLContext
    get() = mySslContext.value

  private fun calcSslContext(): SSLContext {
    val context: SSLContext = getSystemSslContext()
    try {
      // SSLContext context = SSLContext.getDefault();
      // NOTE: existence of default trust manager can be checked here as
      // assert systemManager.getAcceptedIssuers().length != 0
      context.init(getDefaultKeyManagers(), arrayOf<TrustManager>(
        trustManager), null)
    }
    catch (e: KeyManagementException) {
      LOG.error(e)
    }
    return context
  }

  val cacertsPath: String
    get() = DEFAULT_PATH
  val password: String
    get() = DEFAULT_PASSWORD
  val trustManager: ConfirmingTrustManager
    get() = myTrustManager.value
  val customTrustManager: MutableTrustManager
    get() = trustManager.customManager

  companion object {
    val COMPONENT_NAME: @NonNls String = "Certificate Manager"
    val DEFAULT_PATH = java.lang.String.join(File.separator, PathManager.getConfigPath(), "plugins", "cacerts")
    val DEFAULT_PASSWORD: @NonNls String = "changeit"
    private val LOG = Logger.getInstance(CertificateManager::class.java)

    /**
     * Used to check whether dialog is visible to prevent possible deadlock, e.g. when some external resource is loaded by
     * [java.awt.MediaTracker].
     */
    val DIALOG_VISIBILITY_TIMEOUT: Long = 5000 // ms
    val instance: CertificateManager
      get() = ApplicationManager.getApplication().getService(CertificateManager::class.java)


    // NOTE: SSLContext.getDefault() should not be called because it automatically creates
    // default context which can't be initialized twice
    val systemSslContext: SSLContext
      get() {
        // NOTE: SSLContext.getDefault() should not be called because it automatically creates
        // default context which can't be initialized twice
        try {
          // actually TLSv1 support is mandatory for Java platform
          val context = SSLContext.getInstance(CertificateUtil.TLS)
          context.init(null, null, null)
          return context
        }
        catch (e: NoSuchAlgorithmException) {
          LOG.error(e)
          throw AssertionError("Cannot get system SSL context")
        }
        catch (e: KeyManagementException) {
          LOG.error(e)
          throw AssertionError("Cannot initialize system SSL context")
        }
      }
  }

  /**
   * Component initialization constructor
   */
  init {
    AppExecutorUtil.getAppExecutorService().execute {
      try {
        // Don't do this: protocol created this way will ignore SSL tunnels. See IDEA-115708.
        // Protocol.registerProtocol("https", createDefault().createProtocol());
        SSLContext.setDefault(sslContext)
        LOG.info("Default SSL context initialized")
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }
  }

  fun getSystemSslContext(): SSLContext {
    // NOTE: SSLContext.getDefault() should not be called because it automatically creates
    // default context which can't be initialized twice
    return try {
      // actually TLSv1 support is mandatory for Java platform
      val context = SSLContext.getInstance(CertificateUtil.TLS)
      context.init(null, null, null)
      context
    }
    catch (e: NoSuchAlgorithmException) {
      LOG.error(e)
      throw AssertionError("Cannot get system SSL context")
    }
    catch (e: KeyManagementException) {
      LOG.error(e)
      throw AssertionError("Cannot initialize system SSL context")
    }
  }

  /**
   * Workaround for IDEA-124057. Manually find key store specified via VM options.
   *
   * @return key managers or `null` in case of any error
   */
  fun getDefaultKeyManagers(): Array<KeyManager?>? {
    val keyStorePath = System.getProperty("javax.net.ssl.keyStore") ?: return null
    LOG.info("Loading custom key store specified with VM options: $keyStorePath")
    try {
      val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
      val keyStore: KeyStore
      val keyStoreType = System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType())
      keyStore = try {
        KeyStore.getInstance(keyStoreType)
      }
      catch (e: KeyStoreException) {
        if (e.cause is NoSuchAlgorithmException) {
          LOG.error("Wrong key store type: $keyStoreType", e)
          return null
        }
        throw e
      }
      val keyStoreFile = Paths.get(keyStorePath)
      var password = System.getProperty("javax.net.ssl.keyStorePassword", "")
      if (password!!.isEmpty() && SystemInfoRt.isMac) {
        try {
          val itemName = FileUtilRt.getNameWithoutExtension(keyStoreFile.fileName.toString())
          password = PasswordSafe.instance.getPassword(CredentialAttributes(itemName, itemName))
          if (password == null) {
            password = ""
          }
        }
        catch (e: Throwable) {
          LOG.error("Cannot get password for $keyStorePath", e)
        }
      }
      try {
        Files.newInputStream(keyStoreFile).use { inputStream ->
          keyStore.load(inputStream, password!!.toCharArray())
          factory.init(keyStore, password.toCharArray())
        }
      }
      catch (e: NoSuchFileException) {
        LOG.error("Key store file not found: $keyStorePath")
        return null
      }
      catch (e: Exception) {
        if (e.cause is BadPaddingException || e.cause is UnrecoverableKeyException) {
          LOG.error("Wrong key store password (sha-256): " + sha256Hex(
            password!!.toByteArray(StandardCharsets.UTF_8)), e)
          return null
        }
        throw e
      }
      return factory.keyManagers
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    return null
  }

}