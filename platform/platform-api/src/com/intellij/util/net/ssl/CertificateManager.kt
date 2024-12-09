// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.io.DigestUtil.sha256Hex
import com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
import java.security.*
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.BadPaddingException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.io.path.pathString

/**
 * `CertificateManager` is responsible for negotiation SSL connection with server
 * and deals with untrusted/self-signed/expired and other kinds of digital certificates.
 * <h1>Integration details:</h1>
 * If you're using httpclient-3.1 without custom `Protocol` instance for HTTPS you don't have to do anything
 * at all: default `HttpClient` will use "Default" `SSLContext`, which is set up by this component itself.
 *
 *
 * However, for httpclient-4.x you have several of choices:
 *
 *  1. Client returned by `HttpClients.createSystem()` will use "Default" SSL context as it does in httpclient-3.1.
 *  1. If you want to customize `HttpClient` using `HttpClients.custom()`, you can use the following methods of the builder
 * (in the order of increasing complexity/flexibility)
 *
 *  1. `useSystemProperties()` methods makes `HttpClient` use "Default" SSL context again
 *  1. `setSSLContext()` and pass result of the [.getSslContext]
 *  1. `setSSLSocketFactory()` and specify instance `SSLConnectionSocketFactory` which uses result of [.getSslContext].
 *  1. `setConnectionManager` and initialize it with `Registry` that binds aforementioned `SSLConnectionSocketFactory` to HTTPS protocol
 */
@State(name = "CertificateManager",
       category = SettingsCategory.TOOLS,
       exportable = true,
       storages = [Storage("certificates.xml", roamingType = RoamingType.DISABLED)], reportStatistic = false)
class CertificateManager(coroutineScope: CoroutineScope) : PersistentStateComponent<CertificateManager.Config?> {
  private var config = Config()

  val trustManager: ConfirmingTrustManager by lazy {
    ConfirmingTrustManager.createForStorage(tryMigratingDefaultTruststore(), DEFAULT_PASSWORD)
  }

  /**
   * Creates special kind of `SSLContext`, which X509TrustManager first checks certificate presence
   * in default system-wide trust store (usually located at `${JAVA_HOME}/lib/security/cacerts` or specified by
   * `javax.net.ssl.trustStore` property) and when in the one specified by the constant [.DEFAULT_PATH].
   * If certificate wasn't found in either, manager will ask user whether it can be
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
  val sslContext: SSLContext by lazy { computeSslContext() } // hot path, do not use method reference

  /**
   * Component initialization constructor
   */
  init {
    coroutineScope.launch {
      // Don't do this: protocol created this way will ignore SSL tunnels. See IDEA-115708.
      // Protocol.registerProtocol("https", CertificateManager.createDefault().createProtocol());
      SSLContext.setDefault(sslContext)
      LOG.info("Default SSL context initialized")
    }
  }

  companion object {
    const val COMPONENT_NAME: @NonNls String = "Certificate Manager"
    @JvmField
    val DEFAULT_PATH: @NonNls String = PathManager.getOriginalConfigDir().resolve("ssl").resolve("cacerts").pathString
    @Suppress("SpellCheckingInspection")
    const val DEFAULT_PASSWORD: @NonNls String = "changeit"
    private val LOG = logger<CertificateManager>()

    /**
     * Used to check whether dialog is visible to prevent possible deadlock, e.g. when some external resource is loaded by
     * [java.awt.MediaTracker].
     */
    const val DIALOG_VISIBILITY_TIMEOUT: Long = 5000 // ms

    @JvmStatic
    fun getInstance(): CertificateManager = ApplicationManager.getApplication().service<CertificateManager>()

    private fun tryMigratingDefaultTruststore(): String {
      val legacySystemPath = Paths.get(PathManager.getSystemPath(), "tasks", "cacerts")
      val configPath = Paths.get(DEFAULT_PATH)
      if (Files.notExists(configPath) && Files.exists(legacySystemPath)) {
        LOG.info("Migrating the default truststore from $legacySystemPath to $configPath")
        try {
          Files.createDirectories(configPath.parent)
          try {
            Files.move(legacySystemPath, configPath)
          }
          catch (ignored: FileAlreadyExistsException) {
            // The legacy truststore is either already copied or missing for some reason - use the new location.
          }
          catch (ignored: NoSuchFileException) {
          }
        }
        catch (e: IOException) {
          LOG.error("Cannot move the default truststore from $legacySystemPath to $configPath", e)
          return legacySystemPath.toString()
        }
      }
      return DEFAULT_PATH
    }

    // NOTE: SSLContext.getDefault() should not be called because it automatically creates
    // default context which can't be initialized twice
    @JvmStatic
    fun getSystemSslContext(): SSLContext {
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
    @JvmStatic
    fun getDefaultKeyManagers(): Array<KeyManager>? {
      val keyStorePath = System.getProperty("javax.net.ssl.keyStore") ?: return null
      LOG.info("Loading custom key store specified with VM options: $keyStorePath")
      try {
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        val keyStoreType = System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType())
        val keyStore = try {
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
            LOG.error("Wrong key store password (sha-256): " + sha256Hex(password!!.toByteArray(StandardCharsets.UTF_8)), e)
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
        if (proceeded.count == 0L) {
          return@Runnable
        }

        try {
          val dialog = dialogFactory.call()
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
            // if dialog is already shown continue waiting
            proceeded.await()
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

  private fun computeSslContext(): SSLContext {
    val context = getSystemSslContext()
    try {
      // SSLContext context = SSLContext.getDefault();
      // NOTE: existence of default trust manager can be checked here as
      // assert systemManager.getAcceptedIssuers().length != 0
      context.init(getDefaultKeyManagers(), arrayOf(trustManager), null)
      // HttpsUrlConnection behaves strangely and caches defaultSSLSocketFactory = (SSLSocketFactory)SSLSocketFactory.getDefault()
      // on the first invocation, even though it could be overridden later
      // if we change the default factory, we need to manually update the HttpsURLConnection.defaultSSLSocketFactory as well
      // see https://youtrack.jetbrains.com/issue/IJPL-171446
      HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
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

  val customTrustManager: MutableTrustManager
    get() = trustManager.customManager

  override fun getState(): Config = config

  override fun loadState(state: Config) {
    config = state
  }

  data class Config(
    /**
     * Do not show the dialog and accept untrusted certificates automatically.
     */
    @Suppress("PropertyName")
    @JvmField var ACCEPT_AUTOMATICALLY: Boolean = false
  )
}