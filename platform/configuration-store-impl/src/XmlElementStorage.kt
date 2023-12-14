// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.LargeFileWriteRequestor
import com.intellij.openapi.vfs.SafeWriteRequestor
import com.intellij.util.LineSeparator
import com.intellij.util.SmartList
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.io.delete
import com.intellij.util.io.outputStream
import com.intellij.util.io.safeOutputStream
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.jdom.Attribute
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.io.FileNotFoundException
import java.io.OutputStream
import java.io.Writer
import java.nio.file.Path
import kotlin.math.min

abstract class XmlElementStorage protected constructor(val fileSpec: String,
                                                       protected val rootElementName: String?,
                                                       private val pathMacroSubstitutor: PathMacroSubstitutor? = null,
                                                       storageRoamingType: RoamingType,
                                                       private val provider: StreamProvider? = null) : StorageBaseEx<StateMap>() {
  override val saveStorageDataOnReload: Boolean
    get() {
      return provider == null || provider.saveStorageDataOnReload
    }

  internal val rawRoamingType = storageRoamingType
  private val effectiveRoamingType = getEffectiveRoamingType(storageRoamingType, fileSpec)

  protected abstract fun loadLocalData(): Element?

  final override fun getSerializedState(storageData: StateMap, component: Any?, componentName: String, archive: Boolean): Element? {
    return storageData.getState(componentName, archive)
  }

  final override fun archiveState(storageData: StateMap, componentName: String, serializedState: Element?) {
    storageData.archive(componentName, serializedState)
  }

  final override fun hasState(storageData: StateMap, componentName: String) = storageData.hasState(componentName)

  final override fun loadData() = loadElement()?.let { loadState(it) } ?: StateMap.EMPTY

  private fun loadElement(useStreamProvider: Boolean = true): Element? {
    var element: Element? = null
    try {
      val isLoadLocalData: Boolean
      if (useStreamProvider && provider != null) {
        isLoadLocalData = !provider.read(fileSpec, effectiveRoamingType) { inputStream ->
          inputStream?.let {
            element = JDOMUtil.load(inputStream)
            providerDataStateChanged(createDataWriterForElement(element = element!!,
                                                                storageFilePathForDebugPurposes = toString()), DataStateChanged.LOADED)
          }
        }
      }
      else {
        isLoadLocalData = true
      }

      if (isLoadLocalData) {
        element = loadLocalData()
      }
    }
    catch (e: FileNotFoundException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error("Cannot load data for $fileSpec", e)
    }
    return element
  }

  protected open fun providerDataStateChanged(writer: DataWriter?, type: DataStateChanged) {
  }

  private fun loadState(element: Element): StateMap {
    beforeElementLoaded(element)
    return StateMap.fromMap(FileStorageCoreUtil.load(element, pathMacroSubstitutor))
  }

  final override fun createSaveSessionProducer(): SaveSessionProducer? {
    return if (checkIsSavingDisabled()) null else createSaveSession(getStorageData())
  }

  protected abstract fun createSaveSession(states: StateMap): SaveSessionProducer

  override fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<in String>) {
    LOG.debug("Running analyzeExternalChangesAndUpdateIfNeeded")
    val oldData = storageDataRef.get()
    val newData = getStorageData(true)
    if (oldData == null) {
      LOG.debug { "analyzeExternalChangesAndUpdateIfNeeded: old data null, load new for ${toString()}" }
      componentNames.addAll(newData.keys())
    }
    else {
      val changedComponentNames = getChangedComponentNames(oldData, newData)
      LOG.debug { "Changed components: $changedComponentNames" }
      if (changedComponentNames.isNotEmpty()) {
        LOG.debug { "analyzeExternalChangesAndUpdateIfNeeded: changedComponentNames $changedComponentNames for ${toString()}" }
        componentNames.addAll(changedComponentNames)
      }
    }
  }

  private fun setStates(oldStorageData: StateMap, newStorageData: StateMap?) {
    if (oldStorageData !== newStorageData && storageDataRef.getAndSet(newStorageData) !== oldStorageData) {
      LOG.warn("Old storage data is not equal to current, new storage data was set anyway")
    }
  }

  abstract class XmlElementStorageSaveSessionProducer<T : XmlElementStorage>(private val originalStates: StateMap,
                                                                             protected val storage: T) : SaveSessionProducerBase() {
    private var copiedStates: MutableMap<String, Any>? = null

    private var newLiveStates: MutableMap<String, Element>? = HashMap()

    protected open fun isSaveAllowed() = !storage.checkIsSavingDisabled()

    final override fun createSaveSession(): SaveSession? {
      if (copiedStates == null || !isSaveAllowed()) {
        return null
      }

      val stateMap = StateMap.fromMap(copiedStates!!)
      val elements = save(stateMap, newLiveStates ?: throw IllegalStateException("createSaveSession was already called"))
      newLiveStates = null

      val writer: DataWriter?
      if (elements == null) {
        writer = null
      }
      else {
        val rootAttributes = LinkedHashMap<String, String>()
        storage.beforeElementSaved(elements, rootAttributes)
        val macroManager = if (storage.pathMacroSubstitutor == null) null else (storage.pathMacroSubstitutor as TrackingPathMacroSubstitutorImpl).macroManager
        writer = XmlDataWriter(storage.rootElementName, elements, rootAttributes, macroManager, storage.toString())
      }

      // during beforeElementSaved() elements can be modified and so, even if our save() never returns empty list, at this point, elements can be an empty list
      return SaveExecutor(elements, writer, stateMap)
    }

    private inner class SaveExecutor(private val elements: MutableList<Element>?,
                                     private val writer: DataWriter?,
                                     private val stateMap: StateMap) : SaveSession, SafeWriteRequestor, LargeFileWriteRequestor {
      override fun saveBlocking() {
        var isSavedLocally = false
        val provider = storage.provider
        if (elements == null) {
          if (provider == null || !provider.delete(storage.fileSpec, storage.effectiveRoamingType)) {
            isSavedLocally = true
            saveLocally(writer)
          }
        }
        else if (provider != null && provider.isApplicable(storage.fileSpec, storage.effectiveRoamingType)) {
          // we should use standard line-separator (\n) - stream provider can share file content on any OS
          provider.write(storage.fileSpec, writer!!.toBufferExposingByteArray().toByteArray(), storage.effectiveRoamingType)
        }
        else {
          isSavedLocally = true
          saveLocally(writer)
        }

        if (!isSavedLocally) {
          storage.providerDataStateChanged(writer, DataStateChanged.SAVED)
        }

        storage.setStates(originalStates, stateMap)
      }
    }

    override fun setSerializedState(componentName: String, element: Element?) {
      val newLiveStates = newLiveStates ?: throw IllegalStateException("createSaveSession was already called")

      val normalized = element?.normalizeRootName()
      if (copiedStates == null) {
        copiedStates = setStateAndCloneIfNeeded(componentName, normalized, originalStates, newLiveStates)
      }
      else {
        updateState(copiedStates!!, componentName, normalized, newLiveStates)
      }
    }

    protected abstract fun saveLocally(dataWriter: DataWriter?)
  }

  protected open fun beforeElementLoaded(element: Element) {
  }

  protected open fun beforeElementSaved(elements: MutableList<Element>, rootAttributes: MutableMap<String, String>) {
  }

  fun updatedFromStreamProvider(changedComponentNames: MutableSet<String>, deleted: Boolean) {
    val newElement = if (deleted) null else loadElement(useStreamProvider = true)
    val states = storageDataRef.get()
    if (newElement == null) {
      // if data was loaded, mark as changed all loaded components
      if (states != null) {
        changedComponentNames.addAll(states.keys())
        setStates(oldStorageData = states, newStorageData = StateMap.EMPTY)
      }
    }
    else if (states != null) {
      val newStates = loadState(newElement)
      changedComponentNames.addAll(getChangedComponentNames(states, newStates))
      setStates(oldStorageData = states, newStorageData = newStates)
    }
  }
}

internal class XmlDataWriter(private val rootElementName: String?,
                             private val elements: List<Element>,
                             private val rootAttributes: Map<String, String>,
                             private val macroManager: PathMacroManager?,
                             private val storageFilePathForDebugPurposes: String) : StringDataWriter() {
  override fun hasData(filter: DataWriterFilter): Boolean {
    return elements.any { filter.hasData(it) }
  }

  override fun write(writer: Writer, lineSeparator: String, filter: DataWriterFilter?) {
    var lineSeparatorWithIndent = lineSeparator
    val hasRootElement = rootElementName != null

    val replacePathMap = macroManager?.replacePathMap
    val macroFilter = macroManager?.macroFilter

    if (hasRootElement) {
      lineSeparatorWithIndent += "  "
      writer.append('<').append(rootElementName)
      for (entry in rootAttributes) {
        writer.append(' ')
        writer.append(entry.key)
        writer.append('=')
        writer.append('"')
        var value = entry.value
        if (replacePathMap != null) {
          value = replacePathMap.substitute(value, SystemInfo.isFileSystemCaseSensitive)
        }
        writer.append(JDOMUtil.escapeText(value, false, true))
        writer.append('"')
      }

      if (elements.isEmpty()) {
        // see note in the save() why elements here can be an empty list
        writer.append(" />")
        return
      }

      writer.append('>')
    }

    val xmlOutputter = JbXmlOutputter(lineSeparatorWithIndent, filter?.toElementFilter(), replacePathMap, macroFilter, storageFilePathForDebugPurposes = storageFilePathForDebugPurposes)
    for (element in elements) {
      if (hasRootElement) {
        writer.append(lineSeparatorWithIndent)
      }

      xmlOutputter.printElement(writer, element, 0)
    }

    if (rootElementName != null) {
      writer.append(lineSeparator)
      writer.append("</").append(rootElementName).append('>')
    }
  }
}

private fun save(states: StateMap, newLiveStates: Map<String, Element>): MutableList<Element>? {
  if (states.isEmpty()) {
    return null
  }

  var result: MutableList<Element>? = null

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
    if (nameAttribute != null && nameAttribute === elementAttributes[0] && componentName == nameAttribute.value) {
      // all is OK
    }
    else {
      if (nameAttribute == null) {
        nameAttribute = Attribute(FileStorageCoreUtil.NAME, componentName)
        elementAttributes.add(0, nameAttribute)
      }
      else {
        nameAttribute.value = componentName
        if (elementAttributes[0] != nameAttribute) {
          elementAttributes.remove(nameAttribute)
          elementAttributes.add(0, nameAttribute)
        }
      }
    }

    if (result == null) {
      result = SmartList()
    }

    result.add(element)
  }
  return result
}

internal fun Element.normalizeRootName(): Element {
  if (org.jdom.JDOMInterner.isInterned(this)) {
    if (name == FileStorageCoreUtil.COMPONENT) {
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
      LOG.warn("State element must not have a parent: ${JDOMUtil.writeElement(this)}")
      detach()
    }
    name = FileStorageCoreUtil.COMPONENT
    return this
  }
}

// newStorageData - myStates contains only live (unarchived) states
private fun getChangedComponentNames(oldStateMap: StateMap, newStateMap: StateMap): Set<String> {
  val newKeys = newStateMap.keys()
  val existingKeys = oldStateMap.keys()

  val bothStates = ArrayList<String>(min(newKeys.size, existingKeys.size))
  @Suppress("SSBasedInspection")
  val existingKeysSet = if (existingKeys.size < 3) existingKeys.asList() else ObjectOpenHashSet(existingKeys)
  for (newKey in newKeys) {
    if (existingKeysSet.contains(newKey)) {
      bothStates.add(newKey)
    }
  }

  val diffs = CollectionFactory.createSmallMemoryFootprintSet<String>(newKeys.size + existingKeys.size)
  diffs.addAll(newKeys)
  diffs.addAll(existingKeys)
  for (state in bothStates) {
    diffs.remove(state)
  }

  for (componentName in bothStates) {
    oldStateMap.compare(componentName, newStateMap, diffs)
  }
  return diffs
}

enum class DataStateChanged {
  LOADED, SAVED
}

interface DataWriterFilter {
  enum class ElementLevel {
    ZERO, FIRST
  }

  companion object {
    fun requireAttribute(name: String, onLevel: ElementLevel): DataWriterFilter {
      return object: DataWriterFilter {
        override fun toElementFilter(): JDOMUtil.ElementOutputFilter {
          return JDOMUtil.ElementOutputFilter { childElement, level -> level != onLevel.ordinal || childElement.getAttribute(name) != null }
        }

        override fun hasData(element: Element): Boolean {
          val elementFilter = toElementFilter()
          if (onLevel == ElementLevel.ZERO && elementFilter.accept(element, 0)) {
            return true
          }
          return element.children.any { elementFilter.accept(it, 1) }
        }
      }
    }
  }

  fun toElementFilter(): JDOMUtil.ElementOutputFilter

  fun hasData(element: Element): Boolean
}

interface DataWriter {
  // LineSeparator cannot be used because custom (with an indent) line separator can be used
  fun write(output: OutputStream, lineSeparator: LineSeparator = LineSeparator.LF, filter: DataWriterFilter? = null)

  fun hasData(filter: DataWriterFilter): Boolean
}

internal fun DataWriter?.writeTo(file: Path,
                                 requestor: Any?,
                                 lineSeparator: LineSeparator = LineSeparator.LF,
                                 useXmlProlog: Boolean = false) {
  if (this == null) {
    file.delete()
  }
  else {
    val safe = SafeWriteRequestor.shouldUseSafeWrite(requestor)
    (if (safe) file.safeOutputStream() else file.outputStream()).use {
      if (useXmlProlog) {
        it.write(XML_PROLOG)
        it.write(lineSeparator.separatorBytes)
      }
      write(it, lineSeparator)
    }
  }
}

internal abstract class StringDataWriter : DataWriter {
  final override fun write(output: OutputStream, lineSeparator: LineSeparator, filter: DataWriterFilter?) {
    output.bufferedWriter().use {
      write(it, lineSeparator.separatorString, filter)
    }
  }

  internal abstract fun write(writer: Writer, lineSeparator: String, filter: DataWriterFilter?)
}

internal fun DataWriter.toBufferExposingByteArray(lineSeparator: LineSeparator = LineSeparator.LF): BufferExposingByteArrayOutputStream {
  val out = BufferExposingByteArrayOutputStream(1024)
  out.use { write(out, lineSeparator) }
  return out
}

// use ONLY for non-ordinal usages (default project, deprecated directoryBased storage)
internal fun createDataWriterForElement(element: Element, storageFilePathForDebugPurposes: String): DataWriter {
  return object: DataWriter {
    override fun hasData(filter: DataWriterFilter) = filter.hasData(element)

    override fun write(output: OutputStream, lineSeparator: LineSeparator, filter: DataWriterFilter?) {
      output.bufferedWriter().use {
        JbXmlOutputter(lineSeparator.separatorString, elementFilter = filter?.toElementFilter(), storageFilePathForDebugPurposes = storageFilePathForDebugPurposes).output(element, it)
      }
    }
  }
}

@ApiStatus.Internal
interface ExternalStorageWithInternalPart {
  val internalStorage: StateStorage
}