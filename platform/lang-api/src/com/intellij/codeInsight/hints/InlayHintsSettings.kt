// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.configurationStore.deserializeInto
import com.intellij.configurationStore.serialize
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic
import org.jdom.Element
import java.util.*

@State(name = "InlayHintsSettings", storages = [Storage("editor.xml")])
class InlayHintsSettings : PersistentStateComponent<InlayHintsSettings.State> {
  companion object {
    @JvmStatic
    fun instance(): InlayHintsSettings {
      return ApplicationManager.getApplication().getService(InlayHintsSettings::class.java)
    }

    /**
     * Inlay hints settings changed.
     */
    @Topic.AppLevel
    @JvmStatic
    val INLAY_SETTINGS_CHANGED: Topic<SettingsListener> = Topic(SettingsListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN)
  }

  private val listener: SettingsListener
    get() = ApplicationManager.getApplication().messageBus.syncPublisher(INLAY_SETTINGS_CHANGED)

  private var myState = State()
  private val lock = Any()

  class State {
    var disabledHintProviderIds: TreeSet<String> = sortedSetOf()
    // We can't store Map<String, Any> directly, because values deserialized as Object
    var settingsMapElement = Element("settingsMapElement")

    var lastViewedProviderKeyId: String? = null

    var isEnabled: Boolean = true

    var disabledLanguages: TreeSet<String> = sortedSetOf()
  }

  private val myCachedSettingsMap: MutableMap<String, Any> = hashMapOf()

  fun changeHintTypeStatus(key: SettingsKey<*>, language: Language, enable: Boolean) {
    synchronized(lock) {
      val id = key.getFullId(language)
      if (enable) {
        myState.disabledHintProviderIds.remove(id)
      }
      else {
        myState.disabledHintProviderIds.add(id)
      }
    }
    listener.settingsChanged()
  }

  fun setHintsEnabledForLanguage(language: Language, enabled: Boolean) {
    val settingsChanged = synchronized(lock) {
      val id = language.id
      if (enabled) {
        myState.disabledLanguages.remove(id)
      }
      else {
        myState.disabledLanguages.add(id)
      }
    }
    if (settingsChanged) {
      listener.languageStatusChanged()
      listener.settingsChanged()
    }
  }

  fun saveLastViewedProviderId(providerId: String) = synchronized(lock) {
    myState.lastViewedProviderKeyId = providerId
  }

  fun getLastViewedProviderId() : String? {
    return myState.lastViewedProviderKeyId
  }

  fun setEnabledGlobally(enabled: Boolean) {
    val settingsChanged = synchronized(lock) {
      if (myState.isEnabled != enabled) {
        myState.isEnabled = enabled
        listener.globalEnabledStatusChanged(enabled)
        true
      } else {
        false
      }
    }
    if (settingsChanged) {
      listener.settingsChanged()
    }
  }

  fun hintsEnabledGlobally() : Boolean = synchronized(lock) {
    return myState.isEnabled
  }

  /**
   * @param createSettings is a setting, that was obtained from createSettings method of provider
   */
  fun <T: Any> findSettings(key: SettingsKey<T>, language: Language, createSettings: ()->T): T = synchronized(lock) {
    val fullId = key.getFullId(language)
    return getSettingCached(fullId, createSettings)
  }

  fun <T: Any> storeSettings(key: SettingsKey<T>, language: Language, value: T) {
    synchronized(lock){
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
    listener.settingsChanged()
  }

  fun hintsEnabled(language: Language) : Boolean = synchronized(lock) {
    return language.id !in myState.disabledLanguages
  }

  fun hintsShouldBeShown(language: Language) : Boolean = synchronized(lock) {
    if (!hintsEnabledGlobally()) return false
    return hintsEnabled(language)
  }

  fun hintsEnabled(key: SettingsKey<*>, language: Language) : Boolean = synchronized(lock) {
    var lang: Language? = language
    while (lang != null) {
      if (key.getFullId(lang) in myState.disabledHintProviderIds) {
        return false
      }
      lang = lang.baseLanguage
    }
    return true
  }

  fun hintsShouldBeShown(key: SettingsKey<*>, language: Language): Boolean = synchronized(lock) {
    if (!hintsEnabledGlobally()) return false
    if (!hintsEnabled(language)) return false
    return hintsEnabled(key, language)
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

  interface SettingsListener {
    /**
     * @param newEnabled whether inlay hints are globally switched on or off now
     */
    fun globalEnabledStatusChanged(newEnabled: Boolean) {}

    /**
     * Called, when hints are enabled/disabled for some language
     */
    fun languageStatusChanged() {}

    /**
     * Called when any settings in inlay hints were changed
     */
    fun settingsChanged() {}
  }
}

