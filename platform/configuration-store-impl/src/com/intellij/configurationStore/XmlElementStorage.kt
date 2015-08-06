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
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.LineSeparator
import com.intellij.util.containers.ContainerUtil
import gnu.trove.THashMap
import org.jdom.Element
import org.jdom.JDOMException

import java.io.IOException

abstract class XmlElementStorage protected constructor(protected val fileSpec: String,
                                                       protected val rootElementName: String,
                                                       protected val pathMacroSubstitutor: TrackingPathMacroSubstitutor?,
                                                       roamingType: RoamingType?,
                                                       provider: StreamProvider?) : StateStorageBase<StorageData>() {
  protected val roamingType: RoamingType = roamingType ?: RoamingType.PER_USER
  private val provider: StreamProvider? = if (provider == null || roamingType == RoamingType.DISABLED || !provider.isApplicable(fileSpec, this.roamingType)) null else provider

  protected abstract fun loadLocalData(): Element?

  override fun getStateAndArchive(storageData: StorageData, component: Any, componentName: String) = storageData.getStateAndArchive(componentName)

  override fun loadData(): StorageData {
    val storageData = createStorageData()
    val element: Element?
    // we don't use local data if has stream provider
    if (provider != null && provider.enabled) {
      try {
        element = loadDataFromProvider()
        if (element != null) {
          storageData.loadState(element)
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
      storageData.loadState(element)
    }
    return storageData
  }

  throws(IOException::class, JDOMException::class)
  private fun loadDataFromProvider() = JDOMUtil.load(provider!!.loadContent(fileSpec, roamingType))

  private fun StorageData.loadState(element: Element) {
    load(element, pathMacroSubstitutor, true)
  }

  protected open fun createStorageData(): StorageData = StorageData()

  fun setDefaultState(element: Element) {
    element.setName(rootElementName)
    val storageData = createStorageData()
    storageData.loadState(element)
    storageDataRef.set(storageData)
  }

  override fun startExternalization() = if (checkIsSavingDisabled()) null else createSaveSession(getStorageData())

  protected abstract fun createSaveSession(storageData: StorageData): StateStorage.ExternalizationSession

  override fun analyzeExternalChangesAndUpdateIfNeed(componentNames: MutableSet<String>) {
    val oldData = storageDataRef.get()
    val newData = getStorageData(true)
    if (oldData == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("analyzeExternalChangesAndUpdateIfNeed: old data null, load new for ${toString()}")
      }
      componentNames.addAll(newData.getComponentNames())
    }
    else {
      val changedComponentNames = oldData.getChangedComponentNames(newData, pathMacroSubstitutor)
      if (LOG.isDebugEnabled()) {
        LOG.debug("analyzeExternalChangesAndUpdateIfNeed: changedComponentNames $changedComponentNames for ${toString()}")
      }
      if (!ContainerUtil.isEmpty(changedComponentNames)) {
        componentNames.addAll(changedComponentNames)
      }
    }
  }

  private fun setStorageData(oldStorageData: StorageData, newStorageData: StorageData?) {
    if (oldStorageData !== newStorageData && storageDataRef.getAndSet(newStorageData) !== oldStorageData) {
      LOG.warn("Old storage data is not equal to current, new storage data was set anyway")
    }
  }

  abstract class XmlElementStorageSaveSession<T : XmlElementStorage>(private val originalStorageData: StorageData, protected val storage: T) : SaveSessionBase() {
    private var copiedStorageData: StorageData? = null

    private val newLiveStates = THashMap<String, Element>()

    override fun createSaveSession() = if (storage.checkIsSavingDisabled() || (copiedStorageData == null && !originalStorageData.isDirty())) null else this

    override fun setSerializedState(component: Any, componentName: String, element: Element?) {
      if (copiedStorageData == null) {
        copiedStorageData = StorageData.setStateAndCloneIfNeed(componentName, element, originalStorageData, newLiveStates)
      }
      else {
        copiedStorageData!!.setState(componentName, element, newLiveStates)
      }
    }

    override fun save() {
      var storageData = copiedStorageData
      if (storageData == null) {
        storageData = originalStorageData
        if (!storageData.isDirty()) {
          LOG.warn("Copied storage data must be not null because original storage data is not dirty")
        }
      }

      var element = storageData.save(newLiveStates, storage.rootElementName)
      if (element == null || JDOMUtil.isEmpty(element)) {
        element = null
      }
      else if (storage.pathMacroSubstitutor != null) {
        try {
          storage.pathMacroSubstitutor.collapsePaths(element)
        }
        finally {
          storage.pathMacroSubstitutor.reset()
        }
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
      storage.setStorageData(originalStorageData, storageData)
    }

    throws(IOException::class)
    protected abstract fun saveLocally(element: Element?)
  }

  public fun updatedFromStreamProvider(changedComponentNames: MutableSet<String>, deleted: Boolean) {
    if (roamingType == RoamingType.DISABLED) {
      // storage roaming was changed to DISABLED, but settings repository has old state
      return
    }

    try {
      val newElement = if (deleted) null else loadDataFromProvider()
      val storageData = storageDataRef.get()
      if (newElement == null) {
        // if data was loaded, mark as changed all loaded components
        if (storageData != null) {
          changedComponentNames.addAll(storageData.getComponentNames())
          setStorageData(storageData, null)
        }
      }
      else if (storageData != null) {
        val newStorageData = createStorageData()
        newStorageData.loadState(newElement)
        changedComponentNames.addAll(storageData.getChangedComponentNames(newStorageData, pathMacroSubstitutor))
        setStorageData(storageData, newStorageData)
      }
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }
}
