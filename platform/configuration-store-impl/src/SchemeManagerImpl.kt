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

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ex.DecodeDefaultsUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil.DEFAULT_EXT
import com.intellij.openapi.extensions.AbstractExtensionPointBean
import com.intellij.openapi.options.*
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.*
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.messages.MessageBus
import com.intellij.util.text.UniqueNameGenerator
import gnu.trove.THashSet
import org.jdom.Document
import org.jdom.Element
import org.xmlpull.mxp1.MXParser
import org.xmlpull.v1.XmlPullParser
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
                                                        private val messageBus: MessageBus? = null) : SchemeManager<T>(), SafeWriteRequestor {
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
  private val schemeToInfo = ContainerUtil.newConcurrentMap<T, ExternalInfo>(ContainerUtil.identityStrategy())

  private val useVfs = messageBus != null

  init {
    if (processor is SchemeExtensionProvider) {
      schemeExtension = processor.schemeExtension
      updateExtension = processor.isUpgradeNeeded
    }
    else {
      schemeExtension = FileStorageCoreUtil.DEFAULT_EXT
      updateExtension = false
    }

    if (useVfs && (provider == null || !provider.enabled)) {
      try {
        refreshVirtualDirectoryAndAddListener()
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
  }

  private inner class SchemeFileTracker() : BulkFileListener.Adapter() {
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

            updateCurrentScheme(oldCurrentScheme, readSchemeFromFile(event.file)?.let {
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
      val readScheme = readSchemeFromFile(file)
      if (readScheme != null) {
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

  override fun loadBundledScheme(resourceName: String, requestor: Any, convertor: ThrowableConvertor<Element, T, Throwable>) {
    try {
      val url = if (requestor is AbstractExtensionPointBean)
        requestor.loaderForClass.getResource(resourceName)
      else
        DecodeDefaultsUtil.getDefaults(requestor, resourceName)
      if (url == null) {
        LOG.error("Cannot read scheme from $resourceName")
        return
      }

      val element = loadElement(URLUtil.openStream(url))
      val scheme = convertor.convert(element)
      if (processor.isExternalizable(scheme)) {
        val fileName = PathUtilRt.getFileName(url.path)
        val extension = getFileExtension(fileName, true)
        val info = ExternalInfo(fileName.substring(0, fileName.length - extension.length), extension)
        info.digest = element.digest()
        info.schemeName = scheme.name
        val oldInfo = schemeToInfo.put(scheme, info)
        LOG.assertTrue(oldInfo == null)
        val oldScheme = readOnlyExternalizableSchemes.put(scheme.name, scheme)
        if (oldScheme != null) {
          LOG.warn("Duplicated scheme ${scheme.name} - old: $oldScheme, new $scheme")
        }
      }

      schemes.add(scheme)
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
      if (provider != null && provider.isApplicable(fileSpec, roamingType)) {
        provider.processChildren(fileSpec, roamingType, { canRead(it) }) { name, input, readOnly ->
          catchAndLog(name) {
            val scheme = loadScheme(name, input, schemes, filesToDelete)
            if (readOnly && scheme != null) {
              readOnlyExternalizableSchemes.put(scheme.name, scheme)
            }
          }
          true
        }
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

      messageBus?.let { it.connect().subscribe(VirtualFileManager.VFS_CHANGES, SchemeFileTracker()) }

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

  fun reload() {
    // we must not remove non-persistent (e.g. predefined) schemes, because we cannot load it (obviously)
    removeExternalizableSchemes()

    loadSchemes()
  }

  private fun removeExternalizableSchemes() {
    // todo check is bundled/read-only schemes correctly handled
    val iterator = schemes.iterator()
    for (scheme in iterator) {
      if (processor.getState(scheme) == SchemeState.NON_PERSISTENT) {
        continue
      }

      if (scheme === currentScheme) {
        currentScheme = null
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
        externalInfo.digest = (processor.writeScheme(scheme) as Element).digest()
      }
      catch (e: WriteExternalException) {
        LOG.error("Cannot update digest", e)
      }
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
          removeFirstScheme({ it === existingScheme }, schemes, scheduleDelete = false)
          return true
        }
        else if (processor.isExternalizable(existingScheme) && isOverwriteOnLoad(existingScheme)) {
          removeFirstScheme({ it === existingScheme }, schemes)
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
      val parser = MXParser()
      parser.setInput(bytes.inputStream().reader())
      var eventType = parser.eventType
      read@ do {
        when (eventType) {
          XmlPullParser.START_TAG -> {
            if (!isUseOldFileNameSanitize || parser.name != "component") {
              var name: String? = null
              if (isUseOldFileNameSanitize && parser.name == "profile") {
                eventType = parser.next()
                findName@ while (eventType != XmlPullParser.END_DOCUMENT) {
                  when (eventType) {
                    XmlPullParser.START_TAG -> {
                      if (parser.name == "option" && parser.getAttributeValue(null, "name") == "myName") {
                        name = parser.getAttributeValue(null, "value")
                        break@findName
                      }
                    }
                  }

                  eventType = parser.next()
                }
              }

              val attributeProvider = Function<String, String?> { parser.getAttributeValue(null, it) }
              val schemeName = name ?: processor.getName(attributeProvider)
              if (!checkExisting(schemeName)) {
                return null
              }

              val externalInfo = createInfo(schemeName, null)
              val dataHolder = SchemeDataHolderImpl(bytes, externalInfo)
              scheme = processor.createScheme(dataHolder, schemeName, attributeProvider)
              schemeToInfo.put(scheme, externalInfo)
              break@read
            }
          }
        }
        eventType = parser.next()
      }
      while (eventType != XmlPullParser.END_DOCUMENT)
    }
    else {
      val element = loadElement(input)
      scheme = (processor as NonLazySchemeProcessor).readScheme(element, duringLoad) ?: return null
      val schemeName = scheme.name
      if (!checkExisting(schemeName)) {
        return null
      }

      schemeToInfo.put(scheme, createInfo(schemeName, element))
    }

    @Suppress("UNCHECKED_CAST")
    if (duringLoad) {
      schemes.add(scheme as T)
    }
    else {
      addNewScheme(scheme as T, true)
    }
    return scheme
  }

  private val T.fileName: String?
    get() = schemeToInfo.get(this)?.fileNameWithoutExtension

  fun canRead(name: CharSequence) = (updateExtension && name.endsWith(DEFAULT_EXT, true) || name.endsWith(schemeExtension, true)) && (processor !is LazySchemeProcessor || processor.isSchemeFile(name))

  private fun readSchemeFromFile(file: VirtualFile, schemes: MutableList<T> = this.schemes): MUTABLE_SCHEME? {
    val fileName = file.name
    if (file.isDirectory || !canRead(fileName)) {
      return null
    }

    catchAndLog(fileName) {
      return file.inputStream.use { loadScheme(fileName, it, schemes) }
    }

    return null
  }

  fun save(errors: MutableList<Throwable>) {
    if (isLoadingSchemes.get()) {
      LOG.warn("Skip save - schemes are loading")
    }

    var hasSchemes = false
    val nameGenerator = UniqueNameGenerator()
    val schemesToSave = SmartList<MUTABLE_SCHEME>()
    for (scheme in schemes) {
      val state = processor.getState(scheme)
      if (state == SchemeState.NON_PERSISTENT) {
        continue
      }

      hasSchemes = true

      if (state != SchemeState.UNCHANGED) {
        @Suppress("UNCHECKED_CAST")
        schemesToSave.add(scheme as MUTABLE_SCHEME)
      }

      val fileName = scheme.fileName
      if (fileName != null && !isRenamed(scheme)) {
        nameGenerator.addExistingName(fileName)
      }
    }

    for (scheme in schemesToSave) {
      try {
        saveScheme(scheme, nameGenerator)
      }
      catch (e: Throwable) {
        errors.add(RuntimeException("Cannot save scheme $fileSpec/$scheme", e))
      }
    }

    if (!filesToDelete.isEmpty()) {
      deleteFiles(errors)
      // remove empty directory only if some file was deleted - avoid check on each save
      if (!hasSchemes && (provider == null || !provider.enabled)) {
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
        runWriteAction {
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
    val element = if (parent is Element) parent else (parent as Document).detachRootElement()
    if (JDOMUtil.isEmpty(element)) {
      externalInfo?.scheduleDelete()
      return
    }

    var fileNameWithoutExtension = currentFileNameWithoutExtension
    if (fileNameWithoutExtension == null || isRenamed(scheme)) {
      fileNameWithoutExtension = nameGenerator.generateUniqueName(FileUtil.sanitizeFileName(scheme.name, isUseOldFileNameSanitize))
    }

    val newDigest = element!!.digest()
    if (externalInfo != null && currentFileNameWithoutExtension === fileNameWithoutExtension && externalInfo.isDigestEquals(newDigest)) {
      return
    }

    // save only if scheme differs from bundled
    val bundledScheme = readOnlyExternalizableSchemes.get(scheme.name)
    if (bundledScheme != null && schemeToInfo.get(bundledScheme)?.isDigestEquals(newDigest) ?: false) {
      externalInfo?.scheduleDelete()
      return
    }

    // we must check it only here to avoid delete old scheme just because it is empty (old idea save -> new idea delete on open)
    if (processor is LazySchemeProcessor && processor.isSchemeDefault(scheme, newDigest)) {
      externalInfo?.scheduleDelete()
      return
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

    // if another new scheme uses old name of this scheme, so, we must not delete it (as part of rename operation)
    val renamed = externalInfo != null && fileNameWithoutExtension !== currentFileNameWithoutExtension && nameGenerator.value(currentFileNameWithoutExtension)
    if (providerPath == null) {
      if (useVfs) {
        var file: VirtualFile? = null
        var dir = virtualDirectory
        if (dir == null || !dir.isValid) {
          dir = createDir(ioDirectory, this)
          cachedVirtualDirectory = dir
        }

        if (renamed) {
          file = dir.findChild(externalInfo!!.fileName)
          if (file != null) {
            runWriteAction {
              file!!.rename(this, fileName)
            }
          }
        }

        if (file == null) {
          file = getFile(fileName, dir, this)
        }

        runWriteAction {
          file!!.getOutputStream(this).use {
            byteOut.writeTo(it)
          }
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

  private fun ExternalInfo.scheduleDelete() {
    filesToDelete.add(fileName)
  }

  private fun isRenamed(scheme: T): Boolean {
    val info = schemeToInfo.get(scheme)
    return info != null && scheme.name != info.schemeName
  }

  private fun deleteFiles(errors: MutableList<Throwable>) {
    val deleteUsingIo: Boolean
    if (provider != null && provider.enabled) {
      deleteUsingIo = false
      for (name in filesToDelete) {
        errors.catch {
          val spec = "$fileSpec/$name"
          if (provider.isApplicable(spec, roamingType)) {
            provider.delete(spec, roamingType)
          }
        }
      }
    }
    else if (!useVfs) {
      deleteUsingIo = true
    }
    else {
      val dir = virtualDirectory
      deleteUsingIo = dir == null
      if (!deleteUsingIo) {
        var token: AccessToken? = null
        try {
          for (file in dir!!.children) {
            if (filesToDelete.contains(file.name)) {
              if (token == null) {
                token = WriteAction.start()
              }

              errors.catch {
                file.delete(this)
              }
            }
          }
        }
        finally {
          token?.finish()
        }
      }
    }

    if (deleteUsingIo) {
      for (name in filesToDelete) {
        errors.catch { ioDirectory.resolve(name).delete() }
      }
    }

    filesToDelete.clear()
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
    if (removeCondition == null) {
      schemes.clear()
    }
    else {
      val iterator = schemes.iterator()
      for (scheme in iterator) {
        if (removeCondition.value(scheme)) {
          iterator.remove()
        }
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
    for (scheme in schemes) {
      result.add(scheme.name)
    }
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

  override fun removeScheme(schemeName: String) = removeFirstScheme({it.name == schemeName}, schemes)

  override fun removeScheme(scheme: T) {
    removeFirstScheme({ it == scheme }, schemes)
  }

  private fun removeFirstScheme(condition: (T) -> Boolean, schemes: MutableList<T>, scheduleDelete: Boolean = true): T? {
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

  private class ExternalInfo(var fileNameWithoutExtension: String, var fileExtension: String?) {
    // we keep it to detect rename
    var schemeName: String? = null

    var digest: ByteArray? = null

    val fileName: String
      get() = "$fileNameWithoutExtension$fileExtension"

    fun setFileNameWithoutExtension(nameWithoutExtension: String, extension: String) {
      fileNameWithoutExtension = nameWithoutExtension
      fileExtension = extension
    }

    fun isDigestEquals(newDigest: ByteArray) = Arrays.equals(digest, newDigest)

    override fun toString() = fileName
  }

  override fun toString() = fileSpec
}

private fun ExternalizableScheme.renameScheme(newName: String) {
  if (newName != name) {
    name = newName
    LOG.assertTrue(newName == name)
  }
}

private inline fun MutableList<Throwable>.catch(runnable: () -> Unit) {
  try {
    runnable()
  }
  catch (e: Throwable) {
    add(e)
  }
}

fun createDir(ioDir: Path, requestor: Any): VirtualFile {
  ioDir.createDirectories()
  val parentFile = ioDir.parent
  val parentVirtualFile = (if (parentFile == null) null else VfsUtil.createDirectoryIfMissing(parentFile.systemIndependentPath)) ?: throw IOException(ProjectBundle.message("project.configuration.save.file.not.found", parentFile))
  return getFile(ioDir.fileName.toString(), parentVirtualFile, requestor)
}

fun getFile(fileName: String, parent: VirtualFile, requestor: Any): VirtualFile {
  return parent.findChild(fileName) ?: runWriteAction { parent.createChildData(requestor, fileName) }
}

private inline fun catchAndLog(fileName: String, runnable: (fileName: String) -> Unit) {
  try {
    runnable(fileName)
  }
  catch (e: Throwable) {
    LOG.error("Cannot read scheme $fileName", e)
  }
}