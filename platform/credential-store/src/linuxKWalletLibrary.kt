// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.text.nullize
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.errors.NoReply
import org.freedesktop.dbus.errors.ServiceUnknown
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant
import java.io.Closeable

internal class KWalletCredentialStore private constructor(private val connection: DBusConnection, private val kWallet: KWallet) : CredentialStore, Closeable {
  companion object {
    private fun appName(): String {
      val app = ApplicationManager.getApplication()
      val appName = if (app == null || app.isUnitTestMode) null else ApplicationInfo.getInstance().fullApplicationName
      return appName ?: "IDEA tests"
    }

    fun create(): KWalletCredentialStore? {
      try {
        val connection = DBusConnectionBuilder.forSessionBus().build()
        try {
          val wallet = connection.getRemoteObject("org.kde.kwalletd5", "/modules/kwalletd5", KWallet::class.java, true)
          wallet.localWallet() //ping
          return KWalletCredentialStore(connection, wallet)
        }
        catch (e: ServiceUnknown) {
          LOG.info("No KWallet service", e)
        }
        catch (e: DBusException) {
          LOG.warn("Failed to connect to KWallet", e)
        }
        catch (e: RuntimeException) {
          LOG.warn("Failed to connect to KWallet", e)
        }
        connection.close()
      }
      catch (e: DBusException) {
        LOG.warn("Failed to connect to D-Bus", e)
      }
      catch (e: RuntimeException) {
        LOG.warn("Failed to connect to D-Bus", e)
      }
      return null
    }
  }

  private val appId = appName()
  private var cachedWalletId = -1
  private var cachedWalletName: String? = null
  private var suggestedCreation: String? = null

  private fun getWalletId(): Int {
    if (cachedWalletId != -1 && kWallet.isOpen(cachedWalletId) && cachedWalletName?.let { appId in kWallet.users(it) } == true) {
      return cachedWalletId
    }
    val walletName = kWallet.localWallet()
    cachedWalletName = walletName
    val wallets = kWallet.wallets()
    val isNew = walletName !in wallets
    if (walletName == null || isNew && suggestedCreation == walletName) {
      return -1
    }
    cachedWalletId = kWallet.open(walletName, 0L, appId)
    if (isNew) suggestedCreation = walletName
    return cachedWalletId
  }

  private inline fun handleError(run: () -> Unit, handle: () -> Unit) {
    try {
      run()
    }
    catch (e: NoReply) {
      handle()
    }
  }

  override fun get(attributes: CredentialAttributes): Credentials? {
    handleError({
                  val walletId = getWalletId()
                  if (walletId == -1) return ACCESS_TO_KEY_CHAIN_DENIED
                  val userName = attributes.userName.nullize()
                  val passwords = kWallet.readPasswordList(walletId, attributes.serviceName, userName ?: "*", appId)
                  val e = passwords.entries.firstOrNull() ?: return null
                  return Credentials(e.key, e.value.value)
                }) { return CANNOT_UNLOCK_KEYCHAIN }
    return null
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    handleError({
                  val walletId = getWalletId()
                  if (walletId == -1) return
                  val accountName = attributes.userName.nullize() ?: credentials?.userName
                  if (credentials.isEmpty()) {
                    kWallet.removeFolder(walletId, attributes.serviceName, appId)
                  }
                  else {
                    kWallet.writePassword(walletId, attributes.serviceName, accountName ?: "", credentials?.password?.toString() ?: "", appId)
                  }
                }){}
  }

  override fun close() {
    connection.use {
      if (cachedWalletId != -1) {
        kWallet.close(cachedWalletId, false, appId)
      }
    }
  }
}

@DBusInterfaceName("org.kde.KWallet")
interface KWallet : DBusInterface {
  fun localWallet(): String?
  fun wallets(): List<String>
  fun users(wallet: String): List<String>
  fun isOpen(walletId: Int): Boolean
  fun open(wallet: String, wId: Long, appId: String): Int
  fun close(walletId: Int, force: Boolean, appId: String): Int

  fun readPassword(walletId: Int, folder: String, key: String, appId: String): String?
  fun readPasswordList(walletId: Int, folder: String, key: String, appId: String): Map<String, Variant<String>>
  fun removeEntry(walletId: Int, folder: String, key: String, appId: String): Int
  fun removeFolder(walletId: Int, folder: String, appId: String): Boolean
  fun writePassword(walletId: Int, folder: String, key: String, value: String, appId: String): Int
}
