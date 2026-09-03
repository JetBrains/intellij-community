// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.credentialStore.linux.LibSecret
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.text.nullize
import java.lang.foreign.MemorySegment
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Supplier

private const val DBUS_ERROR_SERVICE_UNKNOWN = 2
private const val SECRET_ERROR_IS_LOCKED = 2

private const val SERVICE_ATTRIBUTE = "service"
private const val ACCOUNT_ATTRIBUTE = "account"

// we use a default collection, it seems no way to use custom
internal class SecretCredentialStore private constructor(schemeName: String) : CredentialStore {
  companion object {
    // no need to load lazily - if store created, then it will be used
    // and for clients better to get error earlier, in creation place
    private val DBUS_ERROR = LibSecret.dbusErrorQuark()
    private val SECRET_ERROR = LibSecret.secretErrorQuark()

    fun create(schemeName: String): SecretCredentialStore? =
      if (pingService()) SecretCredentialStore(schemeName)
      else null

    private fun pingService(): Boolean {
      val dummySchema = LibSecret.schemaNew("IJ.dummy.ping.schema", LibSecret.SECRET_SCHEMA_DONT_MATCH_NAME, "ij-dummy-attribute")
      try {
        val errorOut = LibSecret.ErrorOut()
        LibSecret.passwordLookupSync(dummySchema, errorOut, "ij-dummy-attribute", "ij-dummy-attribute")
        val error = errorOut.error ?: return true
        if (isNoSecretService(error)) return false
      }
      finally {
        LibSecret.schemaUnref(dummySchema)
      }
      return true
    }

    private fun isNoSecretService(error: LibSecret.GError) = error.domain == DBUS_ERROR && error.code == DBUS_ERROR_SERVICE_UNKNOWN
  }

  // Matching by name fails on locked keyring, because matches against `org.freedesktop.Secret.Generic`
  // https://mail.gnome.org/archives/gnome-keyring-list/2015-November/msg00000.html
  private val schema: MemorySegment by lazy {
    LibSecret.schemaNew(schemeName, LibSecret.SECRET_SCHEMA_DONT_MATCH_NAME, SERVICE_ATTRIBUTE, ACCOUNT_ATTRIBUTE)
  }

  override fun get(attributes: CredentialAttributes): Credentials? {
    val start = System.currentTimeMillis()
    try {
      val credentials = CompletableFuture.supplyAsync(Supplier {
        val userName = attributes.userName.nullize()
        checkError("secret_password_lookup_sync") { errorOut ->
          // Secret Service doesn't allow getting attributes, so, we store joined data
          val data = if (userName == null) {
            LibSecret.passwordLookupSync(schema, errorOut, SERVICE_ATTRIBUTE, attributes.serviceName)
          }
          else {
            LibSecret.passwordLookupSync(schema, errorOut, SERVICE_ATTRIBUTE, attributes.serviceName, ACCOUNT_ATTRIBUTE, userName)
          }
          data?.let { splitData(it) }
        }
      }, AppExecutorUtil.getAppExecutorService())
        .get(30 /* on Linux first access to keychain can cause system unlock dialog, so, allow user to input data */, TimeUnit.SECONDS)
      val end = System.currentTimeMillis()
      if (credentials == null && end - start > 300) {
        //todo: use complex API instead
        return ACCESS_TO_KEY_CHAIN_DENIED
      }
      return credentials
    }
    catch (_: TimeoutException) {
      LOG.warn("storage unlock timeout")
      return CANNOT_UNLOCK_KEYCHAIN
    }
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    val serviceName = attributes.serviceName
    val accountName = attributes.userName.nullize() ?: credentials?.userName
    val lookupName = if (serviceName == SERVICE_NAME_PREFIX) accountName else null
    if (credentials.isEmpty()) {
      clearPassword(serviceName, lookupName)
      return
    }

    val password = joinData(credentials!!.userName, if (attributes.isPasswordMemoryOnly) null else credentials.password)!!
    try {
      checkError("secret_password_store_sync") { errorOut ->
        clearPassword(serviceName, null)
        if (accountName == null) {
          LibSecret.passwordStoreSync(schema, serviceName, password, errorOut, SERVICE_ATTRIBUTE, serviceName)
        }
        else {
          LibSecret.passwordStoreSync(schema, serviceName, password, errorOut, SERVICE_ATTRIBUTE, serviceName, ACCOUNT_ATTRIBUTE, accountName)
        }
      }
    }
    finally {
      password.fill(0)
    }
  }

  private fun clearPassword(serviceName: String, accountName: String?) {
    checkError("secret_password_clear_sync") { errorOut ->
      if (accountName == null) {
        LibSecret.passwordClearSync(schema, errorOut, SERVICE_ATTRIBUTE, serviceName)
      }
      else {
        LibSecret.passwordClearSync(schema, errorOut, SERVICE_ATTRIBUTE, serviceName, ACCOUNT_ATTRIBUTE, accountName)
      }
    }
  }

  private inline fun <T> checkError(method: String, task: (errorOut: LibSecret.ErrorOut) -> T): T {
    val errorOut = LibSecret.ErrorOut()
    val result = task(errorOut)
    val error = errorOut.error
    if (error != null) {
      if (isNoSecretService(error)) {
        LOG.warn("gnome-keyring not installed or kde doesn't support Secret Service API. $method error code ${error.code}, error message ${error.message}")
      }
      if (error.domain == SECRET_ERROR && error.code == SECRET_ERROR_IS_LOCKED) {
        LOG.warn("Cancelled storage unlock: ${error.message}")
      }
      else {
        LOG.error("$method error code ${error.code}, error message ${error.message}")
      }
    }
    return result
  }
}
