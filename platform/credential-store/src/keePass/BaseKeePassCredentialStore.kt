// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.keePass

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.CredentialStore
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.SERVICE_NAME_PREFIX
import com.intellij.credentialStore.kdbx.KeePassDatabase

internal const val ROOT_GROUP_NAME = SERVICE_NAME_PREFIX

internal abstract class BaseKeePassCredentialStore : CredentialStore {
  protected abstract val db: KeePassDatabase

  override fun get(attributes: CredentialAttributes): Credentials? {
    val entry = db.rootGroup.getGroup(ROOT_GROUP_NAME)?.getEntry(attributes.serviceName, attributes.userName) ?: return null
    return Credentials(attributes.userName ?: entry.userName, entry.password?.get())
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