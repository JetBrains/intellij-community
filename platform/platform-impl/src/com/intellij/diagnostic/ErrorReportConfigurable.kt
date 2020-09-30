// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.SERVICE_NAME_PREFIX
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.*
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.XCollection

@State(name = "ErrorReportConfigurable", storages = [Storage(StoragePathMacros.CACHE_FILE)])
internal class ErrorReportConfigurable : PersistentStateComponent<DeveloperList>, SimpleModificationTracker() {
  companion object {
    @JvmStatic
    val SERVICE_NAME = "$SERVICE_NAME_PREFIX — JetBrains Account"

    @JvmStatic
    fun getInstance() = service<ErrorReportConfigurable>()

    @JvmStatic
    fun getCredentials() = PasswordSafe.instance.get(CredentialAttributes(SERVICE_NAME))
  }

  var developerList = DeveloperList()
    set(value) {
      field = value
      incModificationCount()
    }

  override fun getState() = developerList

  override fun loadState(value: DeveloperList) {
    developerList = value
  }
}

// 24 hours
private const val UPDATE_INTERVAL = 24L * 60 * 60 * 1000

internal class DeveloperList {
  constructor() {
    developers = mutableListOf()
    timestamp = 0
  }

  constructor(list: MutableList<Developer>) {
    developers = list
    timestamp = System.currentTimeMillis()
  }

  @field:XCollection(style = XCollection.Style.v2)
  val developers: List<Developer>

  @field:Attribute
  var timestamp: Long
    private set

  fun isUpToDateAt() = timestamp != 0L && (System.currentTimeMillis() - timestamp) < UPDATE_INTERVAL

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DeveloperList
    return developers == other.developers || timestamp == other.timestamp
  }

  override fun hashCode(): Int {
    var result = developers.hashCode()
    result = 31 * result + timestamp.hashCode()
    return result
  }
}