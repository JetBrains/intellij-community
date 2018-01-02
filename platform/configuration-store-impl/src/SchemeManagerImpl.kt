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
import com.intellij.configurationStore.schemeManager.*
import com.intellij.openapi.application.ex.DecodeDefaultsUtil
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil.DEFAULT_EXT
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.AbstractExtensionPointBean
import com.intellij.openapi.options.NonLazySchemeProcessor
import com.intellij.openapi.options.SchemeProcessor
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.util.io.FileUtilRt
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
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.catch
import com.intellij.util.io.*
import com.intellij.util.messages.MessageBus
import com.intellij.util.text.UniqueNameGenerator
import gnu.trove.THashSet
import org.jdom.Document
import org.jdom.Element
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

class SchemeManagerImpl<T : Any, in MUTABLE_SCHEME : T>(val fileSpec: String,
                                                        processor: SchemeProcessor<T, MUTABLE_SCHEME>,
                                                        private val provider: StreamProvider?,
                                                        private val ioDirectory: Path,
                                                        val roamingType: RoamingType = RoamingType.DEFAULT,
                                                        val presentableName: String? = null,
                                                        private val schemeNameToFileName: SchemeNameToFileName = CURRENT_NAME_CONVERTER,
                                                        private val messageBus: MessageBus? = null) : SchemeManagerBase<T, MUTABLE_SCHEME>(processor), SafeWriteRequestor {
  private val isOldSchemeNaming = schemeNameToFileName == OLD_NAME_CONVERTER

  private val isLoadingSchemes = AtomicBoolean()

  private val schemeListManager = SchemeListManager(this)

  private val schemes: ConcurrentList<T>
    get() = schemeListManager.schemes

  private var cachedVirtualDirectory: VirtualFile? = null

  private val schemeExtension: String
  private val updateExtension: Boolean

  internal val filesToDelete = ContainerUtil.newConcurrentSet<String>()

  // scheme could be changed - so, hashcode will be changed - we must use identity hashing strategy
  internal val schemeToInfo = ConcurrentCollectionFactory.createMap<T, ExternalInfo>(ContainerUtil.identityStrategy())

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

  override val rootDirectory: File
    get() = ioDirectory.toFile()

  override val allSchemeNames: Collection<String>
    get() = schemes.let { if (it.isEmpty()) emptyList() else it.map { processor.getSchemeKey(it) } }

  override val allSchemes: List<T>
    get() = Collections.unmodifiableList(schemes)

  override val isEmpty: Boolean
    get() = schemes.isEmpty()

  private inner class SchemeFileTracker : BulkFileListener {
    private fun isMy(file: VirtualFile) = canRead(file.nameSequence)

    private fun isMyDirectory(parent: VirtualFile) = cachedVirtualDirectory.let { if (it == null) ioDirectory.systemIndependentPath == parent.path else it == parent }

    override fun after(events: MutableList<out VFileEvent>) {
      eventLoop@ for (event in events) {
        if (event.requestor is SchemeManagerImpl<*, *>) {
          continue
        }

        when (event) {
          is VFileContentChangeEvent -> {
            val fileName = event.file.name
            if (!canRead(fileName) || !isMyDirectory(event.file.parent)) {
              continue@eventLoop
            }

            val oldCurrentScheme = activeScheme
            val changedScheme = findExternalizableSchemeByFileName(fileName)

            if (callSchemeContentChangedIfSupported(changedScheme, fileName, event.file)) {
              continue@eventLoop
            }

            changedScheme?.let {
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
            if (canRead(event.childName)) {
              if (isMyDirectory(event.parent)) {
                event.file?.let { schemeCreatedExternally(it) }
              }
            }
            else if (event.file?.isDirectory == true) {
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
            val oldCurrentScheme = activeScheme
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

    private fun callSchemeContentChangedIfSupported(changedScheme: MUTABLE_SCHEME?, fileName: String, file: VirtualFile): Boolean {
      if (changedScheme == null || processor !is SchemeContentChangedHandler<*> || processor !is LazySchemeProcessor) {
        return false
      }

      // unrealistic case, but who knows
      val externalInfo = schemeToInfo.get(changedScheme) ?: return false

      catchAndLog(fileName) {
        val bytes = file.contentsToByteArray()
        lazyPreloadScheme(bytes, isOldSchemeNaming) { name, parser ->
          val attributeProvider = Function<String, String?> { parser.getAttributeValue(null, it) }
          val schemeName = name
                           ?: processor.getSchemeKey(attributeProvider, FileUtilRt.getNameWithoutExtension(fileName))
                           ?: throw RuntimeException("Name is missed:\n${bytes.toString(Charsets.UTF_8)}")

          val dataHolder = SchemeDataHolderImpl(bytes, externalInfo)
          @Suppress("UNCHECKED_CAST")
          (processor as SchemeContentChangedHandler<MUTABLE_SCHEME>).schemeContentChanged(changedScheme, schemeName, dataHolder)
        }
        return true
      }
      return false
    }

    private fun schemeCreatedExternally(file: VirtualFile) {
      val newSchemes = SmartList<T>()
      val readScheme = readSchemeFromFile(file, newSchemes)
      if (readScheme != null) {
        val readSchemeKey = processor.getSchemeKey(readScheme)
        val existingScheme = findSchemeByName(readSchemeKey)
        @Suppress("SuspiciousEqualsCombination")
        if (existingScheme != null && schemeListManager.readOnlyExternalizableSchemes.get(processor.getSchemeKey(existingScheme)) !== existingScheme) {
          LOG.warn("Ignore incorrect VFS create scheme event: schema ${readSchemeKey} is already exists")
          return
        }

        schemes.addAll(newSchemes)

        processor.initScheme(readScheme)
        processor.onSchemeAdded(readScheme)
      }
    }

    private fun updateCurrentScheme(oldScheme: T?, newScheme: T? = null) {
      if (activeScheme != null) {
        return
      }

      if (oldScheme != activeScheme) {
        val scheme = newScheme ?: schemes.firstOrNull()
        currentPendingSchemeName = null
        activeScheme = scheme
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
      lazyPreloadScheme(bytes, isOldSchemeNaming) { name, parser ->
        val attributeProvider = Function<String, String?> { parser.getAttributeValue(null, it) }
        val fileName = PathUtilRt.getFileName(url.path)
        val extension = getFileExtension(fileName, true)
        val externalInfo = ExternalInfo(fileName.substring(0, fileName.length - extension.length), extension)

        val schemeKey = name
                        ?: (processor as LazySchemeProcessor).getSchemeKey(attributeProvider, externalInfo.fileNameWithoutExtension)
                        ?: throw RuntimeException("Name is missed:\n${bytes.toString(Charsets.UTF_8)}")

        externalInfo.schemeKey = schemeKey

        val scheme = (processor as LazySchemeProcessor).createScheme(SchemeDataHolderImpl(bytes, externalInfo), schemeKey, attributeProvider, true)
        val oldInfo = schemeToInfo.put(scheme, externalInfo)
        LOG.assertTrue(oldInfo == null)
        val oldScheme = schemeListManager.readOnlyExternalizableSchemes.put(schemeKey, scheme)
        if (oldScheme != null) {
          LOG.warn("Duplicated scheme ${schemeKey} - old: $oldScheme, new $scheme")
        }
        schemes.add(scheme)
      }
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error("Cannot read scheme from $resourceName", e)
    }
  }

  private fun getFileExtension(fileName: CharSequence, allowAny: Boolean): String {
    return when {
      StringUtilRt.endsWithIgnoreCase(fileName, schemeExtension) -> schemeExtension
      StringUtilRt.endsWithIgnoreCase(fileName, DEFAULT_EXT) -> DEFAULT_EXT
      allowAny -> PathUtil.getFileExtension(fileName.toString())!!
      else -> throw IllegalStateException("Scheme file extension $fileName is unknown, must be filtered out")
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
            schemeListManager.readOnlyExternalizableSchemes.put(processor.getSchemeKey(scheme), scheme)
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
      schemeListManager.replaceSchemeList(oldSchemes, schemes)

      @Suppress("UNCHECKED_CAST")
      for (i in newSchemesOffset until schemes.size) {
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

  override fun reload() {
    processor.beforeReloaded(this)
    // we must not remove non-persistent (e.g. predefined) schemes, because we cannot load it (obviously)
    removeExternalizableSchemes()
    processor.reloaded(this, loadSchemes())
  }

  private fun removeExternalizableSchemes() {
    // todo check is bundled/read-only schemes correctly handled
    val iterator = schemes.iterator()
    for (scheme in iterator) {
      if ((scheme as? SerializableScheme)?.schemeState ?: processor.getState(scheme) == SchemeState.NON_PERSISTENT) {
        continue
      }

      activeScheme?.let {
        if (scheme === it) {
          currentPendingSchemeName = processor.getSchemeKey(it)
          activeScheme = null
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

      schemes.firstOrNull({ processor.getSchemeKey(it) == schemeName})?.let { existingScheme ->
        if (schemeListManager.readOnlyExternalizableSchemes.get(processor.getSchemeKey(existingScheme)) === existingScheme) {
          // so, bundled scheme is shadowed
          schemeListManager.removeFirstScheme(schemes, scheduleDelete = false) { it === existingScheme }
          return true
        }
        else if (processor.isExternalizable(existingScheme) && isOverwriteOnLoad(existingScheme)) {
          schemeListManager.removeFirstScheme(schemes) { it === existingScheme }
        }
        else {
          if (schemeExtension != extension && schemeToInfo.get(existingScheme)?.fileNameWithoutExtension == fileNameWithoutExtension) {
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
      info.schemeKey = schemeName
      return info
    }

    val duringLoad = filesToDelete != null
    var scheme: MUTABLE_SCHEME? = null
    if (processor is LazySchemeProcessor) {
      val bytes = input.readBytes()
      lazyPreloadScheme(bytes, isOldSchemeNaming) { name, parser ->
        val attributeProvider = Function<String, String?> {
          if (parser.eventType == XmlPullParser.START_TAG) {
            parser.getAttributeValue(null, it)
          }
          else {
            null
          }
        }
        val schemeName = name ?: processor.getSchemeKey(attributeProvider, fileNameWithoutExtension)
        if (schemeName == null) {
          throw RuntimeException("Name is missed:\n${bytes.toString(Charsets.UTF_8)}")
        }
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
      val schemeKey = processor.getSchemeKey(scheme!!)
      if (!checkExisting(schemeKey)) {
        return null
      }

      schemeToInfo.put(scheme, createInfo(schemeKey, element))
      this.filesToDelete.remove(fileName)
    }

    if (schemes === this.schemes) {
      @Suppress("UNCHECKED_CAST")
      addScheme(scheme as T, true)
    }
    else {
      @Suppress("UNCHECKED_CAST")
      schemes.add(scheme as T)
    }
    return scheme
  }

  private val T.fileName: String?
    get() = schemeToInfo.get(this)?.fileNameWithoutExtension

  fun canRead(name: CharSequence) = (updateExtension && name.endsWith(DEFAULT_EXT, true) || name.endsWith(schemeExtension, ignoreCase = true)) && (processor !is LazySchemeProcessor || processor.isSchemeFile(name))

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
      fileNameWithoutExtension = nameGenerator.generateUniqueName(schemeNameToFileName.schemeNameToFileName(processor.getSchemeKey(scheme)))
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
    @Suppress("SuspiciousEqualsCombination")
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
          if (oldFile != null) {
            // VFS doesn't allow to rename to existing file, so, check it
            if (dir.findChild(fileName) == null) {
              runUndoTransparentWriteAction { oldFile.rename(this, fileName) }
              file = oldFile
            }
            else {
              externalInfo.scheduleDelete()
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
    externalInfo.schemeKey = processor.getSchemeKey(scheme)
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

    val bundledScheme = schemeListManager.readOnlyExternalizableSchemes.get(processor.getSchemeKey(scheme))
    if (bundledScheme == null) {
      if ((processor as? LazySchemeProcessor)?.isSchemeEqualToBundled(scheme) == true) {
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

  internal fun scheduleDelete(info: ExternalInfo) {
    info.scheduleDelete()
  }

  private fun isRenamed(scheme: T): Boolean {
    val info = schemeToInfo.get(scheme)
    return info != null && processor.getSchemeKey(scheme) != info.schemeKey
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

  override fun setSchemes(newSchemes: List<T>, newCurrentScheme: T?, removeCondition: Condition<T>?) = schemeListManager.setSchemes(newSchemes, newCurrentScheme, removeCondition)

  internal fun retainExternalInfo() {
    if (schemeToInfo.isEmpty()) {
      return
    }

    val iterator = schemeToInfo.entries.iterator()
    l@ for ((scheme, info) in iterator) {
      if (schemeListManager.readOnlyExternalizableSchemes.get(processor.getSchemeKey(scheme)) == scheme) {
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

  override fun addScheme(scheme: T, replaceExisting: Boolean) = schemeListManager.addScheme(scheme, replaceExisting)

  override fun findSchemeByName(schemeName: String) = schemes.firstOrNull { processor.getSchemeKey(it) == schemeName }

  override fun removeScheme(name: String) = schemeListManager.removeFirstScheme(schemes) {processor.getSchemeKey(it) == name }

  override fun removeScheme(scheme: T) = schemeListManager.removeFirstScheme(schemes) { it == scheme } != null

  override fun isMetadataEditable(scheme: T) = !schemeListManager.readOnlyExternalizableSchemes.containsKey(processor.getSchemeKey(scheme))

  override fun toString() = fileSpec
}