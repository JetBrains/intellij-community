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
import com.intellij.openapi.util.Comparing
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
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.messages.MessageBus
import com.intellij.util.text.UniqueNameGenerator
import gnu.trove.THashMap
import gnu.trove.THashSet
import gnu.trove.TObjectObjectProcedure
import org.jdom.Document
import org.jdom.Element
import org.xmlpull.mxp1.MXParser
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import java.util.function.Function

class SchemeManagerImpl<T : Scheme, MUTABLE_SCHEME : T>(val fileSpec: String,
                                                        private val processor: SchemeProcessor<T, MUTABLE_SCHEME>,
                                                        private val provider: StreamProvider?,
                                                        private val ioDirectory: Path,
                                                        val roamingType: RoamingType = RoamingType.DEFAULT,
                                                        val presentableName: String? = null,
                                                        private val isUseOldFileNameSanitize: Boolean = false,
                                                        private val messageBus: MessageBus? = null) : SchemeManager<T>(), SafeWriteRequestor {
  private val schemes = ArrayList<T>()
  private val readOnlyExternalizableSchemes = THashMap<String, T>()

  /**
   * Schemes can be lazy loaded, so, client should be able to set current scheme by name, not only by instance.
   */
  private @Volatile var currentPendingSchemeName: String? = null

  private var currentScheme: T? = null

  private var cachedVirtualDirectory: VirtualFile? = null

  private val schemeExtension: String
  private val updateExtension: Boolean

  private val filesToDelete = THashSet<String>()

  // scheme could be changed - so, hashcode will be changed - we must use identity hashing strategy
  private val schemeToInfo = THashMap<T, ExternalInfo>(ContainerUtil.identityStrategy())

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
        if (event.requestor != null) {
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

            val newScheme = readSchemeFromFile(event.file, false)?.let {
              processor.initScheme(it)
              processor.onSchemeAdded(it)
              it
            }

            updateCurrentScheme(oldCurrentScheme, newScheme)
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
      val readScheme = readSchemeFromFile(file, false)
      if (readScheme != null) {
        processor.initScheme(readScheme)
        processor.onSchemeAdded(readScheme)
      }
    }

    private fun updateCurrentScheme(oldCurrentScheme: T?, newCurrentScheme: T? = null) {
      if (currentScheme != null) {
        return
      }

      if (oldCurrentScheme != currentScheme) {
        @Suppress("UNCHECKED_CAST")
        setCurrent(newCurrentScheme ?: schemes.firstOrNull())
      }
      else if (newCurrentScheme != null) {
        processPendingCurrentSchemeName(newCurrentScheme)
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
    val newSchemesOffset = schemes.size
    if (provider != null && provider.enabled) {
      provider.processChildren(fileSpec, roamingType, { canRead(it) }) { name, input, readOnly ->
        val scheme = loadScheme(name, input, true)
        if (readOnly && scheme != null) {
          readOnlyExternalizableSchemes.put(scheme.name, scheme)
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

          try {
            loadScheme(file.fileName.toString(), file.inputStream(), true)
          }
          catch (e: Throwable) {
            LOG.error("Cannot read scheme $file", e)
          }
        }
      }
    }

    val list = SmartList<T>()
    @Suppress("UNCHECKED_CAST")
    for (i in newSchemesOffset..schemes.size - 1) {
      val scheme = schemes.get(i) as MUTABLE_SCHEME
      processor.initScheme(scheme)
      list.add(scheme)

      @Suppress("UNCHECKED_CAST")
      processPendingCurrentSchemeName(scheme)
    }

    messageBus?.let { it.connect().subscribe(VirtualFileManager.VFS_CHANGES, SchemeFileTracker()) }

    return list
  }

  fun reload() {
    // we must not remove non-persistent (e.g. predefined) schemes, because we cannot load it (obviously)
    removeExternalizableSchemes()

    loadSchemes()
  }

  private fun removeExternalizableSchemes() {
    // todo check is bundled/read-only schemes correctly handled
    for (i in schemes.indices.reversed()) {
      val scheme = schemes.get(i)
      if (processor.getState(scheme) != SchemeState.NON_PERSISTENT) {
        if (scheme == currentScheme) {
          currentScheme = null
        }

        @Suppress("UNCHECKED_CAST")
        processor.onSchemeDeleted(scheme as MUTABLE_SCHEME)
      }
    }
    retainExternalInfo(schemes)
  }

  private fun findExternalizableSchemeByFileName(fileName: String): MUTABLE_SCHEME? {
    for (scheme in schemes) {
      @Suppress("UNCHECKED_CAST")
      if (fileName == "${scheme.fileName}$schemeExtension") {
        return scheme as MUTABLE_SCHEME
      }
    }
    return null
  }

  private fun isOverwriteOnLoad(existingScheme: T): Boolean {
    if (readOnlyExternalizableSchemes.get(existingScheme.name) === existingScheme) {
      // so, bundled scheme is shadowed
      return true
    }

    val info = schemeToInfo.get(existingScheme)
    // scheme from file with old extension, so, we must ignore it
    return info != null && schemeExtension != info.fileExtension
  }

  private inner class SchemeDataHolderImpl(private val bytes: ByteArray, private val externalInfo: ExternalInfo) : SchemeDataHolder {
    override fun read(): Element = loadElement(bytes.inputStream())

    override fun updateDigest() {
      schemeToInfo.forEachEntry({ k, v ->
        if (v !== externalInfo) {
          return@forEachEntry true
        }

        @Suppress("UNCHECKED_CAST")
        try {
          externalInfo.digest = (processor.writeScheme(k as MUTABLE_SCHEME) as Element).digest()
        }
        catch (e: WriteExternalException) {
          LOG.error("Cannot update digest", e)
        }
        false
      })
    }
  }

  private fun loadScheme(fileName: CharSequence, input: InputStream, duringLoad: Boolean): MUTABLE_SCHEME? {
    try {
      return doLoadScheme(fileName, input, duringLoad)
    }
    catch (e: Throwable) {
      LOG.error("Cannot read scheme $fileName", e)
      return null
    }
  }

  private fun doLoadScheme(fileName: CharSequence, input: InputStream, duringLoad: Boolean): MUTABLE_SCHEME? {
    val extension = getFileExtension(fileName, false)
    if (duringLoad && filesToDelete.isNotEmpty() && filesToDelete.contains(fileName.toString())) {
      LOG.warn("Scheme file \"$fileName\" is not loaded because marked to delete")
      return null
    }

    val fileNameWithoutExtension = fileName.subSequence(0, fileName.length - extension.length).toString()
    fun checkExisting(schemeName: String): Boolean {
      if (!duringLoad) {
        return true
      }

      findSchemeByName(schemeName)?.let { existingScheme ->
        if (processor.isExternalizable(existingScheme) && isOverwriteOnLoad(existingScheme)) {
          removeScheme(existingScheme)
        }
        else {
          if (schemeExtension != extension && schemeToInfo.get(existingScheme as Scheme)?.fileNameWithoutExtension == fileNameWithoutExtension) {
            // 1.oldExt is loading after 1.newExt - we should delete 1.oldExt
            filesToDelete.add(fileName.toString())
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
              scheme = processor.createScheme(SchemeDataHolderImpl(bytes, externalInfo), schemeName, attributeProvider, duringLoad)
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
      addScheme(scheme as T)
    }
    return scheme
  }

  private val T.fileName: String?
    get() = schemeToInfo.get(this)?.fileNameWithoutExtension

  private fun canRead(name: CharSequence) = (updateExtension && name.endsWith(DEFAULT_EXT, true) || name.endsWith(schemeExtension, true)) && (processor !is LazySchemeProcessor || processor.isSchemeFile(name))

  private fun readSchemeFromFile(file: VirtualFile, duringLoad: Boolean): MUTABLE_SCHEME? {
    val fileName = file.nameSequence
    if (file.isDirectory || !canRead(fileName)) {
      return null
    }

    try {
      return loadScheme(fileName, file.inputStream, duringLoad)
    }
    catch (e: Throwable) {
      LOG.error("Cannot read scheme $fileName", e)
      return null
    }
  }

  fun save(errors: MutableList<Throwable>) {
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

    if (!filesToDelete.isEmpty) {
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
      errors.catch { ioDirectory.deleteRecursively() }
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

    val fileName = fileNameWithoutExtension!! + schemeExtension
    // file will be overwritten, so, we don't need to delete it
    filesToDelete.remove(fileName)

    // stream provider always use LF separator
    val byteOut = element.toBufferExposingByteArray()

    var providerPath: String?
    if (provider != null && provider.enabled) {
      providerPath = fileSpec + '/' + fileName
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
    val oldCurrentScheme = currentScheme
    if (removeCondition == null) {
      schemes.clear()
    }
    else {
      schemes.removeAll { removeCondition.value(it) }
    }

    retainExternalInfo(newSchemes)

    schemes.addAll(newSchemes)

    if (oldCurrentScheme != newCurrentScheme) {
      if (newCurrentScheme != null) {
        currentScheme = newCurrentScheme
      }
      else if (oldCurrentScheme != null && !schemes.contains(oldCurrentScheme)) {
        currentScheme = schemes.firstOrNull()
      }

      if (oldCurrentScheme != currentScheme) {
        processor.onCurrentSchemeChanged(oldCurrentScheme)
      }
    }
  }

  private fun retainExternalInfo(newSchemes: List<T>) {
    if (schemeToInfo.isEmpty) {
      return
    }

    schemeToInfo.retainEntries(TObjectObjectProcedure { scheme, info ->
      if (readOnlyExternalizableSchemes[scheme.name] == scheme) {
        return@TObjectObjectProcedure true
      }

      for (t in newSchemes) {
        // by identity
        if (t === scheme) {
          if (filesToDelete.isNotEmpty()) {
            filesToDelete.remove("${info.fileName}")
          }
          return@TObjectObjectProcedure true
        }
      }

      info.scheduleDelete()
      false
    })
  }

  override fun addNewScheme(scheme: T, replaceExisting: Boolean) {
    var toReplace = -1
    for (i in schemes.indices) {
      val existing = schemes.get(i)
      if (existing.name == scheme.name) {
        if (!Comparing.equal<Class<out Scheme>>(existing.javaClass, scheme.javaClass)) {
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
      val info = schemeToInfo.get(scheme)
      if (info != null) {
        filesToDelete.remove("${info.fileName}")
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
    schemeToInfo.forEachValue {
      it.scheduleDelete()
      true
    }

    currentScheme = null
    schemes.clear()
    schemeToInfo.clear()
  }

  override fun getAllSchemes(): List<T> = Collections.unmodifiableList(schemes)

  override fun isEmpty() = schemes.isEmpty()

  override fun findSchemeByName(schemeName: String): T? {
    for (scheme in schemes) {
      if (scheme.name == schemeName) {
        return scheme
      }
    }
    return null
  }

  override fun setCurrent(scheme: T?, notify: Boolean) {
    currentPendingSchemeName = null

    val oldCurrent = currentScheme
    currentScheme = scheme
    if (notify && oldCurrent != scheme) {
      processor.onCurrentSchemeChanged(oldCurrent)
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

  override fun removeScheme(scheme: T) {
    for (i in schemes.size - 1 downTo 0) {
      val s = schemes[i]
      if (scheme.name == s.name) {
        if (currentScheme == s) {
          currentScheme = null
        }

        if (processor.isExternalizable(s)) {
          schemeToInfo.remove(s)?.scheduleDelete()
        }
        schemes.removeAt(i)
        break
      }
    }
  }

  override fun getAllSchemeNames() = if (schemes.isEmpty()) emptyList() else schemes.map { it.name }

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

class DigestOutputStream(val digest: MessageDigest) : OutputStream() {
  override fun write(b: Int) {
    digest.update(b.toByte())
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    digest.update(b, off, len)
  }

  override fun toString(): String {
    return "[Digest Output Stream] " + digest.toString()
  }
}

fun Element.digest(): ByteArray {
  // sha-1 is enough, sha-256 is slower, see https://www.nayuki.io/page/native-hash-functions-for-java
  val digest = MessageDigest.getInstance("SHA-1")
  serializeElementToBinary(this, DigestOutputStream(digest))
  return digest.digest()
}