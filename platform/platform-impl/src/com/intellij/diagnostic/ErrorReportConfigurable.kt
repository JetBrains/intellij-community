// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.SERVICE_NAME_PREFIX
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.*
import com.intellij.util.io.decodeBase64
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

@State(name = "ErrorReportConfigurable", storages = [Storage(value = "other.xml", deprecated = true, roamingType = RoamingType.DISABLED), Storage(value = "errorReporting.xml")])
internal class ErrorReportConfigurable : PersistentStateComponent<Element> {
  companion object {

    private const val ITN_LOGIN = "ITN_LOGIN"
    private const val ITN_PASSWORD_CRYPT = "ITN_PASSWORD_CRYPT"

    @JvmStatic
    val SERVICE_NAME = "$SERVICE_NAME_PREFIX — JetBrains Account"

    @JvmStatic
    val instance: ErrorReportConfigurable
      get() = ServiceManager.getService(ErrorReportConfigurable::class.java)

    @JvmStatic
    fun getCredentials() = PasswordSafe.instance.get(CredentialAttributes(SERVICE_NAME))
  }

  private var myState: State? = null

  var cachedDeveloper: CachedDevelopers?
    get() = myState?.let { CachedDevelopers(it.developers.toList(), it.timestamp) }
    set(value) {
      myState = value?.let { State(it.developers.toList(), it.timestamp) }
    }

  override fun getState(): Element? {
    return myState?.let { XmlSerializer.serialize(it) }
  }

  override fun loadState(state: Element) {
    loadOldState(state)
    myState = XmlSerializer.deserialize(state, State::class.java)
  }

  private fun loadOldState(element: Element) {
    val options = element.getChildren("option")

    fun getOptionValue(name: String): String? =
      options.find { it.getAttributeValue("name") == name }?.getAttributeValue("value")

    val login = getOptionValue(ITN_LOGIN)
    val password = getOptionValue(ITN_PASSWORD_CRYPT)

    if (!login.isNullOrEmpty() || !password.isNullOrEmpty()) {
      PasswordSafe.instance.set(CredentialAttributes(SERVICE_NAME, login), Credentials(login, password?.decodeBase64()))
    }
  }

  internal data class State(var developers: List<Developer>, var timestamp: Long) {
    private constructor(): this(emptyList(), 0) // need for xml serialization
  }
}

internal data class CachedDevelopers(val developers: List<Developer>, val timestamp: Long) {
  companion object {
    private const val UPDATE_INTERVAL = 24L * 60 * 60 * 1000 // 24 hours
  }

  fun isUpToDateAt(timestamp: Long): Boolean {
    return (timestamp - this.timestamp < UPDATE_INTERVAL) && developers.isNotEmpty()
  }
}