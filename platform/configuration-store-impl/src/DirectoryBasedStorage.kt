// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.createDir
import com.intellij.configurationStore.schemeManager.getOrCreateChild
import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.LineSeparator
import org.jdom.Element
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

open class DirectoryBasedStorage(
  private val dir: Path,
  @Suppress("DEPRECATION", "removal") private val splitter: com.intellij.openapi.components.StateSplitter,
  private val pathMacroSubstitutor: PathMacroSubstitutor? = null
) : StateStorageBase<StateMap>() {
  protected var componentName: String? = null
  @Volatile private var nameToLineSeparatorMap: Map<String, LineSeparator?> = emptyMap()

  public override fun loadData(): StateMap {
    val (elementMap, separatorMap) = ComponentStorageUtil.load(dir, pathMacroSubstitutor)
    nameToLineSeparatorMap = separatorMap
    return StateMap.fromMap(elementMap)
  }

  private fun getLineSeparator(name: String): LineSeparator =
    nameToLineSeparatorMap[name] ?: LineSeparator.getSystemLineSeparator()

  override fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<in String>) {
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

    // on load, `FileStorageCoreUtil` checks both component and name attributes
    // (critically important for the external store case, where we have only in-project artifacts, but not external)
    val state = Element(ComponentStorageUtil.COMPONENT).setAttribute(ComponentStorageUtil.NAME, componentName)
    if (splitter is StateSplitterEx) {
      for (fileName in storageData.keys()) {
        val subState = storageData.getState(fileName, archive) ?: return null
        splitter.mergeStateInto(state, subState.clone())
      }
    }
    else {
      val subElements = ArrayList<Element>()
      for (fileName in storageData.keys()) {
        val subState = storageData.getState(fileName, archive) ?: return null
        subElements.add(subState.clone())
      }

      if (subElements.isNotEmpty()) {
        @Suppress("removal")
        splitter.mergeStatesInto(state, subElements.toTypedArray())
      }
    }
    return state
  }

  @Volatile
  private var cachedVirtualFile: VirtualFile? = null

  private fun getVirtualFile(): VirtualFile? {
    var result = cachedVirtualFile
    if (result == null) {
      result = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)
      cachedVirtualFile = result
    }
    return result
  }

  internal fun setVirtualDir(dir: VirtualFile?) {
    cachedVirtualFile = dir
  }

  override fun createSaveSessionProducer(): SaveSessionProducer? =
    if (checkIsSavingDisabled()) null else MySaveSessionProducer(this, getStorageData())

  private class MySaveSessionProducer(
    private val storage: DirectoryBasedStorage,
    private val originalStates: StateMap
  ) : SaveSessionProducerBase(), SaveSession, DirectoryBasedSaveSessionProducer {
    private var copiedStorageData: MutableMap<String, Any>? = null

    private val dirtyFileNames = HashSet<String>()
    private var isSomeFileRemoved = false

    override fun setSerializedState(componentName: String, element: Element?) {
      storage.componentName = componentName

      @Suppress("removal") val stateAndFileNameList = if (JDOMUtil.isEmpty(element)) emptyList() else storage.splitter.splitState(element!!)
      if (stateAndFileNameList.isEmpty()) {
        if (copiedStorageData != null) {
          copiedStorageData!!.clear()
        }
        else if (!originalStates.isEmpty()) {
          copiedStorageData = HashMap()
        }
        return
      }

      val existingFiles = HashSet<String>(stateAndFileNameList.size)
      for (pair in stateAndFileNameList) {
        doSetState(pair.second, pair.first)
        existingFiles.add(pair.second)
      }

      for (key in originalStates.keys()) {
        if (existingFiles.contains(key)) {
          continue
        }
        removeFileData(key)
      }
    }

    override fun setFileState(fileName: String, componentName: String, element: Element?) {
      storage.componentName = componentName
      if (element != null) {
        doSetState(fileName, element)
      }
      else {
        removeFileData(fileName)
      }
    }

    private fun removeFileData(fileName: String) {
      if (copiedStorageData == null) {
        copiedStorageData = originalStates.toMutableMap()
      }
      isSomeFileRemoved = true
      copiedStorageData!!.remove(fileName)
    }

    private fun doSetState(fileName: String, subState: Element) {
      if (copiedStorageData == null) {
        copiedStorageData = setStateAndCloneIfNeeded(fileName, subState, originalStates)
        if (copiedStorageData != null) {
          dirtyFileNames.add(fileName)
        }
      }
      else if (updateState(copiedStorageData!!, fileName, subState)) {
        dirtyFileNames.add(fileName)
      }
    }

    override fun createSaveSession(): SaveSession? =
      if (storage.checkIsSavingDisabled() || copiedStorageData == null) null else this

    override fun saveBlocking() {
      val stateMap = StateMap.fromMap(copiedStorageData!!)
      if (copiedStorageData!!.isEmpty()) {
        deleteDirectory()
      }
      else {
        if (dirtyFileNames.isNotEmpty()) {
          saveStates(stateMap)
        }
        if (isSomeFileRemoved) {
          deleteFiles()
        }
      }
      storage.setStorageData(stateMap)
    }

    private fun deleteDirectory() {
      if (storage.isUseVfsForWrite) {
        val dir = storage.getVirtualFile()
        if (dir != null && dir.exists()) {
          dir.delete(this)
        }
      }
      else {
        NioFiles.deleteRecursively(storage.dir)
      }
    }

    private fun saveStates(states: StateMap) {
      val macroManager =
        if (storage.pathMacroSubstitutor == null) null else (storage.pathMacroSubstitutor as TrackingPathMacroSubstitutorImpl).macroManager

      for (fileName in states.keys()) {
        if (!dirtyFileNames.contains(fileName)) continue
        val element = states.getElement(fileName) ?: continue

        val rootAttributes = mapOf(ComponentStorageUtil.NAME to storage.componentName!!)
        val debugString = storage.dir.pathString
        val dataWriter = XmlDataWriter(ComponentStorageUtil.COMPONENT, listOf(element), rootAttributes, macroManager, debugString)

        try {
          if (storage.isUseVfsForWrite) {
            var dir = storage.cachedVirtualFile
            if (dir == null || !dir.exists()) {
              dir = storage.getVirtualFile()
              if (dir == null || !dir.exists()) {
                dir = createDir(storage.dir, this)
                storage.cachedVirtualFile = dir
              }
            }
            val file = dir.getOrCreateChild(fileName, this)
            writeFile(cachedFile = null, requestor = this, file, dataWriter, getOrDetectLineSeparator(file), prependXmlProlog = false)
          }
          else {
            val file = storage.dir.resolve(fileName)
            writeFile(file, requestor = this, dataWriter, storage.getLineSeparator(fileName), prependXmlProlog = false)
          }
        }
        catch (e: IOException) {
          LOG.error(e)
        }
      }
    }

    private fun getOrDetectLineSeparator(file: VirtualFile): LineSeparator {
      if (!file.exists()) {
        return LineSeparator.getSystemLineSeparator()
      }
      file.detectedLineSeparator?.let {
        return LineSeparator.fromString(it)
      }
      val lineSeparator = storage.getLineSeparator(file.name)
      file.detectedLineSeparator = lineSeparator.separatorString
      return lineSeparator
    }

    private fun deleteFiles() {
      val copiedStorageData = copiedStorageData!!
      if (storage.isUseVfsForWrite) {
        val dir = storage.getVirtualFile()
        if (dir == null || !dir.exists()) return
        for (file in dir.children) {
          val fileName = file.name
          if (fileName.endsWith(ComponentStorageUtil.DEFAULT_EXT) && !copiedStorageData.containsKey(fileName)) {
            if (!file.isWritable) throw ReadOnlyModificationException(file, null)
            file.delete(/*requestor =*/ this)
          }
        }
      }
      else {
        for (file in storage.dir.listDirectoryEntries()) {
          val fileName = file.fileName.toString()
          if (fileName.endsWith(ComponentStorageUtil.DEFAULT_EXT) && !copiedStorageData.containsKey(fileName)) {
            file.deleteIfExists()
          }
        }
      }
    }
  }

  private fun setStorageData(newStates: StateMap) {
    storageDataRef.set(newStates)
  }

  override fun toString(): String = "${javaClass.simpleName}(dir=${dir}, componentName=$componentName)"
}

internal interface DirectoryBasedSaveSessionProducer : SaveSessionProducer {
  fun setFileState(fileName: String, componentName: String, element: Element?)
}
