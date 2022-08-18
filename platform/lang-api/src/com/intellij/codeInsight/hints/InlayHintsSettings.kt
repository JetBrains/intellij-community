// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.configurationStore.deserializeInto
import com.intellij.configurationStore.serialize
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic
import org.jdom.Element
import java.util.*

@State(name = "InlayHintsSettings", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
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
    // explicitly enabled languages (because of enabled by default setting, we can't say that everything which is not disabled is enabled)
    var enabledHintProviderIds: TreeSet<String> = sortedSetOf()

    var disabledHintProviderIds: TreeSet<String> = sortedSetOf()
    // We can't store Map<String, Any> directly, because values deserialized as Object
    var settingsMapElement: Element = Element("settingsMapElement")

    var lastViewedProviderKeyId: String? = null

    var isEnabled: Boolean = true

    var disabledLanguages: TreeSet<String> = sortedSetOf()
  }

  // protected by lock
  private val myCachedSettingsMap: MutableMap<String, Any> = hashMapOf()
  // protected by lock
  private val isEnabledByDefaultIdsCache: MutableMap<String, Boolean> = hashMapOf()

  init {
    InlayHintsProviderExtension.inlayProviderName.addChangeListener(Runnable {
      synchronized(lock) {
        isEnabledByDefaultIdsCache.clear()
      }
    }, null)
  }

  fun changeHintTypeStatus(key: SettingsKey<*>, language: Language, enable: Boolean) {
    synchronized(lock) {
      val id = key.getFullId(language)
      if (enable) {
        if (!isEnabledByDefault(key, language)) {
          myState.enabledHintProviderIds.add(id)
        }
        myState.disabledHintProviderIds.remove(id)
      }
      else {
        myState.enabledHintProviderIds.remove(id)
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

  fun saveLastViewedProviderId(providerId: String): Unit = synchronized(lock) {
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
      if (serialized == null) {
        myState.settingsMapElement = element
      }
      else {
        val storeElement = Element(fullId)
        val wrappedSettingsElement = storeElement.addContent(serialized)
        myState.settingsMapElement = element.addContent(wrappedSettingsElement)
        element.sortAttributes(compareBy { it.name })
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

  fun hintsEnabled(key: SettingsKey<*>, language: Language) : Boolean {
    synchronized(lock) {
      if (explicitlyDisabled(language, key)) {
        return false
      }
      if (isEnabledByDefault(key, language)) {
        return true
      }
      return key.getFullId(language) in state.enabledHintProviderIds
    }
  }

  private fun explicitlyDisabled(language: Language, key: SettingsKey<*>): Boolean {
    var lang: Language? = language
    while (lang != null) {
      if (key.getFullId(lang) in myState.disabledHintProviderIds) {
        return true
      }
      lang = lang.baseLanguage
    }
    return false
  }

  fun hintsShouldBeShown(key: SettingsKey<*>, language: Language): Boolean = synchronized(lock) {

    return hintsEnabledGlobally() &&
           hintsEnabled(language) &&
           hintsEnabled(key, language)
  }

  override fun getState(): State = synchronized(lock) {
    return myState
  }

  override fun loadState(state: State): Unit = synchronized(lock) {
    val elementChanged = myState.settingsMapElement != state.settingsMapElement
    if (elementChanged) {
      myCachedSettingsMap.clear()
    }
    myState = state
  }

  // may return parameter settings object or cached object
  private fun <T : Any> getSettingCached(id: String, settings: ()->T): T {
    synchronized(lock) {
      @Suppress("UNCHECKED_CAST")
      val cachedValue = myCachedSettingsMap[id] as T?
      if (cachedValue != null) return cachedValue
      val notCachedSettings = getSettingNotCached(id, settings())
      myCachedSettingsMap[id] = notCachedSettings
      return notCachedSettings
    }
  }

  private fun <T : Any> getSettingNotCached(id: String, settings: T): T {
    val state = myState.settingsMapElement
    val settingsElement = state.getChild(id)
    if (settingsElement == null) return settings
    val settingsElementChildren= settingsElement.children
    if (settingsElementChildren.isEmpty()) return settings
    settingsElementChildren.first().deserializeInto(settings)
    return settings
  }

  // must be called under lock
  private fun isEnabledByDefault(key: SettingsKey<*>, language: Language) : Boolean {
      return isEnabledByDefaultIdsCache.computeIfAbsent(key.getFullId(language)) { computeIsEnabledByDefault(it) }
  }

  private fun computeIsEnabledByDefault(id: String) : Boolean {
    val bean = InlayHintsProviderExtension.inlayProviderName.extensionList
      .firstOrNull {
        val keyId = it.settingsKeyId ?: return@firstOrNull false
        SettingsKey.getFullId(it.language!!, keyId) == id
      } ?: return true
    return bean.isEnabledByDefault
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

