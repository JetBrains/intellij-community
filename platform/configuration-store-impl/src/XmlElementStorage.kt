/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.SmartHashSet
import gnu.trove.THashMap
import org.jdom.Attribute
import org.jdom.Element

abstract class XmlElementStorage protected constructor(protected val fileSpec: String,
                                                       protected val rootElementName: String,
                                                       protected val pathMacroSubstitutor: TrackingPathMacroSubstitutor? = null,
                                                       roamingType: RoamingType? = RoamingType.DEFAULT,
                                                       provider: StreamProvider? = null) : StorageBaseEx<StateMap>() {
  val roamingType: RoamingType = roamingType ?: RoamingType.DEFAULT
  private val provider: StreamProvider? = if (provider == null || roamingType == RoamingType.DISABLED || !provider.isApplicable(fileSpec, this.roamingType)) null else provider

  protected abstract fun loadLocalData(): Element?

  override final fun getSerializedState(storageData: StateMap, component: Any?, componentName: String, archive: Boolean) = storageData.getState(componentName, archive)

  override fun archiveState(storageData: StateMap, componentName: String, serializedState: Element?) {
    storageData.archive(componentName, serializedState)
  }

  override fun hasState(storageData: StateMap, componentName: String) = storageData.hasState(componentName)

  override fun loadData(): StateMap {
    val element: Element?
    // we don't use local data if has stream provider
    if (provider != null && provider.enabled) {
      try {
        element = loadDataFromProvider()
        dataLoadedFromProvider(element)
      }
      catch (e: Exception) {
        LOG.error(e)
        element = null
      }
    }
    else {
      element = loadLocalData()
    }
    return if (element == null) StateMap.EMPTY else loadState(element)
  }

  protected open fun dataLoadedFromProvider(element: Element?) {
  }

  private fun loadDataFromProvider() = JDOMUtil.load(provider!!.read(fileSpec, roamingType))

  private fun loadState(element: Element): StateMap {
    beforeElementLoaded(element)
    return StateMap.fromMap(FileStorageCoreUtil.load(element, pathMacroSubstitutor, true))
  }

  fun setDefaultState(element: Element) {
    element.setName(rootElementName)
    storageDataRef.set(loadState(element))
  }

  override fun startExternalization() = if (checkIsSavingDisabled()) null else createSaveSession(getStorageData())

  protected abstract fun createSaveSession(states: StateMap): StateStorage.ExternalizationSession

  override fun analyzeExternalChangesAndUpdateIfNeed(componentNames: MutableSet<String>) {
    val oldData = storageDataRef.get()
    val newData = getStorageData(true)
    if (oldData == null) {
      LOG.debug { "analyzeExternalChangesAndUpdateIfNeed: old data null, load new for ${toString()}" }
      componentNames.addAll(newData.keys())
    }
    else {
      val changedComponentNames = oldData.getChangedComponentNames(newData)
      LOG.debug { "analyzeExternalChangesAndUpdateIfNeed: changedComponentNames $changedComponentNames for ${toString()}" }
      if (!ContainerUtil.isEmpty(changedComponentNames)) {
        componentNames.addAll(changedComponentNames)
      }
    }
  }

  private fun setStates(oldStorageData: StateMap, newStorageData: StateMap?) {
    if (oldStorageData !== newStorageData && storageDataRef.getAndSet(newStorageData) !== oldStorageData) {
      LOG.warn("Old storage data is not equal to current, new storage data was set anyway")
    }
  }

  abstract class XmlElementStorageSaveSession<T : XmlElementStorage>(private val originalStates: StateMap, protected val storage: T) : SaveSessionBase() {
    private var copiedStates: MutableMap<String, Any>? = null

    private val newLiveStates = THashMap<String, Element>()

    override fun createSaveSession() = if (storage.checkIsSavingDisabled() || copiedStates == null) null else this

    override fun setSerializedState(componentName: String, element: Element?) {
      element?.normalizeRootName()
      if (copiedStates == null) {
        copiedStates = setStateAndCloneIfNeed(componentName, element, originalStates, newLiveStates)
      }
      else {
        updateState(copiedStates!!, componentName, element, newLiveStates)
      }
    }

    override fun save() {
      val stateMap = StateMap.fromMap(copiedStates!!)
      var element = save(stateMap, storage.rootElementName, newLiveStates)
      if (element == null || JDOMUtil.isEmpty(element)) {
        element = null
      }
      else {
        storage.beforeElementSaved(element)
      }

      val provider = storage.provider
      if (provider != null && provider.enabled) {
        if (element == null) {
          provider.delete(storage.fileSpec, storage.roamingType)
        }
        else {
          // we should use standard line-separator (\n) - stream provider can share file content on any OS
          provider.write(storage.fileSpec, element.toBufferExposingByteArray(), storage.roamingType)
        }
      }
      else {
        saveLocally(element)
      }
      storage.setStates(originalStates, stateMap)
    }

    protected abstract fun saveLocally(element: Element?)
  }

  protected open fun beforeElementLoaded(element: Element) {
  }

  protected open fun beforeElementSaved(element: Element) {
    if (pathMacroSubstitutor != null) {
      try {
        pathMacroSubstitutor.collapsePaths(element)
      }
      finally {
        pathMacroSubstitutor.reset()
      }
    }
  }

  fun updatedFromStreamProvider(changedComponentNames: MutableSet<String>, deleted: Boolean) {
    if (roamingType == RoamingType.DISABLED) {
      // storage roaming was changed to DISABLED, but settings repository has old state
      return
    }

    try {
      val newElement = if (deleted) null else loadDataFromProvider()
      val states = storageDataRef.get()
      if (newElement == null) {
        // if data was loaded, mark as changed all loaded components
        if (states != null) {
          changedComponentNames.addAll(states.keys())
          setStates(states, null)
        }
      }
      else if (states != null) {
        val newStates = loadState(newElement)
        changedComponentNames.addAll(states.getChangedComponentNames(newStates))
        setStates(states, newStates)
      }
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }
}

fun save(states: StateMap, rootElementName: String, newLiveStates: Map<String, Element>? = null): Element? {
  if (states.isEmpty()) {
    return null
  }

  val rootElement = Element(rootElementName)
  for (componentName in states.keys()) {
    val element = states.getElement(componentName, newLiveStates)
    // name attribute should be first
    val elementAttributes = element.attributes
    if (elementAttributes.isEmpty()) {
      element.setAttribute(FileStorageCoreUtil.NAME, componentName)
    }
    else {
      var nameAttribute: Attribute? = element.getAttribute(FileStorageCoreUtil.NAME)
      if (nameAttribute == null) {
        nameAttribute = Attribute(FileStorageCoreUtil.NAME, componentName)
        elementAttributes.add(0, nameAttribute)
      }
      else {
        nameAttribute.setValue(componentName)
        if (elementAttributes.get(0) != nameAttribute) {
          elementAttributes.remove(nameAttribute)
          elementAttributes.add(0, nameAttribute)
        }
      }
    }

    rootElement.addContent(element)
  }
  return rootElement
}

internal fun Element.normalizeRootName(): Element {
  if (parent != null) {
    LOG.warn("State element must not have parent ${JDOMUtil.writeElement(this)}")
    detach()
  }
  setName(FileStorageCoreUtil.COMPONENT)
  return this
}

// newStorageData - myStates contains only live (unarchived) states
private fun StateMap.getChangedComponentNames(newStates: StateMap): Set<String> {
  val bothStates = keys().toMutableSet()
  bothStates.retainAll(newStates.keys())

  val diffs = SmartHashSet<String>()
  diffs.addAll(newStates.keys())
  diffs.addAll(keys())
  diffs.removeAll(bothStates)

  for (componentName in bothStates) {
    compare(componentName, newStates, diffs)
  }
  return diffs
}