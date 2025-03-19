// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.util.WaitForProgressToShow
import com.intellij.util.net.ProxyAuthentication.Companion.getInstance
import com.intellij.util.net.internal.asDisabledProxyAuthPromptsManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * [ProxyAuthentication] provides functionality for requesting authentication for proxy from the user.
 */
interface ProxyAuthentication {
  companion object {
    @JvmStatic
    fun getInstance(): ProxyAuthentication = defaultPlatformProxyAuth

    private val defaultPlatformProxyAuth = PlatformProxyAuthentication(
      getCredentialStore = ProxyCredentialStore::getInstance,
      getDisabledPromptsManager = DisabledProxyAuthPromptsManager::getInstance,
    )
  }

  /**
   * @return already known credentials if there are any, `null` otherwise.
   */
  fun getKnownAuthentication(host: String, port: Int): Credentials?

  /**
   * Always requests authentication from the user for a proxy located at the provided host and port.
   * If the user has refused to do so before, returns null without asking them again.
   * Authentication prompt is a blocking operation, which may involve (but not limited to) operations on EDT in arbitrary modality state.
   *
   * One may want to first use [getOrPromptAuthentication] to not ask the user for credentials if they are already known.
   *
   * TODO behaviour in headless mode. Currently no support is implemented, i.e., this method of [getInstance] always returns `null`.
   *  But if credentials are already remembered, they will be used in [getKnownAuthentication]/[getOrPromptAuthentication].
   *
   * @param prompt prompt from the authentication request to be shown to the user
   * @return null if the user has refused to provide credentials
   */
  fun getPromptedAuthentication(prompt: @Nls String, host: String, port: Int): Credentials?

  /**
   * Whether the user refused to provide credentials for the specified proxy
   */
  fun isPromptedAuthenticationCancelled(host: String, port: Int): Boolean

  /**
   * Allows prompting the user for proxy authentication even if they refused to provide credentials before.
   * @see isPromptedAuthenticationCancelled
   */
  fun enablePromptedAuthentication(host: String, port: Int)
}

/**
 * @return already known credentials if there are any, or prompts for new credentials otherwise.
 * @see ProxyAuthentication.getPromptedAuthentication
 */
fun ProxyAuthentication.getOrPromptAuthentication(prompt: @Nls String, host: String, port: Int): Credentials? {
  return getKnownAuthentication(host, port) ?: getPromptedAuthentication(prompt, host, port)
}


@ApiStatus.Internal
interface DisabledProxyAuthPromptsManager {
  companion object {
    @JvmStatic
    fun getInstance(): DisabledProxyAuthPromptsManager = defaultPlatformDisabledPromptsManager

    @Suppress("DEPRECATION", "removal")
    private val defaultPlatformDisabledPromptsManager = (HttpConfigurable::getInstance).asDisabledProxyAuthPromptsManager()
  }

  /**
   * Remembers that the user canceled the prompted authentication.
   */
  fun disablePromptedAuthentication(host: String, port: Int)

  /**
   * Whether the user refused to provide credentials for the specified proxy
   */
  fun isPromptedAuthenticationDisabled(host: String, port: Int): Boolean

  /**
   * Allows prompting the user for proxy authentication even if they refused to provide credentials before.
   * @see isPromptedAuthenticationDisabled
   */
  fun enablePromptedAuthentication(host: String, port: Int)

  /**
   * Allow prompting the user for authentication for all proxies.
   * @see isPromptedAuthenticationDisabled
   */
  fun enableAllPromptedAuthentications()
}

@ApiStatus.Internal
class PlatformProxyAuthentication(
  private val getCredentialStore: () -> ProxyCredentialStore,
  private val getDisabledPromptsManager: () -> DisabledProxyAuthPromptsManager
) : ProxyAuthentication {
  override fun getKnownAuthentication(host: String, port: Int): Credentials? {
    val credentials = getCredentialStore().getCredentials(host, port)
    logger.debug {
      if (credentials != null) "returning known credentials for $host:$port, credentials=${credentials}"
      else "no known credentials for $host:$port"
    }
    return credentials
  }

  override fun getPromptedAuthentication(prompt: String, host: String, port: Int): Credentials? {
    val app = ApplicationManager.getApplication()
    if (app == null || app.isDisposed) {
      logger.debug { "prompted auth for $host:$port: null, application is not initialized yet/already disposed " }
      return null
    }
    if (app.isUnitTestMode) {
      logger.warn("prompted auth for $host:$port: can't prompt proxy authentication in tests")
      return null
    }
    if (app.isHeadlessEnvironment) {
      // TODO request from terminal if allowed by system property ? and maybe check EnvironmentService ?
      logger.debug { "prompted auth for $host:$port: null, can't prompt in headless mode " }
      return null
    }
    if (isPromptedAuthenticationCancelled(host, port)) {
      logger.debug { "prompted auth for $host:$port: prompted auth was cancelled " }
      return null
    }
    val credentialStore = getCredentialStore()
    var result: Credentials? = null
    val login: String = credentialStore.getCredentials(host, port)?.userName ?: ""
    logger.debug { "prompting auth for $host:$port" }
    runAboveAll {
      val dialog = AuthenticationDialog(
        PopupUtil.getActiveComponent(),
        IdeBundle.message("dialog.title.proxy.authentication", host),
        IdeBundle.message("dialog.message.please.enter.credentials.for", prompt),
        login,
        "",
        @Suppress("DEPRECATION", "removal") // fix after migration to PasswordSafe
        HttpConfigurable.getInstance().KEEP_PROXY_PASSWORD
      )
      dialog.show()
      if (dialog.exitCode == DialogWrapper.OK_EXIT_CODE) {
        val panel = dialog.panel
        val credentials = Credentials(panel.login, panel.password)
        credentialStore.setCredentials(host, port, credentials, panel.isRememberPassword)
        @Suppress("DEPRECATION", "removal") // fix after migration to PasswordSafe
        HttpConfigurable.getInstance().KEEP_PROXY_PASSWORD = panel.isRememberPassword
        result = credentials
      }
      else {
        getDisabledPromptsManager().disablePromptedAuthentication(host, port)
      }
    }
    logger.debug { if (result != null) "prompted auth for $host:$port: $result" else "prompted auth was cancelled" }
    return result
  }

  override fun isPromptedAuthenticationCancelled(host: String, port: Int): Boolean {
    return getDisabledPromptsManager().isPromptedAuthenticationDisabled(host, port)
  }

  override fun enablePromptedAuthentication(host: String, port: Int) {
    return getDisabledPromptsManager().enablePromptedAuthentication(host, port)
  }

  private companion object {
    val logger = logger<PlatformProxyAuthentication>()

    private fun runAboveAll(runnable: Runnable) {
      val progressIndicator = ProgressManager.getInstance().getProgressIndicator()
      if (progressIndicator != null && progressIndicator.isModal()) {
        WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(runnable)
      }
      else {
        ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.any())
      }
    }
  }
}