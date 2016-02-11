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

import com.intellij.openapi.application.runWriteAction
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
import com.intellij.util.LineSeparator
import com.intellij.util.SmartList
import com.intellij.util.SystemProperties
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.systemIndependentPath
import gnu.trove.THashMap
import org.jdom.Element
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Path

open class DirectoryBasedStorage(private val dir: Path,
                                 private val splitter: StateSplitter,
                                 private val pathMacroSubstitutor: TrackingPathMacroSubstitutor? = null) : StateStorageBase<StateMap>() {
  private @Volatile var virtualFile: VirtualFile? = null

  private var componentName: String? = null

  fun setVirtualDir(dir: VirtualFile?) {
    virtualFile = dir
  }

  override fun analyzeExternalChangesAndUpdateIfNeed(componentNames: MutableSet<String>) {
    // todo reload only changed file, compute diff
    val newData = loadData()
    storageDataRef.set(newData)
    if (componentName != null) {
      componentNames.add(componentName!!)
    }
  }

  override fun getSerializedState(storageData: StateMap, component: Any?, componentName: String, archive: Boolean): Element? {
    this.componentName = componentName

    if (storageData.isEmpty()) {
      return null
    }

    val state = Element(FileStorageCoreUtil.COMPONENT)
    if (splitter is StateSplitterEx) {
      for (fileName in storageData.keys()) {
        val subState = storageData.getState(fileName, archive) ?: return null
        splitter.mergeStateInto(state, subState)
      }
    }
    else {
      val subElements = SmartList<Element>()
      for (fileName in storageData.keys()) {
        val subState = storageData.getState(fileName, archive) ?: return null
        subElements.add(subState)
      }

      if (!subElements.isEmpty()) {
        splitter.mergeStatesInto(state, subElements.toTypedArray())
      }
    }
    return state
  }

  override fun loadData() = StateMap.fromMap(DirectoryStorageUtil.loadFrom(getVirtualFile(), pathMacroSubstitutor))

  private fun getVirtualFile(): VirtualFile? {
    var result = virtualFile
    if (result == null) {
      result = LocalFileSystem.getInstance().findFileByPath(dir.systemIndependentPath)
      virtualFile = result
    }
    return result
  }

  override fun startExternalization(): StateStorage.ExternalizationSession? = if (checkIsSavingDisabled()) null else MySaveSession(this, getStorageData())

  private class MySaveSession(private val storage: DirectoryBasedStorage, private val originalStates: StateMap) : SaveSessionBase() {
    private var copiedStorageData: MutableMap<String, Any>? = null

    private val dirtyFileNames = SmartHashSet<String>()
    private var someFileRemoved = false

    override fun setSerializedState(componentName: String, element: Element?) {
      storage.componentName = componentName

      if (JDOMUtil.isEmpty(element)) {
        if (copiedStorageData != null) {
          copiedStorageData!!.clear()
        }
        else if (!originalStates.isEmpty()) {
          copiedStorageData = THashMap<String, Any>()
        }
      }
      else {
        val stateAndFileNameList = storage.splitter.splitState(element!!)
        for (pair in stateAndFileNameList) {
          doSetState(pair.second, pair.first)
        }

        outerLoop@
        for (key in originalStates.keys()) {
          for (pair in stateAndFileNameList) {
            if (pair.second == key) {
              continue@outerLoop
            }
          }

          if (copiedStorageData == null) {
            copiedStorageData = originalStates.toMutableMap()
          }
          someFileRemoved = true
          copiedStorageData!!.remove(key)
        }
      }
    }

    private fun doSetState(fileName: String, subState: Element) {
      if (copiedStorageData == null) {
        copiedStorageData = setStateAndCloneIfNeed(fileName, subState, originalStates)
        if (copiedStorageData != null) {
          dirtyFileNames.add(fileName)
        }
      }
      else if (updateState(copiedStorageData!!, fileName, subState)) {
        dirtyFileNames.add(fileName)
      }
    }

    override fun createSaveSession() = if (storage.checkIsSavingDisabled() || copiedStorageData == null) null else this

    override fun save() {
      val stateMap = StateMap.fromMap(copiedStorageData!!)

      var dir = storage.getVirtualFile()
      if (copiedStorageData!!.isEmpty()) {
        if (dir != null && dir.exists()) {
          deleteFile(this, dir)
        }
        storage.setStorageData(stateMap)
        return
      }

      if (dir == null || !dir.isValid) {
        dir = createDir(storage.dir, this)
        storage.virtualFile = dir
      }

      if (!dirtyFileNames.isEmpty) {
        saveStates(dir, stateMap)
      }
      if (someFileRemoved && dir.exists()) {
        deleteFiles(dir)
      }

      storage.setStorageData(stateMap)
    }

    private fun saveStates(dir: VirtualFile, states: StateMap) {
      val storeElement = Element(FileStorageCoreUtil.COMPONENT)
      for (fileName in states.keys()) {
        if (!dirtyFileNames.contains(fileName)) {
          continue
        }

        var element: Element? = null
        try {
          element = states.getElement(fileName, null)
          storage.pathMacroSubstitutor?.collapsePaths(element)

          storeElement.setAttribute(FileStorageCoreUtil.NAME, storage.componentName!!)
          storeElement.addContent(element)

          val file = getFile(fileName, dir, this)
          // we don't write xml prolog due to historical reasons (and should not in any case)
          writeFile(null, this, file, storeElement, LineSeparator.fromString(if (file.exists()) loadFile(file).second else SystemProperties.getLineSeparator()), false)
        }
        catch (e: IOException) {
          LOG.error(e)
        }
        finally {
          if (element != null) {
            element.detach()
          }
        }
      }
    }

    private fun deleteFiles(dir: VirtualFile) {
      runWriteAction {
        for (file in dir.children) {
          val fileName = file.name
          if (fileName.endsWith(FileStorageCoreUtil.DEFAULT_EXT) && !copiedStorageData!!.containsKey(fileName)) {
            try {
              file.delete(this)
            }
            catch (e: FileNotFoundException) {
              throw ReadOnlyModificationException(file, e, null)
            }
          }
        }
      }
    }
  }

  private fun setStorageData(newStates: StateMap) {
    storageDataRef.set(newStates)
  }

  override fun hasState(storageData: StateMap, componentName: String) = storageData.hasStates()
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
  var lineSeparator: String? = file.detectedLineSeparator
  if (lineSeparator == null) {
    lineSeparator = detectLineSeparators(CharsetToolkit.UTF8_CHARSET.decode(ByteBuffer.wrap(bytes)), null).separatorString
  }
  return Pair.create<ByteArray, String>(bytes, lineSeparator)
}