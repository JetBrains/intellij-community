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
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.isEmpty
import com.intellij.util.loadElement
import com.intellij.util.toBufferExposingByteArray
import gnu.trove.THashMap
import org.jdom.Attribute
import org.jdom.Element
import java.io.FileNotFoundException

abstract class XmlElementStorage protected constructor(val fileSpec: String,
                                                       protected val rootElementName: String?,
                                                       private val pathMacroSubstitutor: TrackingPathMacroSubstitutor? = null,
                                                       roamingType: RoamingType? = RoamingType.DEFAULT,
                                                       private val provider: StreamProvider? = null) : StorageBaseEx<StateMap>() {
  val roamingType = roamingType ?: RoamingType.DEFAULT

  protected abstract fun loadLocalData(): Element?

  override final fun getSerializedState(storageData: StateMap, component: Any?, componentName: String, archive: Boolean) = storageData.getState(componentName, archive)

  override fun archiveState(storageData: StateMap, componentName: String, serializedState: Element?) {
    storageData.archive(componentName, serializedState)
  }

  override fun hasState(storageData: StateMap, componentName: String) = storageData.hasState(componentName)

  override fun loadData() = loadElement()?.let { loadState(it) } ?: StateMap.EMPTY

  private fun loadElement(useStreamProvider: Boolean = true): Element? {
    var element: Element? = null
    try {
      if (!useStreamProvider || provider?.read(fileSpec, roamingType) {
        it?.let {
          element = loadElement(it)
          providerDataStateChanged(element, DataStateChanged.LOADED)
        }
      } != true) {
        element = loadLocalData()
      }
    }
    catch (e: FileNotFoundException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
    return element
  }

  protected open fun providerDataStateChanged(element: Element?, type: DataStateChanged) {
  }

  private fun loadState(element: Element): StateMap {
    beforeElementLoaded(element)
    return StateMap.fromMap(FileStorageCoreUtil.load(element, pathMacroSubstitutor))
  }

  fun setDefaultState(element: Element) {
    element.name = rootElementName!!
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
      if (changedComponentNames.isNotEmpty()) {
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

    override fun createSaveSession() = if (copiedStates == null || storage.checkIsSavingDisabled()) null else this

    override fun setSerializedState(componentName: String, element: Element?) {
      val normalized = element?.normalizeRootName()
      if (copiedStates == null) {
        copiedStates = setStateAndCloneIfNeed(componentName, normalized, originalStates, newLiveStates)
      }
      else {
        updateState(copiedStates!!, componentName, normalized, newLiveStates)
      }
    }

    override fun save() {
      val stateMap = StateMap.fromMap(copiedStates!!)
      val element = save(stateMap, storage.rootElementName, newLiveStates)
      if (element != null) {
        storage.beforeElementSaved(element)
      }

      var isSavedLocally = false
      val provider = storage.provider
      if (element == null) {
        if (provider == null || !provider.delete(storage.fileSpec, storage.roamingType)) {
          isSavedLocally = true
          saveLocally(null)
        }
      }
      else if (provider != null && provider.isApplicable(storage.fileSpec, storage.roamingType)) {
        // we should use standard line-separator (\n) - stream provider can share file content on any OS
        provider.write(storage.fileSpec, element.toBufferExposingByteArray(), storage.roamingType)
      }
      else {
        isSavedLocally = true
        saveLocally(element)
      }

      if (!isSavedLocally) {
        storage.providerDataStateChanged(element, DataStateChanged.SAVED)
      }

      storage.setStates(originalStates, stateMap)
    }

    protected abstract fun saveLocally(element: Element?)
  }

  protected open fun beforeElementLoaded(element: Element) {
  }

  protected open fun beforeElementSaved(element: Element) {
    pathMacroSubstitutor?.let {
      try {
        it.collapsePaths(element)
      }
      finally {
        it.reset()
      }
    }
  }

  fun updatedFromStreamProvider(changedComponentNames: MutableSet<String>, deleted: Boolean) {
    updatedFrom(changedComponentNames, deleted, true)
  }

  fun updatedFrom(changedComponentNames: MutableSet<String>, deleted: Boolean, useStreamProvider: Boolean) {
    if (roamingType == RoamingType.DISABLED) {
      // storage roaming was changed to DISABLED, but settings repository has old state
      return
    }

    LOG.runAndLogException {
      val newElement = if (deleted) null else loadElement(useStreamProvider)
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
  }
}

private fun save(states: StateMap, rootElementName: String?, newLiveStates: Map<String, Element>? = null): Element? {
  if (states.isEmpty()) {
    return null
  }

  val rootElement = if (rootElementName == null) null else Element(rootElementName)
  for (componentName in states.keys()) {
    val element: Element
    try {
      element = states.getElement(componentName, newLiveStates)?.clone() ?: continue
    }
    catch (e: Exception) {
      LOG.error("Cannot save \"$componentName\" data", e)
      continue
    }

    // name attribute should be first
    val elementAttributes = element.attributes
    var nameAttribute = element.getAttribute(FileStorageCoreUtil.NAME)
    if (nameAttribute != null && nameAttribute === elementAttributes.get(0) && componentName == nameAttribute.value) {
      // all is OK
    }
    else {
      if (nameAttribute == null) {
        nameAttribute = Attribute(FileStorageCoreUtil.NAME, componentName)
        elementAttributes.add(0, nameAttribute)
      }
      else {
        nameAttribute.value = componentName
        if (elementAttributes.get(0) != nameAttribute) {
          elementAttributes.remove(nameAttribute)
          elementAttributes.add(0, nameAttribute)
        }
      }
    }

    if (rootElement == null) {
      return element
    }

    rootElement.addContent(element)
  }
  return if (rootElement.isEmpty()) null else rootElement
}

internal fun Element.normalizeRootName(): Element {
  if (org.jdom.JDOMInterner.isInterned(this)) {
    if (FileStorageCoreUtil.COMPONENT == name) {
      return this
    }
    else {
      val clone = clone()
      clone.name = FileStorageCoreUtil.COMPONENT
      return clone
    }
  }
  else {
    if (parent != null) {
      LOG.warn("State element must not have parent ${JDOMUtil.writeElement(this)}")
      detach()
    }
    name = FileStorageCoreUtil.COMPONENT
    return this
  }
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

enum class DataStateChanged {
  LOADED, SAVED
}