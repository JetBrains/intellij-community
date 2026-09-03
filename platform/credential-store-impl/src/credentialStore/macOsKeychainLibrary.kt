// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.credentialStore.mac.Keychain
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.Internal
val isMacOsCredentialStoreSupported: Boolean
  get() = SystemInfo.isMac

// https://developer.apple.com/library/mac/documentation/Security/Reference/keychainservices/index.html
// It is very, very important to release every item reference and every password copy, otherwise you can get "An invalid record was encountered."
internal class KeyChainCredentialStore : CredentialStore {
  companion object {
    private fun findGenericPassword(serviceName: ByteArray, accountName: String?): Credentials? {
      val found = Keychain.findGenericPassword(serviceName, accountName?.toByteArray(), true)
      val errorCode = checkForError("find", found.status)
      try {
        if (errorCode == Keychain.errSecUserCanceled) {
          return ACCESS_TO_KEY_CHAIN_DENIED
        }
        if (errorCode == Keychain.errUserNameNotCorrect) {
          return CANNOT_UNLOCK_KEYCHAIN
        }

        val passwordBytes = found.password ?: return null
        val password = OneTimeString(passwordBytes)

        var effectiveAccountName = accountName
        if (effectiveAccountName == null) {
          val account = Keychain.copyAccountAttribute(found.itemRef)
          checkForError("SecKeychainItemCopyAttributesAndData", account.status)
          effectiveAccountName = account.value
        }
        return Credentials(effectiveAccountName, password)
      }
      finally {
        if (found.itemRef != 0L) Keychain.release(found.itemRef)
      }
    }

    private fun checkForError(message: String, code: Int): Int {
      if (code == Keychain.errSecSuccess || code == Keychain.errSecItemNotFound) {
        return code
      }

      val translated = Keychain.errorMessage(code)
      val text = if (translated == null) "$message: $code" else "$message: $translated ($code)"
      if (code == Keychain.errUserNameNotCorrect || code == Keychain.errSecUserCanceled || code == Keychain.errSecDuplicateItem) {
        LOG.warn(text)
      }
      else {
        LOG.error(text)
      }

      return code
    }
  }

  override fun get(attributes: CredentialAttributes): Credentials? {
    return findGenericPassword(attributes.serviceName.toByteArray(), attributes.userName.nullize())
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    val serviceName = attributes.serviceName.toByteArray()
    if (credentials.isEmpty()) {
      val userName = attributes.userName.nullize()?.toByteArray()
      val found = Keychain.findGenericPassword(serviceName, userName, false)
      if (found.status == Keychain.errSecItemNotFound || found.status == Keychain.errSecInvalidRecord) {
        return
      }

      checkForError("find (for delete)", found.status)
      if (found.itemRef != 0L) {
        checkForError("delete", Keychain.deleteItem(found.itemRef))
        Keychain.release(found.itemRef)
      }
      return
    }

    val userName = (attributes.userName.nullize() ?: credentials!!.userName)?.toByteArray()
    val searchUserName = if (attributes.serviceName == SERVICE_NAME_PREFIX) userName else null
    val found = Keychain.findGenericPassword(serviceName, searchUserName, false)
    checkForError("find (for save)", found.status)

    val password = if (attributes.isPasswordMemoryOnly || credentials!!.password == null) null else credentials.password!!.toByteArray(false)
    try {
      if (found.itemRef == 0L) {
        checkForError("save (new)", Keychain.addGenericPassword(serviceName, userName, password))
      }
      else {
        checkForError("save (update)", Keychain.modifyContent(found.itemRef, userName, password))
        Keychain.release(found.itemRef)
      }
    }
    finally {
      password?.fill(0)
    }
  }
}
