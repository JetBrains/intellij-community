// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.schemeManager

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.configurationStore.*
import com.intellij.diagnostic.PluginException
import com.intellij.ide.ui.UITheme
import com.intellij.ide.ui.laf.TempUIThemeLookAndFeelInfo
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
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
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.*
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.write
import com.intellij.util.text.UniqueNameGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jdom.Document
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate
import kotlin.io.path.isDirectory
import kotlin.io.path.isHidden
import kotlin.io.path.readBytes

@ApiStatus.Internal
class SchemeManagerImpl<T : Scheme, MUTABLE_SCHEME : T>(
  val fileSpec: String,
  processor: SchemeProcessor<T, MUTABLE_SCHEME>,
  private val provider: StreamProvider?,
  internal val ioDirectory: Path,
  val roamingType: RoamingType = RoamingType.DEFAULT,
  val presentableName: String? = null,
  private val schemeNameToFileName: SchemeNameToFileName = CURRENT_NAME_CONVERTER,
  private val fileChangeSubscriber: FileChangeSubscriber? = null,
  private val settingsCategory: SettingsCategory = SettingsCategory.OTHER,
  cs: CoroutineScope? = null,
) : SchemeManagerBase<T, MUTABLE_SCHEME>(processor), SafeWriteRequestor, StorageManagerFileWriteRequestor {
  private val isUpdateVfs: Boolean = fileChangeSubscriber != null

  internal val isOldSchemeNaming: Boolean = schemeNameToFileName == OLD_NAME_CONVERTER

  private val isLoadingSchemes = AtomicBoolean()

  internal val schemeListManager: SchemeListManager<T> = SchemeListManager(this)

  internal val schemes: MutableList<T>
    get() = schemeListManager.schemes

  @Volatile
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
      schemeExtension = ComponentStorageUtil.DEFAULT_EXT
      updateExtension = false
    }

    if (isUpdateVfs) {
      cs!!.launch {  // tests should explicitly provide a scope when needed
        runCatching { refreshVirtualDirectory() }.getOrLogException(LOG)
      }
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
    // a parent component store refreshes the root directory, so we don't have to use `refreshAndFind*`
    val directory = LocalFileSystem.getInstance().findFileByNioFile(ioDirectory) ?: return
    cachedVirtualDirectory = directory
    directory.children
    (directory as? NewVirtualFile)?.markDirty()
    directory.refresh(true, false)
  }

  override fun loadBundledSchemes(providers: Sequence<LoadBundleSchemeRequest<T>>) {
    processor as LazySchemeProcessor

    schemeListManager.mutate { schemes, schemeToInfo, readOnlyExternalizableSchemes ->
      for (provider in providers) {
        try {
          val schemeKey = provider.schemeKey

          val fileNameWithoutExtension = schemeNameToFileName(schemeKey)
          val externalInfo = ExternalInfo(fileNameWithoutExtension, fileExtension = fileNameWithoutExtension + ComponentStorageUtil.DEFAULT_EXT)
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
        catch (e: CancellationException) { throw e }
        catch (e: ProcessCanceledException) { throw e }
        catch (e: Throwable) {
          LOG.error(PluginException(e, provider.pluginId))
        }
      }
    }
  }

  override fun loadBundledScheme(resourceName: String, requestor: Any?, pluginDescriptor: PluginDescriptor?): T? {
    try {
      val bytes = loadBytes(pluginDescriptor, requestor, resourceName) ?: return null
      lazyPreloadScheme(bytes, isOldSchemeNaming) { name, parser ->
        val attributeProvider: (String) -> String? = { parser.getAttributeValue(null, it) }
        val fileName = PathUtilRt.getFileName(resourceName)
        val extension = getFileExtension(fileName, isAllowAny = true)
        val externalInfo = ExternalInfo(fileNameWithoutExtension = fileName.substring(0, fileName.length - extension.length), extension)
        val schemeKey = name
                        ?: (processor as LazySchemeProcessor).getSchemeKey(attributeProvider, externalInfo.fileNameWithoutExtension)
                        ?: throw nameIsMissed(bytes)
        externalInfo.schemeKey = schemeKey
        val dataHolder = SchemeDataHolderImpl(processor, bytes, externalInfo)
        val scheme = (processor as LazySchemeProcessor).createScheme(dataHolder, schemeKey, attributeProvider, isBundled = true)
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
    catch (e: CancellationException) { throw e }
    catch (e: ProcessCanceledException) { throw e }
    catch (e: Throwable) {
      LOG.error("Cannot read scheme from $resourceName", e)
    }
    return null
  }

  private fun loadBytes(pluginDescriptor: PluginDescriptor?, requestor: Any?, resourceName: String): ByteArray? {
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
          val classLoader = requestor as? ClassLoader ?: requestor!!.javaClass.classLoader
          bytes = ResourceUtil.getResourceAsBytes(resourceName.removePrefix("/"), classLoader)
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
    // the caller must call SchemeLoader.apply to bring back scheduled for deleting files
    this.filesToDelete.removeAll(filesToDelete)
    // `SchemeLoader` can use a retained list to bring back a previously scheduled for deleting file;
    // but what if someone calls `save()` during a load and the file will be deleted,
    // although it should be loaded by a new load session (because modified on disk)?
    return SchemeLoader(schemeManager = this, oldList = schemeListManager.data, filesToDelete, isDuringLoad)
  }

  internal fun getFileExtension(fileName: CharSequence, isAllowAny: Boolean): String = when {
    fileName.endsWith(schemeExtension, ignoreCase = true) -> schemeExtension
    fileName.endsWith(ComponentStorageUtil.DEFAULT_EXT, ignoreCase = true) -> ComponentStorageUtil.DEFAULT_EXT
    isAllowAny -> PathUtilRt.getFileExtension(fileName.toString())!!
    else -> throw IllegalStateException("Scheme file extension $fileName is unknown, must be filtered out")
  }

  override fun loadSchemes(): Collection<T> {
    if (!isLoadingSchemes.compareAndSet(false, true)) {
      throw IllegalStateException("loadSchemes is already called")
    }

    try {
      // `isDuringLoad` is `true` even if `loadSchemes` called not first time, but on reload,
      // because a scheme processor should use a cumulative `reloaded` event to update runtime state/caches
      val schemeLoader = createSchemeLoader(isDuringLoad = true)
      val isLoadOnlyFromProvider = provider != null && provider.processChildren(fileSpec, roamingType, { canRead(it) }) { name, input, readOnly ->
        catchAndLog({ "${provider.javaClass.name}: $name" }) {
          val scheme = schemeLoader.loadScheme(name, input, null)
          if (readOnly && scheme != null) {
            schemeListManager.readOnlyExternalizableSchemes.put(processor.getSchemeKey(scheme), scheme)
          }
        }
        true
      }

      if (!isLoadOnlyFromProvider) {
        ioDirectory.directoryStreamIfExists { directoryStream ->
          for (file in directoryStream) {
            catchAndLog({ file.toString() }) {
              val fileName = file.fileName.toString()
              if (canRead(fileName)) {
                try {
                  schemeLoader.loadScheme(fileName, null, file.readBytes())
                }
                catch (e: FileSystemException) {
                  if (!file.isDirectory()) throw e
                }
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
    // we must not remove non-persistent (e.g., predefined) schemes, because we cannot load them
    // do not schedule the scheme file removing because we just need to update our runtime state, not state on disk
    removeExternalizableSchemesFromRuntimeState()
    processor.reloaded(this, loadSchemes())
  }

  // this method is used to reflect already performed changes on disk, so, `isScheduleToDelete = false` is passed to `retainExternalInfo`
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

  internal fun getFileName(scheme: T): String? = schemeListManager.getExternalInfo(scheme)?.fileNameWithoutExtension

  fun canRead(name: CharSequence): Boolean =
    (updateExtension && name.endsWith(ComponentStorageUtil.DEFAULT_EXT, true) || name.endsWith(schemeExtension, ignoreCase = true)) &&
    (processor !is LazySchemeProcessor || processor.isSchemeFile(name))

  override fun save() {
    val events = if (isUpdateVfs) mutableListOf<VFileEvent>() else Collections.emptyList()
    saveImpl(events)
    if (events.isNotEmpty()) {
      RefreshQueue.getInstance().processEvents(false, events)
    }
  }

  internal fun saveImpl(events: MutableList<VFileEvent>) {
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
        saveScheme(scheme, nameGenerator, filesToDelete, events)
      }
      catch (e: CancellationException) { throw e }
      catch (e: ProcessCanceledException) { throw e }
      catch (e: Throwable) {
        errorCollector.addError(RuntimeException("Cannot save scheme $fileSpec/$scheme", e))
      }
    }

    if (filesToDelete.isNotEmpty()) {
      schemeListManager.data.schemeToInfo.values.removeIf { filesToDelete.contains(it.fileName) }

      this.filesToDelete.removeAll(filesToDelete)
      deleteFiles(errorCollector, filesToDelete, events)

      // remove an empty directory only if some file was deleted - avoid check on each save
      if (!hasSchemes && (provider == null || !provider.isApplicable(fileSpec, roamingType))) {
        removeDirectoryIfEmpty(errorCollector, events)
      }
    }

    errorCollector.getError()?.let {
      throw it
    }
  }

  override fun getSettingsCategory(): SettingsCategory = settingsCategory

  private fun removeDirectoryIfEmpty(errorCollector: ErrorCollector, events: MutableList<VFileEvent>) {
    ioDirectory.directoryStreamIfExists {
      for (file in it) {
        if (!file.isHidden()) {
          LOG.info("Directory ${ioDirectory.fileName} is not deleted: at least one file ${file.fileName} exists")
          return@removeDirectoryIfEmpty
        }
      }
    }

    LOG.info("Remove scheme directory ${ioDirectory.fileName}")

    try {
      NioFiles.deleteRecursively(ioDirectory)
    }
    catch (e: Throwable) {
      errorCollector.addError(e)
    }

    if (isUpdateVfs) {
      val dir = getVirtualDirectory()
      cachedVirtualDirectory = null
      if (dir != null) {
        events += VFileDeleteEvent(/*requestor =*/ this, dir)
      }
    }
  }

  private fun saveScheme(scheme: MUTABLE_SCHEME, nameGenerator: UniqueNameGenerator, filesToDelete: MutableSet<String>, events: MutableList<VFileEvent>) {
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
    // the file will be overwritten, so we don't need to delete it
    filesToDelete.remove(fileName)

    val newDigest = hashElement(element)
    if (externalInfo != null &&
        currentFileNameWithoutExtension === fileNameWithoutExtension &&
        externalInfo.isDigestEquals(newDigest)) {
      return
    }
    else if (isEqualToBundledScheme(externalInfo, newDigest, scheme, filesToDelete)) {
      // we must check it only here to avoid deleting an old scheme just because it is empty
      // (an old version saves -> a new version deletes)
      return
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

    // if another new scheme uses the old name of this scheme, we must not delete it (as a part of the rename operation)
    val renamed = externalInfo != null &&
                  fileNameWithoutExtension !== currentFileNameWithoutExtension &&
                  currentFileNameWithoutExtension != null &&
                  nameGenerator.isUnique(currentFileNameWithoutExtension)
    if (providerPath == null) {
      if (renamed) {
        externalInfo.scheduleDelete(filesToDelete, "renamed")
      }

      var dir = if (isUpdateVfs) getVirtualDirectory() else null

      val ioFile = ioDirectory.resolve(fileName)
      ioFile.write(byteOut.internalBuffer, 0, byteOut.size())

      if (isUpdateVfs) {
        if (dir == null) {
          dir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(ioDirectory)
          cachedVirtualDirectory = dir
        }
        if (dir != null) {
          val file = dir.findChild(fileName)
          if (file != null) {
            events += updatingEvent(ioFile, file)
          }
          else {
            // an old file deletion event is generated by `deleteFiles`
            events += creationEvent(ioFile, dir)
          }
        }
      }
    }
    else {
      if (renamed) {
        externalInfo.scheduleDelete(filesToDelete, "renamed")
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

  private fun isEqualToBundledScheme(
    externalInfo: ExternalInfo?,
    newDigest: Long,
    scheme: MUTABLE_SCHEME,
    filesToDelete: MutableSet<String>
  ): Boolean {
    fun serializeIfPossible(scheme: T): Element? {
      @Suppress("UNCHECKED_CAST")
      val bundledAsMutable = scheme as? MUTABLE_SCHEME ?: return null
      return runCatching {
        processor.writeScheme(bundledAsMutable) as Element
      }.getOrLogException(LOG)
    }

    val bundledScheme = schemeListManager.readOnlyExternalizableSchemes[processor.getSchemeKey(scheme)]
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

  private fun deleteFiles(errorCollector: ErrorCollector, filesToDelete: MutableSet<String>, events: MutableList<VFileEvent>) {
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

    for (name in filesToDelete) {
      try {
        NioFiles.deleteRecursively(ioDirectory.resolve(name))
      }
      catch (e: Throwable) {
        errorCollector.addError(e)
      }
    }

    if (isUpdateVfs) {
      val dir = getVirtualDirectory()
      if (dir != null) {
        for (file in dir.children) {
          if (file.isValid && file.name in filesToDelete) {
            events += VFileDeleteEvent(/*requestor =*/ this, file)
          }
        }
      }
    }
  }

  internal fun getVirtualDirectory(): VirtualFile? {
    var result = cachedVirtualDirectory
    if (result == null) {
      result = LocalFileSystem.getInstance().findFileByNioFile(ioDirectory)
      cachedVirtualDirectory = result
    }
    return result
  }

  override fun setSchemes(newSchemes: List<T>, newCurrentScheme: T?, removeCondition: Predicate<T>?) {
    schemeListManager.setSchemes(newSchemes, newCurrentScheme, removeCondition?.let { it::test })
  }

  internal fun retainExternalInfo(isScheduleToDelete: Boolean, schemeToInfo: MutableMap<T, ExternalInfo>, newSchemes: List<T>) {
    if (schemeToInfo.isEmpty()) {
      return
    }

    val iterator = schemeToInfo.entries.iterator()
    l@ for ((scheme, info) in iterator) {
      if (schemeListManager.readOnlyExternalizableSchemes[processor.getSchemeKey(scheme)] === scheme) {
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

  override fun addScheme(scheme: T, replaceExisting: Boolean): Unit = schemeListManager.addScheme(scheme, replaceExisting)

  override fun findSchemeByName(schemeName: String): T? = schemes.firstOrNull { processor.getSchemeKey(it) == schemeName }

  override fun removeScheme(name: String): T? = removeFirstScheme(true) { processor.getSchemeKey(it) == name }

  override fun removeScheme(scheme: T): Boolean = removeScheme(scheme, isScheduleToDelete = true)

  fun removeScheme(scheme: T, isScheduleToDelete: Boolean): Boolean = removeFirstScheme(isScheduleToDelete) { it === scheme } != null

  override fun isMetadataEditable(scheme: T): Boolean = !schemeListManager.readOnlyExternalizableSchemes.containsKey(processor.getSchemeKey(scheme))

  override fun toString(): String = fileSpec

  /**
   * Call this method before invoking [com.intellij.openapi.components.impl.stores.IComponentStore.save]
   * to ensure that a schema will be saved even when there are no changes.
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

  private class ErrorCollector {
    private var error: Throwable? = null

    fun addError(error: Throwable) {
      if (error is CancellationException || error is ProcessCanceledException) {
        throw error
      }
      this.error = addSuppressed(this.error, error)
    }

    fun getError(): Throwable? = error
  }
}
