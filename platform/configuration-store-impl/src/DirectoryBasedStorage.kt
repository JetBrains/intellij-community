// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.application.options.PathMacrosCollector
import com.intellij.configurationStore.schemeManager.createDir
import com.intellij.configurationStore.schemeManager.getOrCreateChild
import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.settings.SettingsController
import com.intellij.util.LineSeparator
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.*
import java.util.*

@ApiStatus.Internal
open class DirectoryBasedStorage(
  private val dir: Path,
  @Suppress("DEPRECATION", "removal") private val splitter: com.intellij.openapi.components.StateSplitter,
  private val pathMacroSubstitutor: PathMacroSubstitutor? = null,
  override val controller: SettingsController? = null,
) : StateStorageBase<StateMap>() {
  private var componentName: String? = null
  @Volatile private var nameToLineSeparatorMap: Map<String, LineSeparator?> = @Suppress("RemoveRedundantQualifierName") java.util.Map.of()
  @Volatile private var cachedVirtualFile: VirtualFile? = null

  override val roamingType: RoamingType?
    get() = null

  public override fun loadData(): StateMap {
    val (elementMap, separatorMap) = loadComponentsAndDetectLineSeparator()
    nameToLineSeparatorMap = separatorMap
    return StateMap.fromMap(elementMap)
  }

  private fun loadComponentsAndDetectLineSeparator(): Pair<Map<String, Element>, Map<String, LineSeparator?>> {
    try {
      Files.newDirectoryStream(dir).use { files ->
        val fileToState = HashMap<String, Element>()
        val fileToSeparator = HashMap<String, LineSeparator?>()

        for (file in files) {
          // ignore system files like .DS_Store on Mac
          if (!file.toString().endsWith(ComponentStorageUtil.DEFAULT_EXT, ignoreCase = true)) {
            continue
          }

          try {
            val (element, separator) = loadDataAndDetectLineSeparator(file)
            val componentName = ComponentStorageUtil.getComponentNameIfValid(element) ?: continue
            if (element.name != ComponentStorageUtil.COMPONENT) {
              LOG.error("Incorrect root tag name (${element.name}) in $file")
              continue
            }

            val elementChildren = element.children
            if (elementChildren.isEmpty()) {
              continue
            }

            val state = elementChildren[0].detach()
            if (state.isEmpty) {
              continue
            }

            if (pathMacroSubstitutor != null) {
              pathMacroSubstitutor.expandPaths(state)
              if (pathMacroSubstitutor is TrackingPathMacroSubstitutor) {
                pathMacroSubstitutor.addUnknownMacros(componentName, PathMacrosCollector.getMacroNames(state))
              }
            }

            val name = file.fileName.toString()
            fileToState.put(name, state)
            fileToSeparator.put(name, separator)
          }
          catch (e: Throwable) {
            if (e.message!!.startsWith("Unexpected End-of-input in prolog")) {
              LOG.warn("Ignore empty file $file")
            }
            else {
              LOG.warn("Unable to load state from $file", e)
            }
          }
        }
        return Pair(fileToState, fileToSeparator)
      }
    }
    catch (e: DirectoryIteratorException) {
      throw e.cause!!
    }
    catch (_: NoSuchFileException) {
      @Suppress("RemoveRedundantQualifierName")
      return Pair(java.util.Map.of(), java.util.Map.of())
    }
    catch (_: NotDirectoryException) {
      @Suppress("RemoveRedundantQualifierName")
      return Pair(java.util.Map.of(), java.util.Map.of())
    }
  }

  private fun getLineSeparator(name: String): LineSeparator = nameToLineSeparatorMap[name] ?: LineSeparator.getSystemLineSeparator()

  override fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<in String>) {
    // todo reload only changed file, compute diff
    val newData = loadData()
    storageDataRef.set(newData)
    componentName?.let {
      componentNames.add(it)
    }
  }

  override fun getSerializedState(storageData: StateMap, component: Any?, componentName: String, archive: Boolean): Element? {
    this.componentName = componentName

    if (storageData.isEmpty()) {
      return null
    }

    // on load, `FileStorageCoreUtil` checks both `component` and `name` attributes
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

  override fun createSaveSessionProducer(): SaveSessionProducer? {
    return if (checkIsSavingDisabled()) null else DirectorySaveSessionProducer(storage = this, originalStates = getStorageData())
  }

  private class DirectorySaveSessionProducer(
    private val storage: DirectoryBasedStorage,
    private val originalStates: StateMap
  ) : SaveSessionProducerBase(), SaveSession, DirectoryBasedSaveSessionProducer {
    private var copiedStorageData: MutableMap<String, Any>? = null

    override val controller: SettingsController?
      get() = null

    override val roamingType: RoamingType?
      get() = null

    private val dirtyFileNames = HashSet<String>()
    private var isSomeFileRemoved = false

    override fun setSerializedState(componentName: String, element: Element?) {
      storage.componentName = componentName

      @Suppress("removal") val stateAndFileNameList = if (JDOMUtil.isEmpty(element)) Collections.emptyList() else storage.splitter.splitState(element!!)
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
        copiedStorageData = setStateAndCloneIfNeeded(key = fileName, newState = subState, oldStates = originalStates, newLiveStates = null)
        if (copiedStorageData != null) {
          dirtyFileNames.add(fileName)
        }
      }
      else if (updateState(states = copiedStorageData!!, key = fileName, newState = subState, newLiveStates = null)) {
        dirtyFileNames.add(fileName)
      }
    }

    override fun createSaveSession(): SaveSession? = if (storage.checkIsSavingDisabled() || copiedStorageData == null) null else this

    override suspend fun save(events: MutableList<VFileEvent>?) = blockingContext { doSave(useVfs = false, events = events) }

    override fun saveBlocking() = doSave(useVfs = true, events = null)

    private fun doSave(useVfs: Boolean, events: MutableList<VFileEvent>?) {
      val stateMap = StateMap.fromMap(copiedStorageData!!)

      if (copiedStorageData!!.isEmpty()) {
        deleteDirectory(useVfs, events)
      }
      else {
        if (dirtyFileNames.isNotEmpty()) {
          saveStates(stateMap, useVfs, events)
        }
        if (isSomeFileRemoved) {
          deleteFiles(useVfs, events)
        }
      }

      storage.setStorageData(stateMap)
    }

    private fun deleteDirectory(useVfs: Boolean, events: MutableList<VFileEvent>?) {
      val dir = storage.getVirtualFile()
      if (dir != null && dir.exists()) {
        if (useVfs) {
          dir.delete(/*requestor =*/ this)
        }
        else {
          NioFiles.deleteRecursively(storage.dir)
          if (events != null) {
            events += VFileDeleteEvent(/*requestor =*/ this, dir)
          }
        }
      }
    }

    private fun saveStates(states: StateMap, useVfs: Boolean, events: MutableList<VFileEvent>?) {
      val macroManager =
        if (storage.pathMacroSubstitutor == null) null else (storage.pathMacroSubstitutor as TrackingPathMacroSubstitutorImpl).macroManager

      val dir = if (useVfs) {
        var dir = storage.getVirtualFile()
        if (dir == null || !dir.exists()) {
          dir = createDir(storage.dir, requestor = this)
          storage.cachedVirtualFile = dir
        }
        dir
      }
      else {
        NioFiles.createDirectories(storage.dir)
        storage.getVirtualFile()
      }

      for (fileName in states.keys()) {
        if (!dirtyFileNames.contains(fileName)) continue
        val element = states.getElement(fileName) ?: continue

        val rootAttributes = mapOf(ComponentStorageUtil.NAME to storage.componentName!!)
        val debugString = storage.dir.toString()
        val dataWriter = XmlDataWriter(ComponentStorageUtil.COMPONENT, listOf(element), rootAttributes, macroManager, debugString)

        try {
          if (useVfs) {
            val file = dir!!.getOrCreateChild(requestor = this, fileName, directory = false)
            writeFile(cachedFile = null, requestor = this, file, dataWriter, getOrDetectLineSeparator(file), prependXmlProlog = false)
          }
          else {
            val file = storage.dir.resolve(fileName)
            writeFile(file, requestor = this, dataWriter, storage.getLineSeparator(fileName), prependXmlProlog = false)
            if (events != null) {
              val vFile = dir?.findChild(fileName)
              when {
                vFile != null -> events += updatingEvent(file, vFile)
                dir != null -> events += creationEvent(file, dir)
              }
            }
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

    private fun deleteFiles(useVfs: Boolean, events: MutableList<VFileEvent>?) {
      val copiedStorageData = copiedStorageData!!
      val dir = storage.getVirtualFile()

      if (useVfs) {
        if (dir == null || !dir.exists()) {
          return
        }

        for (file in dir.children) {
          val fileName = file.name
          if (fileName.endsWith(ComponentStorageUtil.DEFAULT_EXT) && !copiedStorageData.containsKey(fileName)) {
            if (!file.isWritable) {
              throw ReadOnlyModificationException(file, null)
            }
            file.delete(/*requestor =*/ this)
          }
        }
      }
      else {
        for (file in NioFiles.list(storage.dir)) {
          val fileName = file.fileName.toString()
          if (fileName.endsWith(ComponentStorageUtil.DEFAULT_EXT) && !copiedStorageData.containsKey(fileName)) {
            Files.deleteIfExists(file)
            if (events != null) {
              dir?.findChild(fileName)?.let { events.add(VFileDeleteEvent(/*requestor =*/ this, it)) }
            }
          }
        }
      }
    }
  }

  private fun setStorageData(newStates: StateMap) {
    storageDataRef.set(newStates)
  }

  override fun toString(): String = "${javaClass.simpleName}(dir=$dir, componentName=$componentName)"
}

internal interface DirectoryBasedSaveSessionProducer : SaveSessionProducer {
  fun setFileState(fileName: String, componentName: String, element: Element?)
}
