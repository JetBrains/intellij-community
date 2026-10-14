// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.internal

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.net.ProxyCredentialStore
import java.util.concurrent.ConcurrentHashMap

private const val SERVICE_NAME = "Proxy"
private const val KEYS_PROPERTY_NAME = "ij.safe.proxy.keys"

/**
 * Because the [PasswordSafe] API doesn't offer group removal, the store retains keys set through it to be able to delete them later.
 */
internal class PasswordSafeProxyCredentialStore : ProxyCredentialStore {
  private val passwordSafe: PasswordSafe get() = when {
    ApplicationManager.getApplication().isUnitTestMode -> TestPasswordSafe
    else -> PasswordSafe.instance
  }

  private val transientKeys = LinkedHashSet<String>()

  override fun getCredentials(host: String, port: Int): Credentials? = passwordSafe[attributes(key(host, port))]

  override fun setCredentials(host: String, port: Int, credentials: Credentials?, remember: Boolean) {
    val key = key(host, port)
    passwordSafe.set(attributes(key), credentials, memoryOnly = !remember)

    val keepKey = remember && !passwordSafe.isMemoryOnly
    synchronized(transientKeys) {
      val keys = if (keepKey) getKeys() else transientKeys
      when {
        credentials != null -> keys += key
        else -> keys -= key
      }
      if (keepKey) {
        setKeys(keys)
      }
    }
  }

  override fun areCredentialsRemembered(host: String, port: Int): Boolean {
    val attributes = attributes(key(host, port))
    val credentials = passwordSafe[attributes]
    return credentials != null && !passwordSafe.isPasswordStoredOnlyInMemory(attributes, credentials)
  }

  override fun clearAllCredentials() {
    val allKeys = synchronized(transientKeys) {
      val allKeys = getKeys().map { it to false } + transientKeys.map { it to true }
      setKeys(emptySet())
      transientKeys.clear()
      allKeys
    }
    for ((key, memoryOnly) in allKeys) {
      passwordSafe.set(attributes(key), credentials = null, memoryOnly)
    }
  }

  private fun key(host: String, port: Int): String = "${host}:${port}"

  private fun attributes(key: String): CredentialAttributes = CredentialAttributes(generateServiceName(SERVICE_NAME, key))

  private fun getKeys(): MutableSet<String> = PropertiesComponent.getInstance().getValue(KEYS_PROPERTY_NAME, "").let {
    if (it.isNotEmpty()) LinkedHashSet(it.split('|')) else LinkedHashSet()
  }

  private fun setKeys(keys: Set<String>): Unit = when {
    keys.isEmpty() -> PropertiesComponent.getInstance().unsetValue(KEYS_PROPERTY_NAME)
    else -> PropertiesComponent.getInstance().setValue(KEYS_PROPERTY_NAME, keys.joinToString(separator = "|"))
  }

  private object TestPasswordSafe : PasswordSafe() {
    private val credentialStore = ConcurrentHashMap<String, Credentials>()

    @Volatile
    override var isRememberPasswordByDefault: Boolean = true

    override val isMemoryOnly: Boolean = true

    override fun set(attributes: CredentialAttributes, credentials: Credentials?, memoryOnly: Boolean) = set(attributes, credentials)

    override fun isPasswordStoredOnlyInMemory(attributes: CredentialAttributes, credentials: Credentials): Boolean = true

    override fun get(attributes: CredentialAttributes): Credentials? = credentialStore[key(attributes)]

    override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
      when (credentials) {
        null -> credentialStore.remove(key(attributes))
        else -> credentialStore[key(attributes)] = credentials
      }
    }

    private fun key(attributes: CredentialAttributes): String = "${attributes.serviceName}:${attributes.userName}"
  }
}
