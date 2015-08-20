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

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.StateSplitter
import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.components.impl.stores.*
import com.intellij.openapi.components.impl.stores.StateMap.getNewByteIfDiffers
import com.intellij.openapi.components.store.ReadOnlyModificationException
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.LineSeparator
import com.intellij.util.SmartList
import com.intellij.util.SystemProperties
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.SmartHashSet
import gnu.trove.THashMap
import org.jdom.Element
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

open class DirectoryBasedStorage(private val myPathMacroSubstitutor: TrackingPathMacroSubstitutor?, private val myDir: File, private val mySplitter: StateSplitter) : StateStorageBase<DirectoryStorageData>() {
  private volatile var virtualFile: VirtualFile? = null

  public fun setVirtualDir(dir: VirtualFile?) {
    virtualFile = dir
  }

  override fun analyzeExternalChangesAndUpdateIfNeed(componentNames: MutableSet<String>) {
    // todo reload only changed file, compute diff
    val oldData = storageDataRef.get()
    val newData = loadData()
    storageDataRef.set(newData)
    if (oldData == null) {
      componentNames.addAll(newData.getComponentNames())
    }
    else {
      componentNames.addAll(oldData.getComponentNames())
      componentNames.addAll(newData.getComponentNames())
    }
  }

  override fun getStateAndArchive(storageData: DirectoryStorageData, component: Any, componentName: String): Element? {
    return getCompositeStateAndArchive(storageData, componentName, mySplitter)
  }

  override fun loadData(): DirectoryStorageData {
    return fromMap(DirectoryStorageUtil.loadFrom(getVirtualFile(), myPathMacroSubstitutor))
  }

  private fun getVirtualFile(): VirtualFile? {
    var virtualFile = virtualFile
    if (virtualFile == null) {
      virtualFile = LocalFileSystem.getInstance().findFileByIoFile(myDir)
      virtualFile = virtualFile
    }
    return virtualFile
  }

  override fun startExternalization(): StateStorage.ExternalizationSession? {
    return if (checkIsSavingDisabled()) null else MySaveSession(this, getStorageData())
  }

  private class MySaveSession(private val storage: DirectoryBasedStorage, private val originalStorageData: DirectoryStorageData) : SaveSessionBase() {
    private var copiedStorageData: Map<String, Map<String, Any>>? = null

    private val dirtyFileNames = SmartHashSet<String>()
    private val removedFileNames = SmartHashSet<String>()

    private fun getFileNames(directoryStorageData: DirectoryStorageData, componentName: String): Array<String> {
      val fileToState = directoryStorageData.states.get(componentName)
      return if (fileToState == null || fileToState.isEmpty()) ArrayUtil.EMPTY_STRING_ARRAY else fileToState.keys()
    }

    override fun setSerializedState(component: Any, componentName: String, element: Element?) {
      ContainerUtil.addAll<String, String, Set<String>>(removedFileNames, *getFileNames(originalStorageData, componentName))
      if (JDOMUtil.isEmpty(element)) {
        doSetState(componentName, null, null)
      }
      else {
        for (pair in storage.mySplitter.splitState(element)) {
          removedFileNames.remove(pair.second)
          doSetState(componentName, pair.second, pair.first)
        }

        if (!removedFileNames.isEmpty()) {
          for (fileName in removedFileNames) {
            doSetState(componentName, fileName, null)
          }
        }
      }
    }

    private fun doSetState(componentName: String, fileName: String?, subState: Element?) {
      if (copiedStorageData == null) {
        copiedStorageData = setStateAndCloneIfNeed(componentName, fileName, subState, originalStorageData)
        if (copiedStorageData != null && fileName != null) {
          dirtyFileNames.add(fileName)
        }
      }
      else if (DirectoryStorageUtil.setState(copiedStorageData!!, componentName, fileName, subState) != null && fileName != null) {
        dirtyFileNames.add(fileName)
      }
    }

    override fun createSaveSession(): StateStorage.SaveSession? {
      return if (storage.checkIsSavingDisabled() || copiedStorageData == null) null else this
    }

    override fun save() {
      var dir = storage.getVirtualFile()
      if (copiedStorageData!!.isEmpty()) {
        if (dir != null && dir.exists()) {
          StorageUtil.deleteFile(this, dir)
        }
        storage.setStorageData(copiedStorageData!!)
        return
      }

      if (dir == null || !dir.isValid()) {
        dir = StorageUtil.createDir(storage.myDir, this)
        storage.virtualFile = dir
      }

      if (!dirtyFileNames.isEmpty()) {
        saveStates(dir!!)
      }
      if (dir!!.exists() && !removedFileNames.isEmpty()) {
        deleteFiles(dir)
      }

      storage.setStorageData(copiedStorageData!!)
    }

    private fun saveStates(dir: VirtualFile) {
      val storeElement = Element(StateMap.COMPONENT)

      for (componentNameToFileNameToStates in copiedStorageData!!.entrySet()) {
        for (entry in componentNameToFileNameToStates.getValue().entrySet()) {
          val fileName = entry.getKey()
          val state = entry.getValue()

          if (!dirtyFileNames.contains(fileName)) {
            return
          }

          var element: Element? = null
          try {
            element = StateMap.stateToElement(fileName, state, emptyMap<String, Element>())
            storage.myPathMacroSubstitutor?.collapsePaths(element!!)

            storeElement.setAttribute(StateMap.NAME, componentNameToFileNameToStates.getKey())
            storeElement.addContent(element)

            val file = StorageUtil.getFile(fileName, dir, this)
            // we don't write xml prolog due to historical reasons (and should not in any case)
            StorageUtil.writeFile(null, this, file, storeElement, LineSeparator.fromString(if (file.exists()) StorageUtil.loadFile(file).second else SystemProperties.getLineSeparator()), false)
          }
          catch (e: IOException) {
            StateStorageBase.LOG.error(e)
          }
          finally {
            if (element != null) {
              element.detach()
            }
          }
        }
      }
    }

    throws(IOException::class)
    private fun deleteFiles(dir: VirtualFile) {
      val token = WriteAction.start()
      try {
        for (file in dir.getChildren()) {
          if (removedFileNames.contains(file.getName())) {
            try {
              file.delete(this)
            }
            catch (e: FileNotFoundException) {
              throw ReadOnlyModificationException(file, e, null)
            }

          }
        }
      }
      finally {
        token.finish()
      }
    }
  }

  private fun setStorageData(newStates: Map<String, Map<String, Any>>) {
    storageDataRef.set(fromMap(newStates))
  }

  companion object {
    fun fromMap(map: Map<String, Map<String, Any>>): DirectoryStorageData {
      val states = THashMap<String, StateMap>(map.size())
      for (entry in map.entrySet()) {
        states.put(entry.getKey(), StateMap.fromMap(entry.getValue()))
      }
      return DirectoryStorageData(states)
    }

    private fun getCompositeStateAndArchive(storageData: DirectoryStorageData, componentName: String, SuppressWarnings("deprecation") splitter: StateSplitter): Element? {
      val fileToState = storageData.states.get(componentName)
      val state = Element(StateMap.COMPONENT)
      if (fileToState == null || fileToState.isEmpty()) {
        return state
      }

      if (splitter is StateSplitterEx) {
        for (fileName in fileToState.keys()) {
          val subState = fileToState.getStateAndArchive(fileName) ?: return null
          splitter.mergeStateInto(state, subState)
        }
      }
      else {
        val subElements = SmartList<Element>()
        for (fileName in fileToState.keys()) {
          val subState = fileToState.getStateAndArchive(fileName) ?: return null
          subElements.add(subState)
        }

        if (!subElements.isEmpty()) {
          splitter.mergeStatesInto(state, subElements.toArray<Element>(arrayOfNulls<Element>(subElements.size())))
        }
      }
      return state
    }

    public fun setStateAndCloneIfNeed(componentName: String, fileName: String?, newState: Element?, storageData: DirectoryStorageData): Map<String, Map<String, Any>>? {
      val fileToState = storageData.states.get(componentName)
      val oldState: Any? = if (fileToState == null || fileName == null) null else fileToState.get(fileName)
      if (fileName == null || newState == null || JDOMUtil.isEmpty(newState)) {
        if (fileName == null) {
          if (fileToState == null) {
            return null
          }
        }
        else if (oldState == null) {
          return null
        }

        val newStorageData = storageData.toMap()
        if (fileName == null) {
          newStorageData.remove(componentName)
        }
        else {
          val clonedFileToState = newStorageData.get(componentName)
          if (clonedFileToState!!.size() == 1) {
            newStorageData.remove(componentName)
          }
          else {
            clonedFileToState.remove(fileName)
            if (clonedFileToState.isEmpty()) {
              newStorageData.remove(componentName)
            }
          }
        }
        return newStorageData
      }

      var newBytes: ByteArray? = null
      if (oldState is Element) {
        if (JDOMUtil.areElementsEqual(oldState as Element?, newState)) {
          return null
        }
      }
      else if (oldState != null) {
        newBytes = getNewByteIfDiffers(componentName, newState, oldState as ByteArray)
        if (newBytes == null) {
          return null
        }
      }

      val newStorageData = storageData.toMap()
      put(newStorageData, componentName, fileName, newBytes ?: newState)
      return newStorageData
    }

    private fun put(states: MutableMap<String, MutableMap<String, Any>>, componentName: String, fileName: String, state: Any) {
      var fileToState: MutableMap<String, Any>? = states.get(componentName)
      if (fileToState == null) {
        fileToState = THashMap<String, Any>()
        states.put(componentName, fileToState)
      }
      fileToState.put(fileName, state)
    }
  }
}

private class DirectoryStorageData(val states: MutableMap<String, StateMap>) : StorageDataBase {
  public fun toMap(): MutableMap<String, MutableMap<String, Any>> {
    val map = THashMap<String, MutableMap<String, Any>>(states.size())
    for (entry in states.entrySet()) {
      map.put(entry.getKey(), entry.getValue().toMap())
    }
    return map
  }

  public fun getComponentNames(): Set<String> {
    return states.keySet()
  }

  override fun hasState(componentName: String): Boolean {
    val fileToState = states.get(componentName)
    return fileToState != null && fileToState.hasStates()
  }
}