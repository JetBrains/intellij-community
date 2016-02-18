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

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ex.DecodeDefaultsUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil.DEFAULT_EXT
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.AbstractExtensionPointBean
import com.intellij.openapi.options.*
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.tracker.VirtualFileTracker
import com.intellij.util.*
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.text.UniqueNameGenerator
import gnu.trove.THashMap
import gnu.trove.THashSet
import gnu.trove.TObjectObjectProcedure
import org.jdom.Document
import org.jdom.Element
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.*

class SchemeManagerImpl<T : Scheme, E : ExternalizableScheme>(val fileSpec: String,
                                                              private val processor: SchemeProcessor<E>,
                                                              private val provider: StreamProvider?,
                                                              private val ioDirectory: Path,
                                                              val roamingType: RoamingType = RoamingType.DEFAULT,
                                                              virtualFileTrackerDisposable: Disposable? = null,
                                                              val presentableName: String? = null) : SchemesManager<T, E>(), SafeWriteRequestor {
  private val schemes = ArrayList<T>()
  private val readOnlyExternalizableSchemes = THashMap<String, E>()

  /**
   * Schemes can be lazy loaded, so, client should be able to set current scheme by name, not only by instance.
   */
  private @Volatile var currentPendingSchemeName: String? = null

  private var currentScheme: T? = null

  private var directory: VirtualFile? = null

  private val schemeExtension: String
  private val updateExtension: Boolean

  private val filesToDelete = THashSet<String>()

  // scheme could be changed - so, hashcode will be changed - we must use identity hashing strategy
  private val schemeToInfo = THashMap<E, ExternalInfo>(ContainerUtil.identityStrategy())

  private val useVfs = virtualFileTrackerDisposable != null

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
        refreshVirtualDirectoryAndAddListener(virtualFileTrackerDisposable)
      }
      catch  (e: Throwable) {
        LOG.error(e)
      }
    }
  }

  private fun refreshVirtualDirectoryAndAddListener(virtualFileTrackerDisposable: Disposable?) {
    // store refreshes root directory, so, we don't need to use refreshAndFindFile
    val directory = LocalFileSystem.getInstance().findFileByPath(ioDirectory.systemIndependentPath) ?: return

    this.directory = directory
    directory.children
    if (directory is NewVirtualFile) {
      directory.markDirty()
    }

    directory.refresh(true, false, {
      addVfsListener(virtualFileTrackerDisposable)
    })
  }

  private fun addVfsListener(virtualFileTrackerDisposable: Disposable?) {
    service<VirtualFileTracker>().addTracker("${LocalFileSystem.PROTOCOL_PREFIX}${ioDirectory.toAbsolutePath().systemIndependentPath}", object : VirtualFileAdapter() {
      override fun contentsChanged(event: VirtualFileEvent) {
        if (event.requestor != null || !isMy(event)) {
          return
        }

        val oldScheme = findExternalizableSchemeByFileName(event.file.name)
        var oldCurrentScheme: T? = null
        if (oldScheme != null) {
          oldCurrentScheme = currentScheme
          @Suppress("UNCHECKED_CAST")
          removeScheme(oldScheme as T)
          processor.onSchemeDeleted(oldScheme)
        }

        val newScheme = readSchemeFromFile(event.file, false)
        if (newScheme != null) {
          processor.initScheme(newScheme)
          processor.onSchemeAdded(newScheme)

          updateCurrentScheme(oldCurrentScheme, newScheme)
        }
      }

      private fun updateCurrentScheme(oldCurrentScheme: T?, newCurrentScheme: E? = null) {
        if (oldCurrentScheme != currentScheme && currentScheme == null) {
          @Suppress("UNCHECKED_CAST")
          setCurrent(newCurrentScheme as T? ?: schemes.firstOrNull())
        }
      }

      override fun fileCreated(event: VirtualFileEvent) {
        if (event.requestor != null) {
          return
        }

        if (event.file.isDirectory) {
          val dir = getDirectory()
          if (event.file == dir) {
            for (file in dir.children) {
              if (isMy(file)) {
                schemeCreatedExternally(file)
              }
            }
          }
        }
        else if (isMy(event)) {
          schemeCreatedExternally(event.file)
        }
      }

      private fun schemeCreatedExternally(file: VirtualFile) {
        val readScheme = readSchemeFromFile(file, false)
        if (readScheme != null) {
          processor.initScheme(readScheme)
          processor.onSchemeAdded(readScheme)
        }
      }

      override fun fileDeleted(event: VirtualFileEvent) {
        if (event.requestor != null) {
          return
        }

        var oldCurrentScheme = currentScheme
        if (event.file.isDirectory) {
          val dir = directory
          if (event.file == dir) {
            directory = null
            removeExternalizableSchemes()
          }
        }
        else if (isMy(event)) {
          val scheme = findExternalizableSchemeByFileName(event.file.name) ?: return
          @Suppress("UNCHECKED_CAST")
          removeScheme(scheme as T)
          processor.onSchemeDeleted(scheme)
        }

        updateCurrentScheme(oldCurrentScheme)
      }
    }, false, virtualFileTrackerDisposable!!)
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

      val element = JDOMUtil.load(URLUtil.openStream(url))
      val scheme = convertor.convert(element)
      if (scheme is ExternalizableScheme) {
        val fileName = PathUtilRt.getFileName(url.path)
        val extension = getFileExtension(fileName, true)
        val info = ExternalInfo(fileName.substring(0, fileName.length - extension.length), extension)
        info.hash = JDOMUtil.getTreeHash(element, true)
        info.schemeName = scheme.name
        @Suppress("UNCHECKED_CAST")
        val oldInfo = schemeToInfo.put(scheme as E, info)
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

  private fun isMy(event: VirtualFileEvent) = isMy(event.file)

  private fun isMy(file: VirtualFile) = StringUtilRt.endsWithIgnoreCase(file.nameSequence, schemeExtension)

  override fun loadSchemes(): Collection<E> {
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

    val list = SmartList<E>()
    for (i in newSchemesOffset..schemes.size - 1) {
      @Suppress("UNCHECKED_CAST")
      val scheme = schemes[i] as E
      processor.initScheme(scheme)
      list.add(scheme)

      @Suppress("UNCHECKED_CAST")
      processPendingCurrentSchemeName(scheme as T)
    }

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
      @Suppress("UNCHECKED_CAST")
      if (scheme is ExternalizableScheme && getState(scheme as E) != SchemeProcessor.State.NON_PERSISTENT) {
        if (scheme == currentScheme) {
          currentScheme = null
        }

        processor.onSchemeDeleted(scheme)
      }
    }
    retainExternalInfo(schemes)
  }

  private fun findExternalizableSchemeByFileName(fileName: String): E? {
    for (scheme in schemes) {
      @Suppress("UNCHECKED_CAST")
      if (scheme is ExternalizableScheme && fileName == "${scheme.fileName}$schemeExtension") {
        return scheme as E
      }
    }
    return null
  }

  private fun isOverwriteOnLoad(existingScheme: E): Boolean {
    if (readOnlyExternalizableSchemes[existingScheme.name] === existingScheme) {
      // so, bundled scheme is shadowed
      return true
    }

    val info = schemeToInfo[existingScheme]
    // scheme from file with old extension, so, we must ignore it
    return info != null && schemeExtension != info.fileExtension
  }

  private fun loadScheme(fileName: CharSequence, input: InputStream, duringLoad: Boolean): E? {
    try {
      val element = JDOMUtil.load(input)
      @Suppress("DEPRECATED_SYMBOL_WITH_MESSAGE", "UNCHECKED_CAST")
      val scheme = processor.readScheme(element, duringLoad) ?: return null

      val extension = getFileExtension(fileName, false)
      val fileNameWithoutExtension = fileName.subSequence(0, fileName.length - extension.length).toString()
      if (duringLoad) {
        if (filesToDelete.isNotEmpty() && filesToDelete.contains(fileName.toString())) {
          LOG.warn("Scheme file \"$fileName\" is not loaded because marked to delete")
          return null
        }

        val existingScheme = findSchemeByName(scheme.name)
        if (existingScheme != null) {
          @Suppress("UNCHECKED_CAST")
          if (existingScheme is ExternalizableScheme && isOverwriteOnLoad(existingScheme as E)) {
            removeScheme(existingScheme)
          }
          else {
            if (schemeExtension != extension && schemeToInfo[existingScheme as Scheme]?.fileNameWithoutExtension == fileNameWithoutExtension) {
              // 1.oldExt is loading after 1.newExt - we should delete 1.oldExt
              filesToDelete.add(fileName.toString())
            }
            else {
              // We don't load scheme with duplicated name - if we generate unique name for it, it will be saved then with new name.
              // It is not what all can expect. Such situation in most cases indicates error on previous level, so, we just warn about it.
              LOG.warn("Scheme file \"$fileName\" is not loaded because defines duplicated name \"${scheme.name}\"")
            }
            return null
          }
        }
      }

      var info: ExternalInfo? = schemeToInfo[scheme]
      if (info == null) {
        info = ExternalInfo(fileNameWithoutExtension, extension)
        schemeToInfo.put(scheme, info)
      }
      else {
        info.setFileNameWithoutExtension(fileNameWithoutExtension, extension)
      }
      info.hash = JDOMUtil.getTreeHash(element, true)
      info.schemeName = scheme.name

      @Suppress("UNCHECKED_CAST")
      if (duringLoad) {
        schemes.add(scheme as T)
      }
      else {
        addScheme(scheme as T)
      }
      return scheme
    }
    catch (e: Throwable) {
      LOG.error("Cannot read scheme $fileName", e)
      return null
    }
  }

  private val ExternalizableScheme.fileName: String?
    get() = schemeToInfo[this]?.fileNameWithoutExtension

  private fun canRead(name: CharSequence) = updateExtension && StringUtilRt.endsWithIgnoreCase(name, DEFAULT_EXT) || StringUtilRt.endsWithIgnoreCase(name, schemeExtension)

  private fun readSchemeFromFile(file: VirtualFile, duringLoad: Boolean): E? {
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
    val schemesToSave = SmartList<E>()
    for (scheme in schemes) {
      @Suppress("UNCHECKED_CAST")
      if (scheme is ExternalizableScheme) {
        val state = getState(scheme as E)
        if (state == SchemeProcessor.State.NON_PERSISTENT) {
          continue
        }

        hasSchemes = true

        if (state != SchemeProcessor.State.UNCHANGED) {
          schemesToSave.add(scheme)
        }

        val fileName = scheme.fileName
        if (fileName != null && !isRenamed(scheme)) {
          nameGenerator.addExistingName(fileName)
        }
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
    directory = null

    var deleteUsingIo = !useVfs
    if (!deleteUsingIo) {
      val dir = getDirectory()
      if (dir != null) {
        runWriteAction {
        try {
          dir.delete(this)
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

  private fun getState(scheme: E) = processor.getState(scheme)

  private fun saveScheme(scheme: E, nameGenerator: UniqueNameGenerator) {
    var externalInfo: ExternalInfo? = schemeToInfo[scheme]
    val currentFileNameWithoutExtension = if (externalInfo == null) null else externalInfo.fileNameWithoutExtension
    val parent = processor.writeScheme(scheme)
    val element = if (parent == null || parent is Element) parent as Element? else (parent as Document).detachRootElement()
    if (JDOMUtil.isEmpty(element)) {
      externalInfo?.scheduleDelete()
      return
    }

    var fileNameWithoutExtension = currentFileNameWithoutExtension
    if (fileNameWithoutExtension == null || isRenamed(scheme)) {
      fileNameWithoutExtension = nameGenerator.generateUniqueName(FileUtil.sanitizeFileName(scheme.name, false))
    }

    val newHash = JDOMUtil.getTreeHash(element!!, true)
    if (externalInfo != null && currentFileNameWithoutExtension === fileNameWithoutExtension && newHash == externalInfo.hash) {
      return
    }

    // save only if scheme differs from bundled
    val bundledScheme = readOnlyExternalizableSchemes[scheme.name]
    if (bundledScheme != null && schemeToInfo[bundledScheme]?.hash == newHash) {
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
        var dir = getDirectory()
        if (dir == null || !dir.isValid) {
          dir = createDir(ioDirectory, this)
          directory = dir
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
    externalInfo.hash = newHash
    externalInfo.schemeName = scheme.name
  }

  private fun ExternalInfo.scheduleDelete() {
    filesToDelete.add(fileName)
  }

  private fun isRenamed(scheme: ExternalizableScheme): Boolean {
    val info = schemeToInfo[scheme]
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
      val dir = getDirectory()
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
          if (token != null) {
            token.finish()
          }
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

  private fun getDirectory(): VirtualFile? {
    var result = directory
    if (result == null) {
      result = LocalFileSystem.getInstance().findFileByPath(ioDirectory.systemIndependentPath)
      directory = result
    }
    return result
  }

  override fun getRootDirectory() = ioDirectory.toFile()

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

    schemeToInfo.retainEntries(TObjectObjectProcedure<E, ExternalInfo> { scheme, info ->
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
        if (replaceExisting && existing is ExternalizableScheme) {
          val oldInfo = schemeToInfo.remove(existing as E)
          if (oldInfo != null && scheme is ExternalizableScheme && !schemeToInfo.containsKey(scheme as E)) {
            @Suppress("UNCHECKED_CAST")
            schemeToInfo.put(scheme as E, oldInfo)
          }
        }
        break
      }
    }
    if (toReplace == -1) {
      schemes.add(scheme)
    }
    else if (replaceExisting || scheme !is ExternalizableScheme) {
      schemes[toReplace] = scheme
    }
    else {
      scheme.renameScheme(UniqueNameGenerator.generateUniqueName(scheme.name, collectExistingNames(schemes)))
      schemes.add(scheme)
    }

    if (scheme is ExternalizableScheme && filesToDelete.isNotEmpty()) {
      val info = schemeToInfo[scheme as E]
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

  override fun getAllSchemes() = Collections.unmodifiableList(schemes)

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

        if (s is ExternalizableScheme) {
          schemeToInfo.remove(s as E)?.scheduleDelete()
        }
        schemes.removeAt(i)
        break
      }
    }
  }

  override fun getAllSchemeNames() = if (schemes.isEmpty()) emptyList() else schemes.map { it.name }

  override fun isMetadataEditable(scheme: E) = !readOnlyExternalizableSchemes.containsKey(scheme.name)

  private class ExternalInfo(var fileNameWithoutExtension: String, var fileExtension: String?) {
    // we keep it to detect rename
    var schemeName: String? = null
    var hash = 0

    val fileName: String
      get() = "$fileNameWithoutExtension$fileExtension"

    fun setFileNameWithoutExtension(nameWithoutExtension: String, extension: String) {
      fileNameWithoutExtension = nameWithoutExtension
      fileExtension = extension
    }

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
  val file = parent.findChild(fileName)
  if (file != null) {
    return file
  }
  return runWriteAction { parent.createChildData(requestor, fileName) }
}