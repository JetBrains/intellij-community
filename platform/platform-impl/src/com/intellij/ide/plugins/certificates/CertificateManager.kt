// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.certificates

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.PasswordSafe.Companion.instance
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.DigestUtil.sha256Hex
import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.net.ssl.CertificateUtil
import com.intellij.util.net.ssl.ConfirmingTrustManager
import com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager
import com.intellij.util.net.ssl.UntrustedCertificateStrategy
import org.jetbrains.annotations.NonNls
import java.io.File
import java.io.IOException
import java.lang.AssertionError
import java.lang.Exception
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.security.*
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.BadPaddingException
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

/**
 * `CertificateManager` is responsible for negotiation SSL connection with server
 * and deals with untrusted/self-signed/expired and other kinds of digital certificates.
 * <h1>Integration details:</h1>
 * If you're using httpclient-3.1 without custom `Protocol` instance for HTTPS you don't have to do anything
 * at all: default `HttpClient` will use "Default" `SSLContext`, which is set up by this component itself.
 *
 *
 * However for httpclient-4.x you have several of choices:
 *
 *  1. Client returned by `HttpClients.createSystem()` will use "Default" SSL context as it does in httpclient-3.1.
 *  1. If you want to customize `HttpClient` using `HttpClients.custom()`, you can use the following methods of the builder
 * (in the order of increasing complexity/flexibility)
 *
 *  1. `useSystemProperties()` methods makes `HttpClient` use "Default" SSL context again
 *  1. `setSSLContext()` and pass result of the [.getSslContext]
 *  1. `setSSLSocketFactory()` and specify instance `SSLConnectionSocketFactory` which uses result of [.getSslContext].
 *  1. `setConnectionManager` and initialize it with `Registry` that binds aforementioned `SSLConnectionSocketFactory` to HTTPS protocol
 *
 *
 *
 *
 * @author Mikhail Golubev
 */
class CertificateManager() {
  private val myTrustManager = NotNullLazyValue.atomicLazy(
    {
      ConfirmingTrustManager.createForStorage(DEFAULT_PATH,
                                              DEFAULT_PASSWORD)
    })
  private val mySslContext = NotNullLazyValue.atomicLazy(
    { calcSslContext() })

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

//TODO:  @Throws(E::class)
  fun <T, E : Throwable?> runWithUntrustedCertificateStrategy(computable: ThrowableComputable<T, E>,
                                                              strategy: UntrustedCertificateStrategy): T {
    trustManager.myUntrustedCertificateStrategy.set(strategy)
    try {
      return computable.compute()
    }
    finally {
      trustManager.myUntrustedCertificateStrategy.remove()
    }
  }

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

    /**
     * Workaround for IDEA-124057. Manually find key store specified via VM options.
     *
     * @return key managers or `null` in case of any error
     */
    val defaultKeyManagers: Array<KeyManager>?
      get() {
        val keyStorePath = System.getProperty("javax.net.ssl.keyStore") ?: return null
        LOG.info(
          "Loading custom key store specified with VM options: $keyStorePath")
        try {
          val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
          val keyStore: KeyStore
          val keyStoreType = System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType())
          try {
            keyStore = KeyStore.getInstance(keyStoreType)
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

    fun showAcceptDialog(dialogFactory: Callable<out DialogWrapper>): Boolean {
      val app = ApplicationManager.getApplication()
      val proceeded = CountDownLatch(1)
      val accepted = AtomicBoolean()
      val dialogRef = AtomicReference<DialogWrapper>()
      val showDialog = Runnable {

        // skip if certificate was already rejected due to timeout or interrupt
        if (proceeded.getCount() == 0L) {
          return@Runnable
        }
        try {
          val dialog: DialogWrapper = dialogFactory.call()
          dialogRef.set(dialog)
          accepted.set(dialog.showAndGet())
        }
        catch (e: Exception) {
          LOG.error(e)
        }
        finally {
          proceeded.countDown()
        }
      }
      if (app.isDispatchThread) {
        showDialog.run()
      }
      else {
        app.invokeLater(showDialog, ModalityState.any())
      }
      try {
        // IDEA-123467 and IDEA-123335 workaround
        val inTime = proceeded.await(DIALOG_VISIBILITY_TIMEOUT, TimeUnit.MILLISECONDS)
        if (!inTime) {
          val dialog = dialogRef.get()
          if (dialog == null || !dialog.isShowing) {
            LOG.debug(
              "After " + DIALOG_VISIBILITY_TIMEOUT + " ms dialog was not shown. " +
              "Rejecting certificate. Current thread: " + Thread.currentThread().name)
            proceeded.countDown()
            return false
          }
          else {
            proceeded.await() // if dialog is already shown continue waiting
          }
        }
      }
      catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        proceeded.countDown()
      }
      return accepted.get()
    }
  }

  /**
   * Component initialization constructor
   */
  init {
    AppExecutorUtil.getAppExecutorService().execute(
      {
        try {
          // Don't do this: protocol created this way will ignore SSL tunnels. See IDEA-115708.
          // Protocol.registerProtocol("https", createDefault().createProtocol());
          SSLContext.setDefault(sslContext)
          LOG.info("Default SSL context initialized")
        }
        catch (e: Exception) {
          LOG.error(e)
        }
      })
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