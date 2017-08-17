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

import com.intellij.configurationStore.schemeManager.createDir
import com.intellij.configurationStore.schemeManager.getOrCreateChild
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.components.StateSplitter
import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.components.impl.stores.DirectoryStorageUtil
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.LineSeparator
import com.intellij.util.SmartList
import com.intellij.util.SystemProperties
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.isEmpty
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jdom.Element
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Path

abstract class DirectoryBasedStorageBase(@Suppress("DEPRECATION") protected val splitter: StateSplitter,
                                         protected val pathMacroSubstitutor: TrackingPathMacroSubstitutor? = null) : StateStorageBase<StateMap>() {
  protected var componentName: String? = null

  protected abstract val virtualFile: VirtualFile?

  override fun loadData() = StateMap.fromMap(DirectoryStorageUtil.loadFrom(virtualFile, pathMacroSubstitutor))

  override fun startExternalization(): StateStorage.ExternalizationSession? = null

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

  override fun hasState(storageData: StateMap, componentName: String) = storageData.hasStates()
}

open class DirectoryBasedStorage(private val dir: Path,
                                 @Suppress("DEPRECATION") splitter: StateSplitter,
                                 pathMacroSubstitutor: TrackingPathMacroSubstitutor? = null) : DirectoryBasedStorageBase(splitter, pathMacroSubstitutor) {
  private @Volatile var cachedVirtualFile: VirtualFile? = null

  override val virtualFile: VirtualFile?
    get() {
      var result = cachedVirtualFile
      if (result == null) {
        result = LocalFileSystem.getInstance().findFileByPath(dir.systemIndependentPath)
        cachedVirtualFile = result
      }
      return result
    }

  internal fun setVirtualDir(dir: VirtualFile?) {
    cachedVirtualFile = dir
  }

  override fun startExternalization(): StateStorage.ExternalizationSession? = if (checkIsSavingDisabled()) null else MySaveSession(this, getStorageData())

  private class MySaveSession(private val storage: DirectoryBasedStorage, private val originalStates: StateMap) : SaveSessionBase() {
    private var copiedStorageData: MutableMap<String, Any>? = null

    private val dirtyFileNames = SmartHashSet<String>()
    private var someFileRemoved = false

    override fun setSerializedState(componentName: String, element: Element?) {
      storage.componentName = componentName

      if (element.isEmpty()) {
        if (copiedStorageData != null) {
          copiedStorageData!!.clear()
        }
        else if (!originalStates.isEmpty()) {
          copiedStorageData = THashMap<String, Any>()
        }
      }
      else {
        val stateAndFileNameList = storage.splitter.splitState(element!!)
        val existingFiles = THashSet<String>(stateAndFileNameList.size)
        for (pair in stateAndFileNameList) {
          doSetState(pair.second, pair.first)
          existingFiles.add(pair.second)
        }

        for (key in originalStates.keys()) {
          if (existingFiles.contains(key)) {
            continue
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

      var dir = storage.virtualFile
      if (copiedStorageData!!.isEmpty()) {
        if (dir != null && dir.exists()) {
          deleteFile(this, dir)
        }
        storage.setStorageData(stateMap)
        return
      }

      if (dir == null || !dir.isValid) {
        dir = createDir(storage.dir, this)
        storage.cachedVirtualFile = dir
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
          element = states.getElement(fileName) ?: continue
          storage.pathMacroSubstitutor?.collapsePaths(element)

          storeElement.setAttribute(FileStorageCoreUtil.NAME, storage.componentName!!)
          storeElement.addContent(element)

          val file = dir.getOrCreateChild(fileName, this)
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
      runUndoTransparentWriteAction {
        for (file in dir.children) {
          val fileName = file.name
          if (fileName.endsWith(FileStorageCoreUtil.DEFAULT_EXT) && !copiedStorageData!!.containsKey(fileName)) {
            if (file.isWritable) {
              file.delete(this)
            }
            else {
              throw ReadOnlyModificationException(file, null)
            }
          }
        }
      }
    }
  }

  private fun setStorageData(newStates: StateMap) {
    storageDataRef.set(newStates)
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
  val lineSeparator = file.detectedLineSeparator ?: detectLineSeparators(Charsets.UTF_8.decode(ByteBuffer.wrap(bytes)), null).separatorString
  return Pair.create<ByteArray, String>(bytes, lineSeparator)
}