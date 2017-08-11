/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.application.ex.DecodeDefaultsUtil
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil.DEFAULT_EXT
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.AbstractExtensionPointBean
import com.intellij.openapi.options.*
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.SafeWriteRequestor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.*
import com.intellij.util.containers.*
import com.intellij.util.io.*
import com.intellij.util.messages.MessageBus
import com.intellij.util.text.UniqueNameGenerator
import gnu.trove.THashSet
import org.jdom.Document
import org.jdom.Element
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

class SchemeManagerImpl<T : Scheme, MUTABLE_SCHEME : T>(val fileSpec: String,
                                                        private val processor: SchemeProcessor<T, MUTABLE_SCHEME>,
                                                        private val provider: StreamProvider?,
                                                        private val ioDirectory: Path,
                                                        val roamingType: RoamingType = RoamingType.DEFAULT,
                                                        val presentableName: String? = null,
                                                        private val isUseOldFileNameSanitize: Boolean = false,
                                                        private val messageBus: MessageBus? = null) : SchemesManager<T>(), SafeWriteRequestor {
  private val isLoadingSchemes = AtomicBoolean()

  private val schemesRef = AtomicReference(ContainerUtil.createLockFreeCopyOnWriteList<T>() as ConcurrentList<T>)

  private val schemes: ConcurrentList<T>
    get() = schemesRef.get()

  private val readOnlyExternalizableSchemes = ContainerUtil.newConcurrentMap<String, T>()

  /**
   * Schemes can be lazy loaded, so, client should be able to set current scheme by name, not only by instance.
   */
  private @Volatile var currentPendingSchemeName: String? = null

  private var currentScheme: T? = null

  private var cachedVirtualDirectory: VirtualFile? = null

  private val schemeExtension: String
  private val updateExtension: Boolean

  private val filesToDelete = ContainerUtil.newConcurrentSet<String>()

  // scheme could be changed - so, hashcode will be changed - we must use identity hashing strategy
  private val schemeToInfo = ConcurrentCollectionFactory.createMap<T, ExternalInfo>(ContainerUtil.identityStrategy())

  private val useVfs = messageBus != null

  init {
    if (processor is SchemeExtensionProvider) {
      schemeExtension = processor.schemeExtension
      updateExtension = true
    }
    else {
      schemeExtension = FileStorageCoreUtil.DEFAULT_EXT
      updateExtension = false
    }

    if (useVfs && (provider == null || !provider.isApplicable(fileSpec, roamingType))) {
      LOG.runAndLogException { refreshVirtualDirectoryAndAddListener() }
    }
  }

  private inner class SchemeFileTracker : BulkFileListener {
    private fun isMy(file: VirtualFile) = isMy(file.nameSequence)
    private fun isMy(name: CharSequence) = name.endsWith(schemeExtension, ignoreCase = true) && (processor !is LazySchemeProcessor || processor.isSchemeFile(name))

    override fun after(events: MutableList<out VFileEvent>) {
      eventLoop@ for (event in events) {
        if (event.requestor is SchemeManagerImpl<*, *>) {
          continue
        }

        fun isMyDirectory(parent: VirtualFile) = cachedVirtualDirectory.let { if (it == null) ioDirectory.systemIndependentPath == parent.path else it == parent }

        when (event) {
          is VFileContentChangeEvent -> {
            if (!isMy(event.file) || !isMyDirectory(event.file.parent)) {
              continue@eventLoop
            }

            val oldCurrentScheme = currentScheme
            findExternalizableSchemeByFileName(event.file.name)?.let {
              removeScheme(it)
              processor.onSchemeDeleted(it)
            }

            updateCurrentScheme(oldCurrentScheme, readSchemeFromFile(event.file, schemes)?.let {
              processor.initScheme(it)
              processor.onSchemeAdded(it)
              it
            })
          }

          is VFileCreateEvent -> {
            if (isMy(event.childName)) {
              if (isMyDirectory(event.parent)) {
                event.file?.let { schemeCreatedExternally(it) }
              }
            }
            else if (event.file?.isDirectory ?: false) {
              val dir = virtualDirectory
              if (event.file == dir) {
                for (file in dir!!.children) {
                  if (isMy(file)) {
                    schemeCreatedExternally(file)
                  }
                }
              }
            }
          }
          is VFileDeleteEvent -> {
            val oldCurrentScheme = currentScheme
            if (event.file.isDirectory) {
              val dir = virtualDirectory
              if (event.file == dir) {
                cachedVirtualDirectory = null
                removeExternalizableSchemes()
              }
            }
            else if (isMy(event.file) && isMyDirectory(event.file.parent)) {
              val scheme = findExternalizableSchemeByFileName(event.file.name) ?: continue@eventLoop
              removeScheme(scheme)
              processor.onSchemeDeleted(scheme)
            }

            updateCurrentScheme(oldCurrentScheme)
          }
        }
      }
    }

    private fun schemeCreatedExternally(file: VirtualFile) {
      val newSchemes = SmartList<T>()
      val readScheme = readSchemeFromFile(file, newSchemes)
      if (readScheme != null) {
        val existingScheme = findSchemeByName(readScheme.name)
        if (existingScheme != null && readOnlyExternalizableSchemes.get(existingScheme.name) !== existingScheme) {
          LOG.warn("Ignore incorrect VFS create scheme event: schema ${readScheme.name} is already exists")
          return
        }

        schemes.addAll(newSchemes)

        processor.initScheme(readScheme)
        processor.onSchemeAdded(readScheme)
      }
    }

    private fun updateCurrentScheme(oldScheme: T?, newScheme: T? = null) {
      if (currentScheme != null) {
        return
      }

      if (oldScheme != currentScheme) {
        val scheme = newScheme ?: schemes.firstOrNull()
        currentPendingSchemeName = null
        currentScheme = scheme
        // must be equals by reference
        if (oldScheme !== scheme) {
          processor.onCurrentSchemeSwitched(oldScheme, scheme)
        }
      }
      else if (newScheme != null) {
        processPendingCurrentSchemeName(newScheme)
      }
    }
  }

  private fun refreshVirtualDirectoryAndAddListener() {
    // store refreshes root directory, so, we don't need to use refreshAndFindFile
    val directory = LocalFileSystem.getInstance().findFileByPath(ioDirectory.systemIndependentPath) ?: return

    this.cachedVirtualDirectory = directory
    directory.children
    if (directory is NewVirtualFile) {
      directory.markDirty()
    }

    directory.refresh(true, false)
  }

  override fun loadBundledScheme(resourceName: String, requestor: Any) {
    try {
      val url = if (requestor is AbstractExtensionPointBean)
        requestor.loaderForClass.getResource(resourceName)
      else
        DecodeDefaultsUtil.getDefaults(requestor, resourceName)
      if (url == null) {
        LOG.error("Cannot read scheme from $resourceName")
        return
      }

      val bytes = URLUtil.openStream(url).readBytes()
      lazyPreloadScheme(bytes, isUseOldFileNameSanitize) { name, parser ->
        val attributeProvider = Function<String, String?> { parser.getAttributeValue(null, it) }
        val fileName = PathUtilRt.getFileName(url.path)
        val extension = getFileExtension(fileName, true)
        val externalInfo = ExternalInfo(fileName.substring(0, fileName.length - extension.length), extension)

        val schemeName = name ?: (processor as LazySchemeProcessor).getName(attributeProvider, externalInfo.fileNameWithoutExtension)
        externalInfo.schemeName = schemeName

        val scheme = (processor as LazySchemeProcessor).createScheme(SchemeDataHolderImpl(bytes, externalInfo), schemeName, attributeProvider, true)
        val oldInfo = schemeToInfo.put(scheme, externalInfo)
        LOG.assertTrue(oldInfo == null)
        val oldScheme = readOnlyExternalizableSchemes.put(scheme.name, scheme)
        if (oldScheme != null) {
          LOG.warn("Duplicated scheme ${scheme.name} - old: $oldScheme, new $scheme")
        }
        schemes.add(scheme)
      }
    }
    catch (e: Throwable) {
      LOG.error("Cannot read scheme from $resourceName", e)
    }
  }

  private fun getFileExtension(fileName: CharSequence, allowAny: Boolean): String {
    return if (StringUtilRt.endsWithIgnoreCase(fileName, schemeExtension)) {
      schemeExtension
    }
    else if (StringUtilRt.endsWithIgnoreCase(fileName, DEFAULT_EXT)) {
      DEFAULT_EXT
    }
    else if (allowAny) {
      PathUtil.getFileExtension(fileName.toString())!!
    }
    else {
      throw IllegalStateException("Scheme file extension $fileName is unknown, must be filtered out")
    }
  }

  override fun loadSchemes(): Collection<T> {
    if (!isLoadingSchemes.compareAndSet(false, true)) {
      throw IllegalStateException("loadSchemes is already called")
    }

    try {
      val filesToDelete = THashSet<String>()
      val oldSchemes = schemes
      val schemes = oldSchemes.toMutableList()
      val newSchemesOffset = schemes.size
      if (provider != null && provider.processChildren(fileSpec, roamingType, { canRead(it) }) { name, input, readOnly ->
        catchAndLog(name) {
          val scheme = loadScheme(name, input, schemes, filesToDelete)
          if (readOnly && scheme != null) {
            readOnlyExternalizableSchemes.put(scheme.name, scheme)
          }
        }
        true
      }) {
      }
      else {
        ioDirectory.directoryStreamIfExists({ canRead(it.fileName.toString()) }) {
          for (file in it) {
            if (file.isDirectory()) {
              continue
            }

            catchAndLog(file.fileName.toString()) { filename ->
              file.inputStream().use { loadScheme(filename, it, schemes, filesToDelete) }
            }
          }
        }
      }

      this.filesToDelete.addAll(filesToDelete)
      replaceSchemeList(oldSchemes, schemes)

      @Suppress("UNCHECKED_CAST")
      for (i in newSchemesOffset..schemes.size - 1) {
        val scheme = schemes.get(i) as MUTABLE_SCHEME
        processor.initScheme(scheme)
        @Suppress("UNCHECKED_CAST")
        processPendingCurrentSchemeName(scheme)
      }

      messageBus?.connect()?.subscribe(VirtualFileManager.VFS_CHANGES, SchemeFileTracker())

      return schemes.subList(newSchemesOffset, schemes.size)
    }
    finally {
      isLoadingSchemes.set(false)
    }
  }

  private fun replaceSchemeList(oldList: ConcurrentList<T>, newList: List<T>) {
    if (!schemesRef.compareAndSet(oldList, ContainerUtil.createLockFreeCopyOnWriteList(newList) as ConcurrentList<T>)) {
      throw IllegalStateException("Scheme list was modified")
    }
  }

  override fun reload() {
    // we must not remove non-persistent (e.g. predefined) schemes, because we cannot load it (obviously)
    removeExternalizableSchemes()

    loadSchemes()

    (processor as? LazySchemeProcessor)?.reloaded(this)
  }

  private fun removeExternalizableSchemes() {
    // todo check is bundled/read-only schemes correctly handled
    val iterator = schemes.iterator()
    for (scheme in iterator) {
      if ((scheme as? SerializableScheme)?.schemeState ?: processor.getState(scheme) == SchemeState.NON_PERSISTENT) {
        continue
      }

      currentScheme?.let {
        if (scheme === it) {
          currentPendingSchemeName = it.name
          currentScheme = null
        }
      }

      iterator.remove()

      @Suppress("UNCHECKED_CAST")
      processor.onSchemeDeleted(scheme as MUTABLE_SCHEME)
    }
    retainExternalInfo()
  }

  @Suppress("UNCHECKED_CAST")
  private fun findExternalizableSchemeByFileName(fileName: String) = schemes.firstOrNull { fileName == "${it.fileName}$schemeExtension" } as MUTABLE_SCHEME?

  private fun isOverwriteOnLoad(existingScheme: T): Boolean {
    val info = schemeToInfo.get(existingScheme)
    // scheme from file with old extension, so, we must ignore it
    return info != null && schemeExtension != info.fileExtension
  }

  private inner class SchemeDataHolderImpl(private val bytes: ByteArray, private val externalInfo: ExternalInfo) : SchemeDataHolder<MUTABLE_SCHEME> {
    override fun read(): Element = loadElement(bytes.inputStream())

    override fun updateDigest(scheme: MUTABLE_SCHEME) {
      try {
        updateDigest(processor.writeScheme(scheme) as Element)
      }
      catch (e: WriteExternalException) {
        LOG.error("Cannot update digest", e)
      }
    }

    override fun updateDigest(data: Element) {
      externalInfo.digest = data.digest()
    }
  }

  private fun loadScheme(fileName: String, input: InputStream, schemes: MutableList<T>, filesToDelete: MutableSet<String>? = null): MUTABLE_SCHEME? {
    val extension = getFileExtension(fileName, false)
    if (filesToDelete != null && filesToDelete.contains(fileName)) {
      LOG.warn("Scheme file \"$fileName\" is not loaded because marked to delete")
      return null
    }

    val fileNameWithoutExtension = fileName.substring(0, fileName.length - extension.length)
    fun checkExisting(schemeName: String): Boolean {
      if (filesToDelete == null) {
        return true
      }

      schemes.firstOrNull({ it.name == schemeName})?.let { existingScheme ->
        if (readOnlyExternalizableSchemes.get(existingScheme.name) === existingScheme) {
          // so, bundled scheme is shadowed
          removeFirstScheme(schemes, scheduleDelete = false) { it === existingScheme }
          return true
        }
        else if (processor.isExternalizable(existingScheme) && isOverwriteOnLoad(existingScheme)) {
          removeFirstScheme(schemes) { it === existingScheme }
        }
        else {
          if (schemeExtension != extension && schemeToInfo.get(existingScheme as Scheme)?.fileNameWithoutExtension == fileNameWithoutExtension) {
            // 1.oldExt is loading after 1.newExt - we should delete 1.oldExt
            filesToDelete.add(fileName)
          }
          else {
            // We don't load scheme with duplicated name - if we generate unique name for it, it will be saved then with new name.
            // It is not what all can expect. Such situation in most cases indicates error on previous level, so, we just warn about it.
            LOG.warn("Scheme file \"$fileName\" is not loaded because defines duplicated name \"$schemeName\"")
          }
          return false
        }
      }

      return true
    }

    fun createInfo(schemeName: String, element: Element?): ExternalInfo {
      val info = ExternalInfo(fileNameWithoutExtension, extension)
      element?.let {
        info.digest = it.digest()
      }
      info.schemeName = schemeName
      return info
    }

    val duringLoad = filesToDelete != null
    var scheme: MUTABLE_SCHEME? = null
    if (processor is LazySchemeProcessor) {
      val bytes = input.readBytes()
      lazyPreloadScheme(bytes, isUseOldFileNameSanitize) { name, parser ->
        val attributeProvider = Function<String, String?> { parser.getAttributeValue(null, it) }
        val schemeName = name ?: processor.getName(attributeProvider, fileNameWithoutExtension)
        if (!checkExisting(schemeName)) {
          return null
        }

        val externalInfo = createInfo(schemeName, null)
        scheme = processor.createScheme(SchemeDataHolderImpl(bytes, externalInfo), schemeName, attributeProvider)
        schemeToInfo.put(scheme, externalInfo)
        this.filesToDelete.remove(fileName)
      }
    }
    else {
      val element = loadElement(input)
      scheme = (processor as NonLazySchemeProcessor).readScheme(element, duringLoad) ?: return null
      val schemeName = scheme!!.name
      if (!checkExisting(schemeName)) {
        return null
      }

      schemeToInfo.put(scheme, createInfo(schemeName, element))
      this.filesToDelete.remove(fileName)
    }

    if (schemes === this.schemes) {
      addNewScheme(scheme as T, true)
    }
    else {
      schemes.add(scheme as T)
    }
    return scheme
  }

  private val T.fileName: String?
    get() = schemeToInfo.get(this)?.fileNameWithoutExtension

  fun canRead(name: CharSequence) = (updateExtension && name.endsWith(DEFAULT_EXT, true) || name.endsWith(schemeExtension, true)) && (processor !is LazySchemeProcessor || processor.isSchemeFile(name))

  private fun readSchemeFromFile(file: VirtualFile, schemes: MutableList<T>): MUTABLE_SCHEME? {
    val fileName = file.name
    if (file.isDirectory || !canRead(fileName)) {
      return null
    }

    catchAndLog(fileName) {
      return file.inputStream.use { loadScheme(fileName, it, schemes) }
    }

    return null
  }

  override fun save(errors: MutableList<Throwable>) {
    if (isLoadingSchemes.get()) {
      LOG.warn("Skip save - schemes are loading")
    }

    var hasSchemes = false
    val nameGenerator = UniqueNameGenerator()
    val changedSchemes = SmartList<MUTABLE_SCHEME>()
    for (scheme in schemes) {
      val state = (scheme as? SerializableScheme)?.schemeState ?: processor.getState(scheme)
      if (state == SchemeState.NON_PERSISTENT) {
        continue
      }

      hasSchemes = true

      if (state != SchemeState.UNCHANGED) {
        @Suppress("UNCHECKED_CAST")
        changedSchemes.add(scheme as MUTABLE_SCHEME)
      }

      val fileName = scheme.fileName
      if (fileName != null && !isRenamed(scheme)) {
        nameGenerator.addExistingName(fileName)
      }
    }

    for (scheme in changedSchemes) {
      try {
        saveScheme(scheme, nameGenerator)
      }
      catch (e: Throwable) {
        errors.add(RuntimeException("Cannot save scheme $fileSpec/$scheme", e))
      }
    }

    val filesToDelete = THashSet(filesToDelete)
    if (!filesToDelete.isEmpty) {
      this.filesToDelete.removeAll(filesToDelete)
      deleteFiles(errors, filesToDelete)
      // remove empty directory only if some file was deleted - avoid check on each save
      if (!hasSchemes && (provider == null || !provider.isApplicable(fileSpec, roamingType))) {
        removeDirectoryIfEmpty(errors)
      }
    }
  }

  private fun removeDirectoryIfEmpty(errors: MutableList<Throwable>) {
    ioDirectory.directoryStreamIfExists {
      for (file in it) {
        if (!file.isHidden()) {
          LOG.info("Directory ${ioDirectory.fileName} is not deleted: at least one file ${file.fileName} exists")
          return@removeDirectoryIfEmpty
        }
      }
    }

    LOG.info("Remove schemes directory ${ioDirectory.fileName}")
    cachedVirtualDirectory = null

    var deleteUsingIo = !useVfs
    if (!deleteUsingIo) {
      virtualDirectory?.let {
        runUndoTransparentWriteAction {
          try {
            it.delete(this)
          }
          catch (e: IOException) {
            deleteUsingIo = true
            errors.add(e)
          }
        }
      }
    }

    if (deleteUsingIo) {
      errors.catch { ioDirectory.delete() }
    }
  }

  private fun saveScheme(scheme: MUTABLE_SCHEME, nameGenerator: UniqueNameGenerator) {
    var externalInfo: ExternalInfo? = schemeToInfo.get(scheme)
    val currentFileNameWithoutExtension = externalInfo?.fileNameWithoutExtension
    val parent = processor.writeScheme(scheme)
    val element = parent as? Element ?: (parent as Document).detachRootElement()
    if (element.isEmpty()) {
      externalInfo?.scheduleDelete()
      return
    }

    var fileNameWithoutExtension = currentFileNameWithoutExtension
    if (fileNameWithoutExtension == null || isRenamed(scheme)) {
      fileNameWithoutExtension = nameGenerator.generateUniqueName(FileUtil.sanitizeFileName(scheme.name, isUseOldFileNameSanitize))
    }

    val newDigest = element!!.digest()
    when {
      externalInfo != null && currentFileNameWithoutExtension === fileNameWithoutExtension && externalInfo.isDigestEquals(newDigest) -> return
      isEqualToBundledScheme(externalInfo, newDigest, scheme) -> return

      // we must check it only here to avoid delete old scheme just because it is empty (old idea save -> new idea delete on open)
      processor is LazySchemeProcessor && processor.isSchemeDefault(scheme, newDigest) -> {
        externalInfo?.scheduleDelete()
        return
      }
    }

    val fileName = fileNameWithoutExtension!! + schemeExtension
    // file will be overwritten, so, we don't need to delete it
    filesToDelete.remove(fileName)

    // stream provider always use LF separator
    val byteOut = element.toBufferExposingByteArray()

    var providerPath: String?
    if (provider != null && provider.enabled) {
      providerPath = "$fileSpec/$fileName"
      if (!provider.isApplicable(providerPath, roamingType)) {
        providerPath = null
      }
    }
    else {
      providerPath = null
    }

    // if another new scheme uses old name of this scheme, we must not delete it (as part of rename operation)
    val renamed = externalInfo != null && fileNameWithoutExtension !== currentFileNameWithoutExtension && currentFileNameWithoutExtension != null && nameGenerator.isUnique(currentFileNameWithoutExtension)
    if (providerPath == null) {
      if (useVfs) {
        var file: VirtualFile? = null
        var dir = virtualDirectory
        if (dir == null || !dir.isValid) {
          dir = createDir(ioDirectory, this)
          cachedVirtualDirectory = dir
        }

        if (renamed) {
          val oldFile = dir.findChild(externalInfo!!.fileName)
          oldFile?.let {
            // VFS doesn't allow to rename to existing file, so, check it
            if (dir!!.findChild(fileName) == null) {
              runUndoTransparentWriteAction { it.rename(this, fileName) }
              file = oldFile
            }
            else {
              externalInfo!!.scheduleDelete()
            }
          }
        }

        if (file == null) {
          file = dir.getOrCreateChild(fileName, this)
        }

        runUndoTransparentWriteAction {
          file!!.getOutputStream(this).use { byteOut.writeTo(it) }
        }
      }
      else {
        if (renamed) {
          externalInfo!!.scheduleDelete()
        }
        ioDirectory.resolve(fileName).write(byteOut.internalBuffer, 0, byteOut.size())
      }
    }
    else {
      if (renamed) {
        externalInfo!!.scheduleDelete()
      }
      provider!!.write(providerPath, byteOut.internalBuffer, byteOut.size(), roamingType)
    }

    if (externalInfo == null) {
      externalInfo = ExternalInfo(fileNameWithoutExtension, schemeExtension)
      schemeToInfo.put(scheme, externalInfo)
    }
    else {
      externalInfo.setFileNameWithoutExtension(fileNameWithoutExtension, schemeExtension)
    }
    externalInfo.digest = newDigest
    externalInfo.schemeName = scheme.name
  }

  private fun isEqualToBundledScheme(externalInfo: ExternalInfo?, newDigest: ByteArray, scheme: MUTABLE_SCHEME): Boolean {
    fun serializeIfPossible(scheme: T): Element? {
      LOG.runAndLogException {
        @Suppress("UNCHECKED_CAST")
        val bundledAsMutable = scheme as? MUTABLE_SCHEME ?: return null
        return processor.writeScheme(bundledAsMutable) as Element
      }
      return null
    }

    val bundledScheme = readOnlyExternalizableSchemes.get(scheme.name)
    if (bundledScheme == null) {
      if ((processor as? LazySchemeProcessor)?.isSchemeEqualToBundled(scheme) ?: false) {
        externalInfo?.scheduleDelete()
        return true
      }
      return false
    }

    val bundledExternalInfo = schemeToInfo.get(bundledScheme) ?: return false
    if (bundledExternalInfo.digest == null) {
      serializeIfPossible(bundledScheme)?.let {
        bundledExternalInfo.digest = it.digest()
      } ?: return false
    }
    if (bundledExternalInfo.isDigestEquals(newDigest)) {
      externalInfo?.scheduleDelete()
      return true
    }
    return false
  }

  private fun ExternalInfo.scheduleDelete() {
    filesToDelete.add(fileName)
  }

  private fun isRenamed(scheme: T): Boolean {
    val info = schemeToInfo.get(scheme)
    return info != null && scheme.name != info.schemeName
  }

  private fun deleteFiles(errors: MutableList<Throwable>, filesToDelete: MutableSet<String>) {
    if (provider != null) {
      val iterator = filesToDelete.iterator()
      for (name in iterator) {
        errors.catch {
          val spec = "$fileSpec/$name"
          if (provider.delete(spec, roamingType)) {
            iterator.remove()
          }
        }
      }
    }

    if (filesToDelete.isEmpty()) {
      return
    }

    if (useVfs) {
      virtualDirectory?.let {
        val childrenToDelete = it.children.filter { filesToDelete.contains(it.name) }
        if (childrenToDelete.isNotEmpty()) {
          runUndoTransparentWriteAction {
            childrenToDelete.forEach { file ->
              errors.catch { file.delete(this) }
            }
          }
        }
        return
      }
    }

    for (name in filesToDelete) {
      errors.catch { ioDirectory.resolve(name).delete() }
    }
  }

  private val virtualDirectory: VirtualFile?
    get() {
      var result = cachedVirtualDirectory
      if (result == null) {
        result = LocalFileSystem.getInstance().findFileByPath(ioDirectory.systemIndependentPath)
        cachedVirtualDirectory = result
      }
      return result
    }

  override fun getRootDirectory(): File = ioDirectory.toFile()

  override fun setSchemes(newSchemes: List<T>, newCurrentScheme: T?, removeCondition: Condition<T>?) {
    if (schemes.isNotEmpty()) {
      if (removeCondition == null) {
        schemes.clear()
      }
      else {
        // we must not use remove or removeAll to avoid "equals" call
        schemesRef.set(ContainerUtil.createConcurrentList(schemes.filterSmart { !removeCondition.value(it) }))
      }
    }

    schemes.addAll(newSchemes)

    val oldCurrentScheme = currentScheme
    retainExternalInfo()

    if (oldCurrentScheme != newCurrentScheme) {
      val newScheme: T?
      if (newCurrentScheme != null) {
        currentScheme = newCurrentScheme
        newScheme = newCurrentScheme
      }
      else if (oldCurrentScheme != null && !schemes.contains(oldCurrentScheme)) {
        newScheme = schemes.firstOrNull()
        currentScheme = newScheme
      }
      else {
        newScheme = null
      }

      if (oldCurrentScheme != newScheme) {
        processor.onCurrentSchemeSwitched(oldCurrentScheme, newScheme)
      }
    }
  }

  private fun retainExternalInfo() {
    if (schemeToInfo.isEmpty()) {
      return
    }

    val iterator = schemeToInfo.entries.iterator()
    l@ for ((scheme, info) in iterator) {
      if (readOnlyExternalizableSchemes.get(scheme.name) == scheme) {
        continue
      }

      for (s in schemes) {
        if (s === scheme) {
          filesToDelete.remove(info.fileName)
          continue@l
        }
      }

      iterator.remove()
      info.scheduleDelete()
    }
  }

  override fun addNewScheme(scheme: T, replaceExisting: Boolean) {
    var toReplace = -1
    val schemes = schemes
    for (i in schemes.indices) {
      val existing = schemes.get(i)
      if (existing.name == scheme.name) {
        if (existing.javaClass != scheme.javaClass) {
          LOG.warn("'${scheme.name}' ${existing.javaClass.simpleName} replaced with ${scheme.javaClass.simpleName}")
        }

        toReplace = i
        if (replaceExisting && processor.isExternalizable(existing)) {
          val oldInfo = schemeToInfo.remove(existing)
          if (oldInfo != null && processor.isExternalizable(scheme) && !schemeToInfo.containsKey(scheme)) {
            schemeToInfo.put(scheme, oldInfo)
          }
        }
        break
      }
    }
    if (toReplace == -1) {
      schemes.add(scheme)
    }
    else if (replaceExisting || !processor.isExternalizable(scheme)) {
      schemes.set(toReplace, scheme)
    }
    else {
      (scheme as ExternalizableScheme).renameScheme(UniqueNameGenerator.generateUniqueName(scheme.name, collectExistingNames(schemes)))
      schemes.add(scheme)
    }

    if (processor.isExternalizable(scheme) && filesToDelete.isNotEmpty()) {
      schemeToInfo.get(scheme)?.let {
        filesToDelete.remove(it.fileName)
      }
    }

    processPendingCurrentSchemeName(scheme)
  }

  private fun collectExistingNames(schemes: Collection<T>): Collection<String> {
    val result = THashSet<String>(schemes.size)
    schemes.mapTo(result) { it.name }
    return result
  }

  override fun clearAllSchemes() {
    for (it in schemeToInfo.values) {
      it.scheduleDelete()
    }

    currentScheme = null
    schemes.clear()
    schemeToInfo.clear()
  }

  override fun getAllSchemes(): List<T> = Collections.unmodifiableList(schemes)

  override fun isEmpty() = schemes.isEmpty()

  override fun findSchemeByName(schemeName: String) = schemes.firstOrNull { it.name == schemeName }

  override fun setCurrent(scheme: T?, notify: Boolean) {
    currentPendingSchemeName = null

    val oldCurrent = currentScheme
    currentScheme = scheme
    if (notify && oldCurrent !== scheme) {
      processor.onCurrentSchemeSwitched(oldCurrent, scheme)
    }
  }

  override fun setCurrentSchemeName(schemeName: String?, notify: Boolean) {
    currentPendingSchemeName = schemeName

    val scheme = if (schemeName == null) null else findSchemeByName(schemeName)
    // don't set current scheme if no scheme by name - pending resolution (see currentSchemeName field comment)
    if (scheme != null || schemeName == null) {
      setCurrent(scheme, notify)
    }
  }

  override fun getCurrentScheme() = currentScheme

  override fun getCurrentSchemeName() = currentScheme?.name ?: currentPendingSchemeName

  private fun processPendingCurrentSchemeName(newScheme: T) {
      if (newScheme.name == currentPendingSchemeName) {
        setCurrent(newScheme, false)
      }
    }

  override fun removeScheme(schemeName: String) = removeFirstScheme(schemes) {it.name == schemeName}

  override fun removeScheme(scheme: T) = removeFirstScheme(schemes) { it == scheme } != null

  private fun removeFirstScheme(schemes: MutableList<T>, scheduleDelete: Boolean = true, condition: (T) -> Boolean): T? {
    val iterator = schemes.iterator()
    for (scheme in iterator) {
      if (!condition(scheme)) {
        continue
      }

      if (currentScheme === scheme) {
        currentScheme = null
      }

      iterator.remove()

      if (scheduleDelete && processor.isExternalizable(scheme)) {
        schemeToInfo.remove(scheme)?.scheduleDelete()
      }
      return scheme
    }

    return null
  }

  override fun getAllSchemeNames() = schemes.let { if (it.isEmpty()) emptyList() else it.map { it.name } }

  override fun isMetadataEditable(scheme: T) = !readOnlyExternalizableSchemes.containsKey(scheme.name)

  override fun toString() = fileSpec
}

private fun ExternalizableScheme.renameScheme(newName: String) {
  if (newName != name) {
    name = newName
    LOG.assertTrue(newName == name)
  }
}

private inline fun catchAndLog(fileName: String, runnable: (fileName: String) -> Unit) {
  try {
    runnable(fileName)
  }
  catch (e: Throwable) {
    LOG.error("Cannot read scheme $fileName", e)
  }
}