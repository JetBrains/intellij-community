// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.configurationStore.deserializeInto
import com.intellij.configurationStore.serialize
import com.intellij.lang.Language
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jdom.Element

@State(name = "InlayHintsSettings", storages = [Storage("workspace.xml")])
class InlayHintsSettings : PersistentStateComponent<InlayHintsSettings.State> {
  private var myState = State()
  private val lock = Any()

  class State {
    var disabledHintProviderIds: MutableSet<String> = hashSetOf()
    // We can't store Map<String, Any> directly, because values deserialized as Object
    var settingsMapElement = Element("settingsMapElement")

    var lastViewedProviderKeyId: String? = null
  }

  private val myCachedSettingsMap: MutableMap<String, Any> = hashMapOf()

  fun changeHintTypeStatus(key: SettingsKey<*>, language: Language, enable: Boolean) = synchronized(lock) {
    val id = key.getFullId(language)
    if (enable) {
      myState.disabledHintProviderIds.remove(id)
    } else {
      myState.disabledHintProviderIds.add(id)
    }
  }

  fun saveLastViewedProviderId(providerId: String) = synchronized(lock) {
    state.lastViewedProviderKeyId = providerId
  }

  fun getLastViewedProviderId() : String? {
    return state.lastViewedProviderKeyId
  }

  fun invertHintTypeStatus(key: SettingsKey<*>, language: Language) = synchronized(lock) {
    val id = key.getFullId(language)
    if (id in myState.disabledHintProviderIds) {
      myState.disabledHintProviderIds.remove(id)
    }
    else {
      myState.disabledHintProviderIds.add(id)
    }
  }

  /**
   * @param uninitSettings is a setting, that was obtained from createSettings method of provider
   */
  fun <T: Any> findSettings(key: SettingsKey<T>, language: Language, uninitSettings: ()->T): T = synchronized(lock) {
    val fullId = key.getFullId(language)
    return getSettingCached(fullId, uninitSettings)
  }

  fun <T: Any> storeSettings(key: SettingsKey<T>, language: Language, value: T) = synchronized(lock){
    val fullId = key.getFullId(language)
    myCachedSettingsMap[fullId] = value as Any
    val element = myState.settingsMapElement.clone()
    element.removeChild(fullId)
    val serialized = serialize(value)
    if (serialized != null) {
      val storeElement = Element(fullId)
      val wrappedSettingsElement = storeElement.addContent(serialized)
      myState.settingsMapElement = element.addContent(wrappedSettingsElement)
      element.sortAttributes(compareBy { it.name })
    } else {
      myState.settingsMapElement = element
    }
  }

  fun hintsEnabled(key: SettingsKey<*>, language: Language) : Boolean = synchronized(lock) {
    var lang: Language? = language
    while (lang != null) {
      if (key.getFullId(lang) in myState.disabledHintProviderIds) return false
      lang = lang.baseLanguage
    }
    return true
  }

  override fun getState(): State = synchronized(lock) {
    return myState
  }

  override fun loadState(state: State) = synchronized(lock) {
    val elementChanged = myState.settingsMapElement != state.settingsMapElement
    if (elementChanged) {
      myCachedSettingsMap.clear()
    }
    myState = state
  }

  // may return parameter settings object or cached object
  private fun <T : Any> getSettingCached(id: String, settings: ()->T): T {
    @Suppress("UNCHECKED_CAST")
    val cachedValue = myCachedSettingsMap[id] as T?
    if (cachedValue != null) return cachedValue
    return getSettingNotCached(id, settings())
  }

  private fun <T : Any> getSettingNotCached(id: String, settings: T): T {
    val state = myState.settingsMapElement
    val settingsElement = state.getChild(id) ?: return settings
    val settingsElementChildren= settingsElement.children
    if (settingsElementChildren.isEmpty()) return settings
    settingsElementChildren.first().deserializeInto(settings)
    myCachedSettingsMap[id] = settings
    return settings
  }

  companion object {
    @JvmStatic
    fun instance(): InlayHintsSettings {
      return ServiceManager.getService(InlayHintsSettings::class.java)
    }
  }
}

