// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.keePass

import com.intellij.credentialStore.*
import com.intellij.credentialStore.kdbx.KeePassDatabase

internal const val ROOT_GROUP_NAME = SERVICE_NAME_PREFIX

internal abstract class BaseKeePassCredentialStore : CredentialStore {
  protected abstract val db: KeePassDatabase

  override fun get(attributes: CredentialAttributes): Credentials? {
    val requestor = attributes.requestor
    val userName = attributes.userName
    val entry = db.rootGroup.getGroup(ROOT_GROUP_NAME)?.getEntry(attributes.serviceName, attributes.userName)
    if (entry != null) {
      return Credentials(attributes.userName ?: entry.userName, entry.password?.get())
    }

    if (requestor == null || userName == null) {
      return null
    }

    // try old key - as hash
    val oldAttributes = toOldKey(requestor, userName)
    db.rootGroup.getGroup(ROOT_GROUP_NAME)?.removeEntry(oldAttributes.serviceName, oldAttributes.userName)?.let {
      fun createCredentials() = Credentials(userName, it.password?.get())
      @Suppress("DEPRECATION")
      set(CredentialAttributes(requestor, userName), createCredentials())
      return createCredentials()
    }

    return null
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    if (credentials == null) {
      db.rootGroup.getGroup(ROOT_GROUP_NAME)?.removeEntry(attributes.serviceName, attributes.userName)
    }
    else {
      val group = db.rootGroup.getOrCreateGroup(ROOT_GROUP_NAME)
      // should be the only credentials per service name - find without user name
      val userName = attributes.userName ?: credentials.userName
      var entry = group.getEntry(attributes.serviceName, if (attributes.serviceName == SERVICE_NAME_PREFIX) userName else null)
      if (entry == null) {
        entry = group.getOrCreateEntry(attributes.serviceName, userName)
      }
      entry.userName = userName
      entry.password = if (attributes.isPasswordMemoryOnly || credentials.password == null) null else db.protectValue(credentials.password!!)
    }

    if (db.isDirty) {
      markDirty()
    }
  }

  protected abstract fun markDirty()
}