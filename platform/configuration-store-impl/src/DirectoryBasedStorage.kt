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
import com.intellij.openapi.components.impl.stores.DirectoryStorageUtil
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.components.impl.stores.StateStorageBase
import com.intellij.openapi.components.store.ReadOnlyModificationException
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.LineSeparator
import com.intellij.util.SmartList
import com.intellij.util.SystemProperties
import com.intellij.util.containers.SmartHashSet
import gnu.trove.THashMap
import org.jdom.Element
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer

open class DirectoryBasedStorage(private val myPathMacroSubstitutor: TrackingPathMacroSubstitutor?, private val myDir: File, private val mySplitter: StateSplitter) : StateStorageBase<Map<String, StateMap>>() {
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
      componentNames.addAll(newData.keySet())
    }
    else {
      componentNames.addAll(oldData.keySet())
      componentNames.addAll(newData.keySet())
    }
  }

  override fun getStateAndArchive(storageData: Map<String, StateMap>, component: Any, componentName: String): Element? {
    return getCompositeStateAndArchive(storageData, componentName, mySplitter)
  }

  override fun loadData(): MutableMap<String, StateMap> {
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

  private class MySaveSession(private val storage: DirectoryBasedStorage, private val originalStates: Map<String, StateMap>) : SaveSessionBase() {
    private var copiedStorageData: MutableMap<String, MutableMap<String, Any>>? = null

    private val dirtyFileNames = SmartHashSet<String>()
    private val removedFileNames = SmartHashSet<String>()

    private fun getFileNames(states: Map<String, StateMap>, componentName: String): Array<String> {
      val fileToState = states.get(componentName)
      return if (fileToState == null || fileToState.isEmpty()) ArrayUtil.EMPTY_STRING_ARRAY else fileToState.keys()
    }

    override fun setSerializedState(component: Any, componentName: String, element: Element?) {
      removedFileNames.addAll(getFileNames(originalStates, componentName))
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
        copiedStorageData = setStateAndCloneIfNeed(componentName, fileName, subState, originalStates)
        if (copiedStorageData != null && fileName != null) {
          dirtyFileNames.add(fileName)
        }
      }
      else if (DirectoryBasedStorage.setState(copiedStorageData!!, componentName, fileName, subState) != null && fileName != null) {
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
          deleteFile(this, dir)
        }
        storage.setStorageData(copiedStorageData!!)
        return
      }

      if (dir == null || !dir.isValid()) {
        dir = createDir(storage.myDir, this)
        storage.virtualFile = dir
      }

      if (!dirtyFileNames.isEmpty()) {
        saveStates(dir)
      }
      if (dir.exists() && !removedFileNames.isEmpty()) {
        deleteFiles(dir)
      }

      storage.setStorageData(copiedStorageData!!)
    }

    private fun saveStates(dir: VirtualFile) {
      val storeElement = Element(FileStorageCoreUtil.COMPONENT)
      for (componentNameToFileNameToStates in copiedStorageData!!.entrySet()) {
        for (entry in componentNameToFileNameToStates.getValue().entrySet()) {
          val fileName = entry.getKey()
          if (!dirtyFileNames.contains(fileName)) {
            continue
          }

          var element: Element? = null
          try {
            element = StateMap.stateToElement(fileName, entry.getValue())
            storage.myPathMacroSubstitutor?.collapsePaths(element)

            storeElement.setAttribute(FileStorageCoreUtil.NAME, componentNameToFileNameToStates.getKey())
            storeElement.addContent(element)

            val file = getFile(fileName, dir, this)
            // we don't write xml prolog due to historical reasons (and should not in any case)
            writeFile(null, this, file, storeElement, LineSeparator.fromString(if (file.exists()) loadFile(file).second else SystemProperties.getLineSeparator()), false)
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

  override fun hasState(storageData: Map<String, StateMap>, componentName: String) = storageData.get(componentName)?.hasStates() ?: false

  companion object {
    fun setState(states: MutableMap<String, MutableMap<String, Any>>, componentName: String, fileName: String?, newState: Element?): Any? {
      var fileToState = states.get(componentName)
      if (fileName == null || newState == null || JDOMUtil.isEmpty(newState)) {
        if (fileToState == null) {
          return null
        }
        else if (fileName == null) {
          return states.remove(componentName)
        }
        else {
          val oldState = fileToState.remove(fileName)
          if (fileToState.isEmpty()) {
            states.remove(componentName)
          }
          return oldState
        }
      }

      if (fileToState == null) {
        fileToState = THashMap<String, Any>()
        fileToState.put(fileName, newState)
        states.put(componentName, fileToState)
      }
      else {
        val oldState = fileToState.get(fileName)
        var newBytes: ByteArray? = null
        if (oldState is Element) {
          if (JDOMUtil.areElementsEqual(oldState as Element?, newState)) {
            return null
          }
        }
        else if (oldState != null) {
          newBytes = StateMap.getNewByteIfDiffers(fileName, newState, oldState as ByteArray) ?: return null
        }

        fileToState.put(fileName, newBytes ?: newState)
      }
      return newState
    }

    public fun Map<String, StateMap>.toMutableMap(): MutableMap<String, MutableMap<String, Any>> {
      val map = THashMap<String, MutableMap<String, Any>>(size())
      for (entry in entrySet()) {
        map.put(entry.getKey(), entry.getValue().toMutableMap())
      }
      return map
    }

    fun fromMap(map: Map<String, Map<String, Any>>): MutableMap<String, StateMap> {
      val states = THashMap<String, StateMap>(map.size())
      for (entry in map.entrySet()) {
        states.put(entry.getKey(), StateMap.fromMap(entry.getValue()))
      }
      return states
    }

    private fun getCompositeStateAndArchive(states: Map<String, StateMap>, componentName: String, SuppressWarnings("deprecation") splitter: StateSplitter): Element? {
      val fileToState = states.get(componentName)
      val state = Element(FileStorageCoreUtil.COMPONENT)
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

    public fun setStateAndCloneIfNeed(componentName: String, fileName: String?, newState: Element?, oldStates: Map<String, StateMap>): MutableMap<String, MutableMap<String, Any>>? {
      val fileToState = oldStates.get(componentName)
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

        val newStorageData = oldStates.toMutableMap()
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
        newBytes = StateMap.getNewByteIfDiffers(componentName, newState, oldState as ByteArray)
        if (newBytes == null) {
          return null
        }
      }

      val newStorageData = oldStates.toMutableMap()
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

private val NON_EXISTENT_FILE_DATA = Pair.create<ByteArray, String>(null, SystemProperties.getLineSeparator())

/**
 * @return pair.first - file contents (null if file does not exist), pair.second - file line separators
 */
private fun loadFile(file: VirtualFile?): Pair<ByteArray, String> {
  if (file == null || !file.exists()) {
    return NON_EXISTENT_FILE_DATA
  }

  val bytes = file.contentsToByteArray()
  var lineSeparator: String? = file.getDetectedLineSeparator()
  if (lineSeparator == null) {
    lineSeparator = detectLineSeparators(CharsetToolkit.UTF8_CHARSET.decode(ByteBuffer.wrap(bytes)), null).getSeparatorString()
  }
  return Pair.create<ByteArray, String>(bytes, lineSeparator)
}