// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.configurationStore.schemeManager

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.configurationStore.*
import com.intellij.diagnostic.PluginException
import com.intellij.ide.ui.UITheme
import com.intellij.ide.ui.laf.TempUIThemeLookAndFeelInfo
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.options.SchemeProcessor
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.SafeWriteRequestor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.util.*
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.io.write
import com.intellij.util.text.UniqueNameGenerator
import org.jdom.Document
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isHidden

class SchemeManagerImpl<T : Scheme, MUTABLE_SCHEME : T>(
  val fileSpec: String,
  processor: SchemeProcessor<T, MUTABLE_SCHEME>,
  private val provider: StreamProvider?,
  internal val ioDirectory: Path,
  val roamingType: RoamingType = RoamingType.DEFAULT,
  val presentableName: String? = null,
  private val schemeNameToFileName: SchemeNameToFileName = CURRENT_NAME_CONVERTER,
  private val fileChangeSubscriber: FileChangeSubscriber? = null,
  private val virtualFileResolver: VirtualFileResolver? = null,
  private val settingsCategory: SettingsCategory = SettingsCategory.OTHER
) : SchemeManagerBase<T, MUTABLE_SCHEME>(processor), SafeWriteRequestor, StorageManagerFileWriteRequestor {
  internal val isUseVfs: Boolean
    get() = fileChangeSubscriber != null || virtualFileResolver != null

  internal val isOldSchemeNaming = schemeNameToFileName == OLD_NAME_CONVERTER

  private val isLoadingSchemes = AtomicBoolean()

  internal val schemeListManager = SchemeListManager(this)

  internal val schemes: MutableList<T>
    get() = schemeListManager.schemes

  internal var cachedVirtualDirectory: VirtualFile? = null

  internal val schemeExtension: String
  private val updateExtension: Boolean

  internal val filesToDelete: MutableSet<String> = ConcurrentCollectionFactory.createConcurrentSet()

  init {
    if (processor is SchemeExtensionProvider) {
      schemeExtension = processor.schemeExtension
      updateExtension = true
    }
    else {
      schemeExtension = FileStorageCoreUtil.DEFAULT_EXT
      updateExtension = false
    }

    if (isUseVfs) {
      runCatching { refreshVirtualDirectory() }.getOrLogException(LOG)
    }
  }

  override val rootDirectory: File
    get() = ioDirectory.toFile()

  override val allSchemeNames: Collection<String>
    get() = schemes.map { processor.getSchemeKey(it) }

  override val allSchemes: List<T>
    get() = Collections.unmodifiableList(schemes)

  override val isEmpty: Boolean
    get() = schemes.isEmpty()

  private fun refreshVirtualDirectory() {
    // store refreshes root directory, so, we don't need to use refreshAndFindFile
    val directory = LocalFileSystem.getInstance().findFileByPath(ioDirectory.invariantSeparatorsPathString) ?: return
    cachedVirtualDirectory = directory
    directory.children
    if (directory is NewVirtualFile) {
      directory.markDirty()
    }

    directory.refresh(true, false)
  }

  override fun loadBundledSchemes(providers: Sequence<LoadBundleSchemeRequest<T>>) {
    processor as LazySchemeProcessor

    schemeListManager.mutate { schemes, schemeToInfo, readOnlyExternalizableSchemes ->
      for (provider in providers) {
        try {
          val schemeKey = provider.schemeKey

          val fileNameWithoutExtension = schemeNameToFileName(schemeKey)
          val externalInfo = ExternalInfo(fileNameWithoutExtension = fileNameWithoutExtension,
                                          fileExtension = fileNameWithoutExtension + FileStorageCoreUtil.DEFAULT_EXT)

          externalInfo.schemeKey = schemeKey

          val scheme = provider.createScheme()
          val oldInfo = schemeToInfo.put(scheme, externalInfo)
          LOG.assertTrue(oldInfo == null)
          val oldScheme = readOnlyExternalizableSchemes.put(schemeKey, scheme)
          if (oldScheme != null) {
            LOG.debug("Duplicated scheme $schemeKey - old: $oldScheme, new $scheme")
          }
          schemes.add(scheme)
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(PluginException(e, provider.pluginId))
        }
      }
    }
  }

  override fun loadBundledScheme(resourceName: String, requestor: Any?, pluginDescriptor: PluginDescriptor?): T? {
    try {
      val bytes = loadBytes(pluginDescriptor = pluginDescriptor, requestor = requestor, resourceName = resourceName) ?: return null
      lazyPreloadScheme(bytes = bytes, isOldSchemeNaming = isOldSchemeNaming) { name, parser ->
        val attributeProvider: (String) -> String? = { parser.getAttributeValue(null, it) }
        val fileName = PathUtilRt.getFileName(resourceName)
        val extension = getFileExtension(fileName = fileName, isAllowAny = true)
        val externalInfo = ExternalInfo(fileNameWithoutExtension = fileName.substring(0, fileName.length - extension.length),
                                        fileExtension = extension)

        val schemeKey = name
                        ?: (processor as LazySchemeProcessor).getSchemeKey(attributeProvider, externalInfo.fileNameWithoutExtension)
                        ?: throw nameIsMissed(bytes)

        externalInfo.schemeKey = schemeKey

        val scheme = (processor as LazySchemeProcessor).createScheme(dataHolder = SchemeDataHolderImpl(processor = processor,
                                                                                                       bytes = bytes,
                                                                                                       externalInfo = externalInfo),
                                                                     name = schemeKey,
                                                                     attributeProvider = attributeProvider,
                                                                     isBundled = true)
        val oldInfo = schemeListManager.data.putSchemeInfo(scheme, externalInfo)
        LOG.assertTrue(oldInfo == null)
        val oldScheme = schemeListManager.readOnlyExternalizableSchemes.put(schemeKey, scheme)
        if (oldScheme != null) {
          LOG.warn("Duplicated scheme $schemeKey - old: $oldScheme, new $scheme")
        }
        schemes.add(scheme)
        return scheme
      }
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error("Cannot read scheme from $resourceName", e)
    }
    return null
  }

  private fun loadBytes(pluginDescriptor: PluginDescriptor?,
                        requestor: Any?,
                        resourceName: String): ByteArray? {
    val bytes: ByteArray?
    if (pluginDescriptor == null) {
      when (requestor) {
        is TempUIThemeLookAndFeelInfo -> {
          bytes = Files.readAllBytes(Path.of(resourceName))
        }
        is UITheme -> {
          bytes = ResourceUtil.getResourceAsBytes(resourceName.removePrefix("/"), requestor.providerClassLoader)
          if (bytes == null) {
            LOG.error("Cannot find $resourceName in ${requestor.providerClassLoader}")
            return null
          }
        }
        else -> {
          bytes = ResourceUtil.getResourceAsBytes(resourceName.removePrefix("/"),
                                                  (if (requestor is ClassLoader) requestor else requestor!!.javaClass.classLoader))
          if (bytes == null) {
            LOG.error("Cannot read scheme from $resourceName")
            return null
          }
        }
      }
    }
    else {
      val classLoader = pluginDescriptor.classLoader
      bytes = ResourceUtil.getResourceAsBytes(resourceName.removePrefix("/"), classLoader)
      if (bytes == null) {
        LOG.error("Cannot found scheme $resourceName in $classLoader")
        return null
      }
    }
    return bytes
  }

  internal fun createSchemeLoader(isDuringLoad: Boolean = false): SchemeLoader<T, MUTABLE_SCHEME> {
    val filesToDelete = HashSet(filesToDelete)
    // caller must call SchemeLoader.apply to bring back scheduled for deleting files
    this.filesToDelete.removeAll(filesToDelete)
    // SchemeLoader can use a retained list to bring back previously scheduled for deleting file,
    // but what if someone calls save() during a load and file will be deleted, although you should be loaded by a new load session
    // (because modified on disk)
    return SchemeLoader(schemeManager = this,
                        oldList = schemeListManager.data,
                        preScheduledFilesToDelete = filesToDelete,
                        isDuringLoad = isDuringLoad)
  }

  internal fun getFileExtension(fileName: CharSequence, isAllowAny: Boolean): String {
    return when {
      fileName.endsWith(schemeExtension, ignoreCase = true) -> schemeExtension
      fileName.endsWith(FileStorageCoreUtil.DEFAULT_EXT, ignoreCase = true) -> FileStorageCoreUtil.DEFAULT_EXT
      isAllowAny -> PathUtilRt.getFileExtension(fileName.toString())!!
      else -> throw IllegalStateException("Scheme file extension $fileName is unknown, must be filtered out")
    }
  }

  override fun loadSchemes(): Collection<T> {
    if (!isLoadingSchemes.compareAndSet(false, true)) {
      throw IllegalStateException("loadSchemes is already called")
    }

    try {
      // isDuringLoad is true even if loadSchemes called not first time, but on reload,
      // because scheme processor should use cumulative event `reloaded` to update runtime state/caches
      val schemeLoader = createSchemeLoader(isDuringLoad = true)
      val isLoadOnlyFromProvider = provider != null && provider.processChildren(fileSpec, roamingType,
                                                                                { canRead(it) }) { name, input, readOnly ->
        catchAndLog({ "${provider.javaClass.name}: $name" }) {
          val scheme = schemeLoader.loadScheme(name, input, null)
          if (readOnly && scheme != null) {
            schemeListManager.readOnlyExternalizableSchemes.put(processor.getSchemeKey(scheme), scheme)
          }
        }
        true
      }

      if (!isLoadOnlyFromProvider) {
        if (virtualFileResolver == null) {
          ioDirectory.directoryStreamIfExists({ canRead(it.fileName.toString()) }) { directoryStream ->
            for (file in directoryStream) {
              catchAndLog({ file.toString() }) {
                val bytes = try {
                  Files.readAllBytes(file)
                }
                catch (e: FileSystemException) {
                  when {
                    file.isDirectory() -> return@catchAndLog
                    else -> throw e
                  }
                }
                schemeLoader.loadScheme(file.fileName.toString(), null, bytes)
              }
            }
          }
        }
        else {
          for (file in getVirtualDirectory(StateStorageOperation.READ)?.children ?: VirtualFile.EMPTY_ARRAY) {
            catchAndLog({ file.path }) {
              if (canRead(file.nameSequence)) {
                schemeLoader.loadScheme(file.name, null, file.contentsToByteArray())
              }
            }
          }
        }
      }

      val newSchemes = schemeLoader.apply()
      for (newScheme in newSchemes) {
        if (processPendingCurrentSchemeName(newScheme)) {
          break
        }
      }

      fileChangeSubscriber?.invoke(this)

      return newSchemes
    }
    finally {
      isLoadingSchemes.set(false)
    }
  }

  override fun reload(retainFilter: ((scheme: T) -> Boolean)?) {
    processor.beforeReloaded(this)
    // we must not remove non-persistent (e.g., predefined) schemes, because we cannot load it (obviously)
    // do not schedule scheme file removing because we just need to update our runtime state, not state on disk
    removeExternalizableSchemesFromRuntimeState()
    processor.reloaded(this, loadSchemes())
  }

  // method is used to reflect already performed changes on disk, so, `isScheduleToDelete = false` is passed to `retainExternalInfo`
  internal fun removeExternalizableSchemesFromRuntimeState(retainFilter: ((scheme: T) -> Boolean)? = null) {
    val effectiveRetainFilter = retainFilter ?: { scheme ->
      ((scheme as? SerializableScheme)?.schemeState ?: processor.getState(scheme)) == SchemeState.NON_PERSISTENT
    }

    // todo check is bundled/read-only schemes correctly handled
    val list = schemeListManager.data
    val iterator = list.list.iterator()
    for (scheme in iterator) {
      if (effectiveRetainFilter(scheme)) {
        continue
      }

      activeScheme?.let {
        if (scheme === it) {
          currentPendingSchemeName = processor.getSchemeKey(it)
          activeScheme = null
        }
      }

      iterator.remove()

      schemeListManager.readOnlyExternalizableSchemes.remove(processor.getSchemeKey(scheme))

      @Suppress("UNCHECKED_CAST")
      processor.onSchemeDeleted(scheme as MUTABLE_SCHEME)
    }
    retainExternalInfo(isScheduleToDelete = false, schemeToInfo = list.schemeToInfo, newSchemes = list.list)
  }

  internal fun getFileName(scheme: T) = schemeListManager.getExternalInfo(scheme)?.fileNameWithoutExtension

  fun canRead(name: CharSequence): Boolean {
    return (updateExtension && name.endsWith(FileStorageCoreUtil.DEFAULT_EXT, true) || name.endsWith(schemeExtension, ignoreCase = true)) &&
           (processor !is LazySchemeProcessor || processor.isSchemeFile(name))
  }

  override fun save() {
    if (isLoadingSchemes.get()) {
      LOG.warn("Skip save - schemes are loading")
    }

    var hasSchemes = false
    val nameGenerator = UniqueNameGenerator()
    val changedSchemes = SmartList<MUTABLE_SCHEME>()
    val errorCollector = ErrorCollector()
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

      val fileName = getFileName(scheme)
      if (fileName != null && !isRenamed(scheme)) {
        nameGenerator.addExistingName(fileName)
      }
    }

    val filesToDelete = HashSet(filesToDelete)
    for (scheme in changedSchemes) {
      try {
        saveScheme(scheme, nameGenerator, filesToDelete)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        errorCollector.addError(RuntimeException("Cannot save scheme $fileSpec/$scheme", e))
      }
    }

    if (filesToDelete.isNotEmpty()) {
      schemeListManager.data.schemeToInfo.values.removeIf { filesToDelete.contains(it.fileName) }

      this.filesToDelete.removeAll(filesToDelete)
      deleteFiles(errorCollector, filesToDelete)

      // remove empty directory only if some file was deleted - avoid check on each save
      if (!hasSchemes && (provider == null || !provider.isApplicable(fileSpec, roamingType))) {
        removeDirectoryIfEmpty(errorCollector)
      }
    }

    errorCollector.getError()?.let {
      throw it
    }
  }

  override fun getSettingsCategory(): SettingsCategory {
    return settingsCategory
  }

  private fun removeDirectoryIfEmpty(errorCollector: ErrorCollector) {
    ioDirectory.directoryStreamIfExists {
      for (file in it) {
        if (!file.isHidden()) {
          LOG.info("Directory ${ioDirectory.fileName} is not deleted: at least one file ${file.fileName} exists")
          return@removeDirectoryIfEmpty
        }
      }
    }

    LOG.info("Remove scheme directory ${ioDirectory.fileName}")

    if (isUseVfs) {
      val dir = getVirtualDirectory(StateStorageOperation.WRITE)
      cachedVirtualDirectory = null
      if (dir != null) {
        runWriteAction {
          try {
            dir.delete(this)
          }
          catch (e: Throwable) {
            errorCollector.addError(e)
          }
        }
      }
    }
    else {
      try {
        NioFiles.deleteRecursively(ioDirectory)
      }
      catch (e: Throwable) {
        errorCollector.addError(e)
      }
    }
  }

  private fun saveScheme(scheme: MUTABLE_SCHEME, nameGenerator: UniqueNameGenerator, filesToDelete: MutableSet<String>) {
    var externalInfo: ExternalInfo? = schemeListManager.getExternalInfo(scheme)
    val currentFileNameWithoutExtension = externalInfo?.fileNameWithoutExtension
    val element = processor.writeScheme(scheme)?.let { it as? Element ?: (it as Document).detachRootElement() }
    if (element == null || element.isEmpty) {
      externalInfo?.scheduleDelete(filesToDelete, "empty")
      return
    }

    var fileNameWithoutExtension = currentFileNameWithoutExtension
    if (fileNameWithoutExtension == null || isRenamed(scheme)) {
      fileNameWithoutExtension = nameGenerator.generateUniqueName(schemeNameToFileName(processor.getSchemeKey(scheme)))
    }

    val fileName = fileNameWithoutExtension + schemeExtension
    // the file will be overwritten, so, we don't need to delete it
    filesToDelete.remove(fileName)

    val newDigest = hashElement(element)
    if (externalInfo != null &&
        currentFileNameWithoutExtension === fileNameWithoutExtension &&
        externalInfo.isDigestEquals(newDigest)) {
      return
    }
    else if (isEqualToBundledScheme(externalInfo = externalInfo, newDigest = newDigest, scheme = scheme, filesToDelete = filesToDelete)) {
      return

      // we must check it only here to avoid deleting an old scheme just because it is empty
      // (an old idea save -> a new idea deletes on open)
    }
    else if (processor is LazySchemeProcessor && processor.isSchemeDefault(scheme, newDigest)) {
      externalInfo?.scheduleDelete(filesToDelete, "equals to default")
      return
    }

    // stream provider always uses LF separator
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

    // if another new scheme uses the old name of this scheme, we must not delete it (as part of rename operation)
    val renamed = externalInfo != null &&
                  fileNameWithoutExtension !== currentFileNameWithoutExtension &&
                  currentFileNameWithoutExtension != null &&
                  nameGenerator.isUnique(currentFileNameWithoutExtension)
    if (providerPath == null) {
      if (isUseVfs) {
        var file: VirtualFile? = null
        var dir = getVirtualDirectory(StateStorageOperation.WRITE)
        if (dir == null || !dir.isValid) {
          dir = createDir(ioDirectory, this)
          cachedVirtualDirectory = dir
        }

        if (renamed) {
          val oldFile = dir.findChild(externalInfo!!.fileName)
          if (oldFile != null) {
            // VFS doesn't allow renaming to an existing file, so, check it
            if (dir.findChild(fileName) == null) {
              runWriteAction {
                oldFile.rename(this, fileName)
              }
              file = oldFile
            }
            else {
              externalInfo.scheduleDelete(filesToDelete, "renamed")
            }
          }
        }

        if (file == null) {
          file = SlowOperations.knownIssue("IDEA-338219, EA-867032").use {
            dir.getOrCreateChild(fileName, this)
          }
        }

        runWriteAction {
          file.getOutputStream(this).use { byteOut.writeTo(it) }
        }
      }
      else {
        if (renamed) {
          externalInfo!!.scheduleDelete(filesToDelete, "renamed")
        }
        ioDirectory.resolve(fileName).write(byteOut.internalBuffer, 0, byteOut.size())
      }
    }
    else {
      if (renamed) {
        externalInfo!!.scheduleDelete(filesToDelete, "renamed")
      }
      provider!!.write(providerPath, byteOut.toByteArray(), roamingType)
    }

    if (externalInfo == null) {
      externalInfo = ExternalInfo(fileNameWithoutExtension, schemeExtension)
      schemeListManager.data.schemeToInfo.put(scheme, externalInfo)
    }
    else {
      externalInfo.setFileNameWithoutExtension(fileNameWithoutExtension, schemeExtension)
    }
    externalInfo.digest = newDigest
    externalInfo.schemeKey = processor.getSchemeKey(scheme)
  }

  private fun isEqualToBundledScheme(externalInfo: ExternalInfo?,
                                     newDigest: Long,
                                     scheme: MUTABLE_SCHEME,
                                     filesToDelete: MutableSet<String>): Boolean {
    fun serializeIfPossible(scheme: T): Element? {
      return runCatching {
        @Suppress("UNCHECKED_CAST")
        val bundledAsMutable = scheme as? MUTABLE_SCHEME ?: return@runCatching null
        return@runCatching processor.writeScheme(bundledAsMutable) as Element
      }.getOrLogException(LOG)
    }

    val bundledScheme = schemeListManager.readOnlyExternalizableSchemes.get(processor.getSchemeKey(scheme))
    if (bundledScheme == null) {
      if ((processor as? LazySchemeProcessor)?.isSchemeEqualToBundled(scheme) == true) {
        externalInfo?.scheduleDelete(filesToDelete, "equals to bundled")
        return true
      }
      return false
    }

    val bundledExternalInfo = schemeListManager.getExternalInfo(bundledScheme) ?: return false
    if (bundledExternalInfo.digest == null) {
      serializeIfPossible(bundledScheme)?.let {
        bundledExternalInfo.digest = hashElement(it)
      } ?: return false
    }
    if (bundledExternalInfo.isDigestEquals(newDigest)) {
      externalInfo?.scheduleDelete(filesToDelete, "equals to bundled")
      return true
    }
    return false
  }

  private fun isRenamed(scheme: T): Boolean {
    val info = schemeListManager.getExternalInfo(scheme)
    return info != null && processor.getSchemeKey(scheme) != info.schemeKey
  }

  private fun deleteFiles(errorCollector: ErrorCollector, filesToDelete: MutableSet<String>) {
    if (provider != null) {
      val iterator = filesToDelete.iterator()
      for (name in iterator) {
        try {
          val spec = "$fileSpec/$name"
          if (provider.delete(spec, roamingType)) {
            LOG.debug { "$spec deleted from provider $provider" }
            iterator.remove()
          }
        }
        catch (e: Throwable) {
          errorCollector.addError(e)
        }
      }
    }

    if (filesToDelete.isEmpty()) {
      return
    }

    LOG.debug { "Delete scheme files: ${filesToDelete.joinToString()}" }

    if (isUseVfs) {
      getVirtualDirectory(StateStorageOperation.WRITE)?.let { virtualDir ->
        val childrenToDelete = virtualDir.children.filter { filesToDelete.contains(it.name) }
        if (childrenToDelete.isNotEmpty()) {
          runWriteAction {
            for (file in childrenToDelete) {
              try {
                file.delete(this)
              }
              catch (e: Throwable) {
                errorCollector.addError(e)
              }
            }
          }
        }
        return
      }
    }

    for (name in filesToDelete) {
      try {
        NioFiles.deleteRecursively(ioDirectory.resolve(name))
      }
      catch (e: Throwable) {
        errorCollector.addError(e)
      }
    }
  }

  internal fun getVirtualDirectory(reasonOperation: StateStorageOperation): VirtualFile? {
    var result = cachedVirtualDirectory
    if (result == null) {
      val path = ioDirectory.systemIndependentPath
      result = when (virtualFileResolver) {
        null -> LocalFileSystem.getInstance().findFileByPath(path)
        else -> virtualFileResolver.resolveVirtualFile(path, reasonOperation)
      }
      cachedVirtualDirectory = result
    }
    return result
  }

  override fun setSchemes(newSchemes: List<T>, newCurrentScheme: T?, removeCondition: Predicate<T>?) {
    schemeListManager.setSchemes(newSchemes = newSchemes,
                                 newCurrentScheme = newCurrentScheme,
                                 removeCondition = removeCondition?.let { it::test })
  }

  internal fun retainExternalInfo(isScheduleToDelete: Boolean, schemeToInfo: MutableMap<T, ExternalInfo>, newSchemes: List<T>) {
    if (schemeToInfo.isEmpty()) {
      return
    }

    val iterator = schemeToInfo.entries.iterator()
    l@ for ((scheme, info) in iterator) {
      if (schemeListManager.readOnlyExternalizableSchemes.get(processor.getSchemeKey(scheme)) === scheme) {
        continue
      }

      for (newScheme in newSchemes) {
        if (scheme === newScheme) {
          filesToDelete.remove(info.fileName)
          continue@l
        }
      }

      iterator.remove()
      if (isScheduleToDelete) {
        info.scheduleDelete(filesToDelete, "requested to delete")
      }
    }
  }

  override fun addScheme(scheme: T, replaceExisting: Boolean) = schemeListManager.addScheme(scheme, replaceExisting)

  override fun findSchemeByName(schemeName: String) = schemes.firstOrNull { processor.getSchemeKey(it) == schemeName }

  override fun removeScheme(name: String) = removeFirstScheme(true) { processor.getSchemeKey(it) == name }

  override fun removeScheme(scheme: T) = removeScheme(scheme, isScheduleToDelete = true)

  fun removeScheme(scheme: T, isScheduleToDelete: Boolean) = removeFirstScheme(isScheduleToDelete) { it === scheme } != null

  override fun isMetadataEditable(scheme: T) = !schemeListManager.readOnlyExternalizableSchemes.containsKey(processor.getSchemeKey(scheme))

  override fun toString() = fileSpec

  /**
   * Call this method before invoking [com.intellij.openapi.components.impl.stores.IComponentStore.save] to ensure that schema will be saved
   * even if there were no changes.
   */
  @TestOnly
  fun forceSaving() {
    schemeListManager.data.schemeToInfo.values.forEach { it.digest = null }
  }

  internal fun removeFirstScheme(isScheduleToDelete: Boolean, condition: (T) -> Boolean): T? {
    val iterator = schemes.iterator()
    for (scheme in iterator) {
      if (!condition(scheme)) {
        continue
      }

      if (activeScheme === scheme) {
        activeScheme = null
      }

      iterator.remove()

      if (isScheduleToDelete && processor.isExternalizable(scheme)) {
        schemeListManager.data.schemeToInfo.remove(scheme)?.scheduleDelete(filesToDelete, "requested to delete (removeFirstScheme)")
      }
      return scheme
    }

    return null
  }
}

private class ErrorCollector {
  private var error: Throwable? = null

  fun addError(error: Throwable) {
    if (error is CancellationException || error is ProcessCanceledException) {
      throw error
    }

    val compoundError = this.error
    if (compoundError == null) {
      this.error = error
    }
    else {
      compoundError.addSuppressed(error)
    }
  }

  fun getError(): Throwable? = error
}