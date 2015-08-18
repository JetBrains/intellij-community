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
import com.intellij.openapi.components.impl.stores.*
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.SmartHashSet
import gnu.trove.THashMap
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.JDOMException
import java.io.IOException
import java.util.Arrays

abstract class XmlElementStorage protected constructor(protected val fileSpec: String,
                                                       protected val rootElementName: String,
                                                       protected val pathMacroSubstitutor: TrackingPathMacroSubstitutor?,
                                                       roamingType: RoamingType?,
                                                       provider: StreamProvider?) : StateStorageBase<StateMap>() {
  protected val roamingType: RoamingType = roamingType ?: RoamingType.PER_USER
  private val provider: StreamProvider? = if (provider == null || roamingType == RoamingType.DISABLED || !provider.isApplicable(fileSpec, this.roamingType)) null else provider

  protected abstract fun loadLocalData(): Element?

  override fun getStateAndArchive(storageData: StateMap, component: Any, componentName: String) = storageData.getStateAndArchive(componentName)

  override fun loadData(): StateMap {
    val states = StateMap()
    val element: Element?
    // we don't use local data if has stream provider
    if (provider != null && provider.enabled) {
      try {
        element = loadDataFromProvider()
        if (element != null) {
          states.loadState(element)
        }
      }
      catch (e: Exception) {
        LOG.error(e)
        element = null
      }
    }
    else {
      element = loadLocalData()
    }

    if (element != null) {
      states.loadState(element)
    }
    return states
  }

  throws(IOException::class, JDOMException::class)
  private fun loadDataFromProvider() = JDOMUtil.load(provider!!.loadContent(fileSpec, roamingType))

  private fun StateMap.loadState(element: Element) {
    beforeElementLoaded(element)
    StateMap.load(this, element, pathMacroSubstitutor, true)
  }

  fun setDefaultState(element: Element) {
    element.setName(rootElementName)
    val states = StateMap()
    states.loadState(element)
    storageDataRef.set(states)
  }

  override fun startExternalization() = if (checkIsSavingDisabled()) null else createSaveSession(getStorageData())

  protected abstract fun createSaveSession(states: StateMap): StateStorage.ExternalizationSession

  override fun analyzeExternalChangesAndUpdateIfNeed(componentNames: MutableSet<String>) {
    val oldData = storageDataRef.get()
    val newData = getStorageData(true)
    if (oldData == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("analyzeExternalChangesAndUpdateIfNeed: old data null, load new for ${toString()}")
      }
      componentNames.addAll(newData.keys())
    }
    else {
      val changedComponentNames = oldData.getChangedComponentNames(newData)
      if (LOG.isDebugEnabled()) {
        LOG.debug("analyzeExternalChangesAndUpdateIfNeed: changedComponentNames $changedComponentNames for ${toString()}")
      }
      if (!ContainerUtil.isEmpty(changedComponentNames)) {
        componentNames.addAll(changedComponentNames)
      }
    }
  }

  private fun setStorageData(oldStorageData: StateMap, newStorageData: StateMap?) {
    if (oldStorageData !== newStorageData && storageDataRef.getAndSet(newStorageData) !== oldStorageData) {
      LOG.warn("Old storage data is not equal to current, new storage data was set anyway")
    }
  }

  abstract class XmlElementStorageSaveSession<T : XmlElementStorage>(private val originalStates: StateMap, protected val storage: T) : SaveSessionBase() {
    private var copiedStates: StateMap? = null

    private val newLiveStates = THashMap<String, Element>()

    override fun createSaveSession() = if (storage.checkIsSavingDisabled() || copiedStates == null) null else this

    override fun setSerializedState(component: Any, componentName: String, element: Element?) {
      if (copiedStates == null) {
        copiedStates = setStateAndCloneIfNeed(componentName, element, originalStates, newLiveStates)
      }
      else {
        copiedStates!!.setState(componentName, element, newLiveStates)
      }
    }

    override fun save() {
      var states = copiedStates!!
      var element = save(states, newLiveStates, storage.rootElementName)
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
          val content = StorageUtil.writeToBytes(element, "\n")
          provider.saveContent(storage.fileSpec, content.getInternalBuffer(), content.size(), storage.roamingType)
        }
      }
      else {
        saveLocally(element)
      }
      storage.setStorageData(originalStates, states)
    }

    throws(IOException::class)
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

  public fun updatedFromStreamProvider(changedComponentNames: MutableSet<String>, deleted: Boolean) {
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
          setStorageData(states, null)
        }
      }
      else if (states != null) {
        val newStorageData = StateMap()
        newStorageData.loadState(newElement)
        changedComponentNames.addAll(states.getChangedComponentNames(newStorageData))
        setStorageData(states, newStorageData)
      }
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }
}

private fun save(states: StateMap, newLiveStates: Map<String, Element>, rootElementName: String): Element? {
  if (states.isEmpty()) {
    return null
  }

  val rootElement = Element(rootElementName)
  val componentNames = ArrayUtil.toStringArray(states.keys())
  Arrays.sort(componentNames)
  for (componentName in componentNames) {
    assert(componentName != null)
    val element = states.getElement(componentName, newLiveStates)
    // name attribute should be first
    val elementAttributes = element.getAttributes()
    if (elementAttributes.isEmpty()) {
      element.setAttribute(StateMap.NAME, componentName)
    }
    else {
      var nameAttribute: Attribute? = element.getAttribute(StateMap.NAME)
      if (nameAttribute == null) {
        nameAttribute = Attribute(StateMap.NAME, componentName)
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

fun setStateAndCloneIfNeed(componentName: String, newState: Element?, oldStates: StateMap, newLiveStates: MutableMap<String, Element>): StateMap? {
  val oldState = oldStates.get(componentName)
  if (newState == null || JDOMUtil.isEmpty(newState)) {
    if (oldState == null) {
      return null
    }

    val newStates = StateMap(oldStates)
    newStates.remove(componentName)
    return newStates
  }

  prepareElement(newState)

  newLiveStates.put(componentName, newState)

  var newBytes: ByteArray? = null
  if (oldState is Element) {
    if (JDOMUtil.areElementsEqual(oldState as Element?, newState)) {
      return null
    }
  }
  else if (oldState != null) {
    newBytes = StateMap.getNewByteIfDiffers(componentName, newState, oldState as ByteArray)
    if (newBytes == null) {
      return null
    }
  }

  val newStates = StateMap(oldStates)
  newStates.put(componentName, newBytes ?: newState)
  return newStates
}

fun prepareElement(state: Element) {
  if (state.getParent() != null) {
    LOG.warn("State element must not have parent ${JDOMUtil.writeElement(state)}")
    state.detach()
  }
  state.setName(StateMap.COMPONENT)
}

fun StateMap.setState(componentName: String, newState: Element?, newLiveStates: MutableMap<String, Element>): Any? {
  if (newState == null || JDOMUtil.isEmpty(newState)) {
    return remove(componentName)
  }

  prepareElement(newState)

  newLiveStates.put(componentName, newState)

  val oldState = get(componentName)

  var newBytes: ByteArray? = null
  if (oldState is Element) {
    if (JDOMUtil.areElementsEqual(oldState as Element?, newState)) {
      return null
    }
  }
  else if (oldState != null) {
    newBytes = StateMap.getNewByteIfDiffers(componentName, newState, oldState as ByteArray)
    if (newBytes == null) {
      return null
    }
  }

  put(componentName, if (newBytes == null) newState else newBytes)
  return newState
}

// newStorageData - myStates contains only live (unarchived) states
private fun StateMap.getChangedComponentNames(newStates: StateMap): Set<String> {
  val bothStates = SmartHashSet(keys())
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