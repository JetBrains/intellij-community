// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore.keePass

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.CredentialStore
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.SERVICE_NAME_PREFIX
import com.intellij.credentialStore.kdbx.KeePassDatabase
import com.intellij.util.text.nullize

internal const val ROOT_GROUP_NAME = SERVICE_NAME_PREFIX

sealed class BaseKeePassCredentialStore : CredentialStore {
  protected abstract val db: KeePassDatabase

  override fun get(attributes: CredentialAttributes): Credentials? {
    val group = db.rootGroup.getGroup(ROOT_GROUP_NAME) ?: return null
    val userName = attributes.userName.nullize()
    // opposite to set, on get if username is specified, find using specified username,
    // otherwise, if for some reason there are several matches by service name, an incorrect result can be returned
    // (on get we cannot punish/judge and better to return the most correct result if possible)
    val entry = group.getEntry(attributes.serviceName, userName.nullize()) ?: return null
    return Credentials(userName ?: entry.userName, entry.password?.get())
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    if (credentials == null) {
      db.rootGroup.getGroup(ROOT_GROUP_NAME)?.removeEntry(attributes.serviceName, attributes.userName.nullize())
    }
    else {
      val group = db.rootGroup.getOrCreateGroup(ROOT_GROUP_NAME)
      val userName = attributes.userName.nullize() ?: credentials.userName
      // should be the only credentials per service name - find without username
      var entry = group.getEntry(attributes.serviceName, if (attributes.serviceName == SERVICE_NAME_PREFIX) userName else null)
      if (entry == null) {
        entry = group.createEntry(attributes.serviceName, userName)
      }
      else {
        entry.userName = userName
      }
      entry.password = if (attributes.isPasswordMemoryOnly || credentials.password == null) null else db.protectValue(credentials.password!!)
    }

    if (db.isDirty) {
      markDirty()
    }
  }

  protected abstract fun markDirty()
}
