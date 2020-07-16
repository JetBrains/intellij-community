// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.SERVICE_NAME_PREFIX
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.io.decodeBase64
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

@State(name = "ErrorReportConfigurable", storages = [Storage(value = "errorReporting.xml")])
internal class ErrorReportConfigurable : PersistentStateComponent<Element> {
  companion object {
    @JvmStatic
    val SERVICE_NAME = "$SERVICE_NAME_PREFIX — JetBrains Account"

    @JvmStatic
    val instance: ErrorReportConfigurable
      get() = ServiceManager.getService(ErrorReportConfigurable::class.java)

    @JvmStatic
    fun getCredentials() = PasswordSafe.instance.get(CredentialAttributes(SERVICE_NAME))
  }

  private var myState: State? = null

  var developer: Developers?
    get() = myState?.let { Developers(it.developers.toList(), it.timestamp) }
    set(value) {
      myState = value?.let { State(it.developers.toList(), it.timestamp) }
    }

  override fun getState(): Element? = myState?.let { XmlSerializer.serialize(it) }

  override fun loadState(element: Element) {
    loadOldState(element)
    myState = XmlSerializer.deserialize(element, State::class.java)
  }

  private fun loadOldState(element: Element) {
    val state = XmlSerializer.deserialize(element, OldState::class.java)

    if (!state.ITN_LOGIN.isNullOrEmpty() || !state.ITN_PASSWORD_CRYPT.isNullOrEmpty()) {
      PasswordSafe.instance.set(
        CredentialAttributes(SERVICE_NAME, state.ITN_LOGIN), Credentials(state.ITN_LOGIN, state.ITN_PASSWORD_CRYPT!!.decodeBase64()))
    }
  }

  private data class State(var developers: List<Developer>, var timestamp: Long) {
    @Suppress("unused")
    private constructor(): this(emptyList(), 0)  // needed for XML serialization
  }

  @Suppress("PropertyName")
  private class OldState {
    var ITN_LOGIN: String? = null
    var ITN_PASSWORD_CRYPT: String? = null
  }
}

internal data class Developers(val developers: List<Developer>, val timestamp: Long) {
  companion object {
    private const val UPDATE_INTERVAL = 24L * 60 * 60 * 1000 // 24 hours
  }

  fun isUpToDateAt(timestamp: Long): Boolean = developers.isEmpty() || (timestamp - this.timestamp < UPDATE_INTERVAL)
}