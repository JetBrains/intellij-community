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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ex.DecodeDefaultsUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.impl.stores.DirectoryBasedStorage
import com.intellij.openapi.components.impl.stores.DirectoryStorageData
import com.intellij.openapi.components.impl.stores.StorageUtil
import com.intellij.openapi.components.impl.stores.StreamProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.AbstractExtensionPointBean
import com.intellij.openapi.options.*
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.tracker.VirtualFileTracker
import com.intellij.util.PathUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import com.intellij.util.ThrowableConvertor
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.text.UniqueNameGenerator
import gnu.trove.THashMap
import gnu.trove.THashSet
import gnu.trove.TObjectObjectProcedure
import gnu.trove.TObjectProcedure
import org.jdom.Document
import org.jdom.Element
import java.io.File
import java.io.InputStream
import java.util.ArrayList
import java.util.Collections

public class SchemeManagerImpl<T : Scheme, E : ExternalizableScheme>(private val fileSpec: String,
                                                                     private val processor: SchemeProcessor<E>,
                                                                     private val roamingType: RoamingType,
                                                                     private val provider: StreamProvider?,
                                                                     private val ioDirectory: File) : SchemesManager<T, E>(), SafeWriteRequestor {
  private val schemes = ArrayList<T>()
  private val readOnlyExternalizableSchemes = THashMap<String, E>()

  private var currentScheme: T? = null

  private var directory: VirtualFile? = null

  private val schemeExtension: String
  private val updateExtension: Boolean

  private val filesToDelete = THashSet<String>()

  // scheme could be changed - so, hashcode will be changed - we must use identity hashing strategy
  private val schemeToInfo = THashMap<E, ExternalInfo>(ContainerUtil.identityStrategy())

  init {
    if (processor is SchemeExtensionProvider) {
      schemeExtension = processor.getSchemeExtension()
      updateExtension = processor.isUpgradeNeeded()
    }
    else {
      schemeExtension = DirectoryStorageData.DEFAULT_EXT
      updateExtension = false
    }

    if (provider == null || !provider.enabled) {
      service<VirtualFileTracker>()?.addTracker("${LocalFileSystem.PROTOCOL_PREFIX}${ioDirectory.getAbsolutePath().replace(File.separatorChar, '/')}", object : VirtualFileAdapter() {
        override fun contentsChanged(event: VirtualFileEvent) {
          if (event.getRequestor() != null || !isMy(event)) {
            return
          }

          val oldScheme = findExternalizableSchemeByFileName(event.getFile().getName())
          var oldCurrentScheme: T? = null
          if (oldScheme != null) {
            oldCurrentScheme = currentScheme
            @suppress("UNCHECKED_CAST")
            removeScheme(oldScheme as T)
            processor.onSchemeDeleted(oldScheme)
          }

          val newScheme = readSchemeFromFile(event.getFile(), false)
          if (newScheme != null) {
            processor.initScheme(newScheme)
            processor.onSchemeAdded(newScheme)

            updateCurrentScheme(oldCurrentScheme, newScheme)
          }
        }

        private fun updateCurrentScheme(oldCurrentScheme: T?, newCurrentScheme: E? = null) {
          if (oldCurrentScheme != currentScheme && currentScheme == null) {
            @suppress("UNCHECKED_CAST")
            setCurrent(newCurrentScheme as T? ?: schemes.firstOrNull())
          }
        }

        override fun fileCreated(event: VirtualFileEvent) {
          if (event.getRequestor() != null) {
            return
          }

          if (event.getFile().isDirectory()) {
            val dir = getDirectory()
            if (event.getFile() == dir) {
              for (file in dir!!.getChildren()) {
                if (isMy(file)) {
                  schemeCreatedExternally(file)
                }
              }
            }
          }
          else if (isMy(event)) {
            schemeCreatedExternally(event.getFile())
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
          if (event.getRequestor() != null) {
            return
          }

          var oldCurrentScheme = currentScheme
          if (event.getFile().isDirectory()) {
            val dir = directory
            if (event.getFile() == dir) {
              directory = null
              removeExternalizableSchemes()
            }
          }
          else if (isMy(event)) {
            val scheme = findExternalizableSchemeByFileName(event.getFile().getName()) ?: return
            @suppress("UNCHECKED_CAST")
            removeScheme(scheme as T)
            processor.onSchemeDeleted(scheme)
          }

          updateCurrentScheme(oldCurrentScheme)
        }
      }, false, ApplicationManager.getApplication())
    }
  }

  override fun loadBundledScheme(resourceName: String, requestor: Any, convertor: ThrowableConvertor<Element, T, Throwable>) {
    try {
      val url = if (requestor is AbstractExtensionPointBean)
        requestor.getLoaderForClass().getResource(resourceName)
      else
        DecodeDefaultsUtil.getDefaults(requestor, resourceName)
      if (url == null) {
        LOG.error("Cannot read scheme from $resourceName")
        return
      }

      val element = JDOMUtil.load(URLUtil.openStream(url))
      val scheme = convertor.convert(element)
      if (scheme is ExternalizableScheme) {
        val fileName = PathUtilRt.getFileName(url.getPath())
        val extension = getFileExtension(fileName, true)
        val info = ExternalInfo(fileName.substring(0, fileName.length() - extension.length()), extension)
        info.hash = JDOMUtil.getTreeHash(element, true)
        info.schemeName = scheme.getName()
        @suppress("UNCHECKED_CAST")
        val oldInfo = schemeToInfo.put(scheme as E, info)
        LOG.assertTrue(oldInfo == null)
        val oldScheme = readOnlyExternalizableSchemes.put(scheme.getName(), scheme)
        if (oldScheme != null) {
          LOG.warn("Duplicated scheme ${scheme.getName()} - old: $oldScheme, new $scheme")
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
    else if (StringUtilRt.endsWithIgnoreCase(fileName, DirectoryStorageData.DEFAULT_EXT)) {
      DirectoryStorageData.DEFAULT_EXT
    }
    else if (allowAny) {
      PathUtil.getFileExtension(fileName.toString())!!
    }
    else {
      throw IllegalStateException("Scheme file extension $fileName is unknown, must be filtered out")
    }
  }

  private fun isMy(event: VirtualFileEvent) = isMy(event.getFile())

  private fun isMy(file: VirtualFile) = StringUtilRt.endsWithIgnoreCase(file.getNameSequence(), schemeExtension)

  override fun loadSchemes(): Collection<E> {
    val newSchemesOffset = schemes.size()
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
      val dir = getDirectory()
      val files = dir?.getChildren()
      if (files != null) {
        for (file in files) {
          readSchemeFromFile(file, true)
        }
      }
    }

    val list = SmartList<E>()
    for (i in newSchemesOffset..schemes.size() - 1) {
      @suppress("UNCHECKED_CAST")
      val scheme = schemes[i] as E
      processor.initScheme(scheme)
      list.add(scheme)
    }
    return list
  }

  public fun reload() {
    // we must not remove non-persistent (e.g. predefined) schemes, because we cannot load it (obviously)
    removeExternalizableSchemes()

    loadSchemes()
  }

  private fun removeExternalizableSchemes() {
    // todo check is bundled/read-only schemes correctly handled
    for (i in schemes.indices.reversed()) {
      val scheme = schemes.get(i)
      @suppress("UNCHECKED_CAST")
      if (scheme is ExternalizableScheme && getState(scheme as E) != BaseSchemeProcessor.State.NON_PERSISTENT) {
        if (scheme == currentScheme) {
          currentScheme = null
        }

        processor.onSchemeDeleted(scheme as E)
      }
    }
    retainExternalInfo(schemes)
  }

  private fun findExternalizableSchemeByFileName(fileName: String): E? {
    for (scheme in schemes) {
      @suppress("UNCHECKED_CAST")
      if (scheme is ExternalizableScheme && fileName == "${getFileName(scheme)}$schemeExtension") {
        return scheme as E
      }
    }
    return null
  }

  private fun isOverwriteOnLoad(existingScheme: E): Boolean {
    if (readOnlyExternalizableSchemes.get(existingScheme.getName()) === existingScheme) {
      // so, bundled scheme is shadowed
      return true
    }

    val info = schemeToInfo.get(existingScheme)
    // scheme from file with old extension, so, we must ignore it
    return info != null && schemeExtension != info.fileExtension
  }

  private fun loadScheme(fileName: CharSequence, input: InputStream, duringLoad: Boolean): E? {
    try {
      val element = JDOMUtil.load(input)
      @suppress("DEPRECATED_SYMBOL_WITH_MESSAGE", "UNCHECKED_CAST")
      val scheme = (if (processor is BaseSchemeProcessor<*>) {
        processor.readScheme(element, duringLoad) as E?
      }
      else {
        processor.readScheme(Document(element.detach() as Element))
      }) ?: return null

      val extension = getFileExtension(fileName, false)
      val fileNameWithoutExtension = fileName.subSequence(0, fileName.length() - extension.length()).toString()
      if (duringLoad) {
        if (filesToDelete.isNotEmpty() && filesToDelete.contains(fileName.toString())) {
          LOG.warn("Scheme file $fileName is not loaded because marked to delete")
          return null
        }

        val existingScheme = findSchemeByName(scheme.getName())
        if (existingScheme != null) {
          @suppress("UNCHECKED_CAST")
          if (existingScheme is ExternalizableScheme && isOverwriteOnLoad(existingScheme as E)) {
            removeScheme(existingScheme)
          }
          else {
            // We don't load scheme with duplicated name - if we generate unique name for it, it will be saved then with new name.
            // It is not what all can expect. Such situation in most cases indicates error on previous level, so, we just warn about it.
            LOG.warn("Scheme file $fileName is not loaded because defines duplicated name ${scheme.getName()}")
            return null
          }
        }
      }

      var info: ExternalInfo? = schemeToInfo.get(scheme)
      if (info == null) {
        info = ExternalInfo(fileNameWithoutExtension, extension)
        schemeToInfo.put(scheme, info)
      }
      else {
        info.setFileNameWithoutExtension(fileNameWithoutExtension, extension)
      }
      info.hash = JDOMUtil.getTreeHash(element, true)
      info.schemeName = scheme.getName()

      @suppress("UNCHECKED_CAST")
      if (duringLoad) {
        schemes.add(scheme as T)
      }
      else {
        addScheme(scheme as T)
      }
      return scheme
    }
    catch (e: Exception) {
      LOG.error("Cannot read scheme $fileName", e)
      return null
    }
  }

  private fun getFileName(scheme: ExternalizableScheme) = schemeToInfo.get(scheme)?.fileNameWithoutExtension

  private fun canRead(name: CharSequence): Boolean {
    return updateExtension && StringUtilRt.endsWithIgnoreCase(name, DirectoryStorageData.DEFAULT_EXT) || StringUtilRt.endsWithIgnoreCase(name, schemeExtension)
  }

  private fun readSchemeFromFile(file: VirtualFile, duringLoad: Boolean): E? {
    val fileName = file.getNameSequence()
    if (file.isDirectory() || !canRead(fileName)) {
      return null
    }

    try {
      return loadScheme(fileName, file.getInputStream(), duringLoad)
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
      @suppress("UNCHECKED_CAST")
      if (scheme is ExternalizableScheme) {
        val state = getState(scheme as E)
        if (state === BaseSchemeProcessor.State.NON_PERSISTENT) {
          continue
        }

        hasSchemes = true

        if (state !== BaseSchemeProcessor.State.UNCHANGED) {
          schemesToSave.add(scheme)
        }

        val fileName = getFileName(scheme)
        if (fileName != null && !isRenamed(scheme)) {
          nameGenerator.addExistingName(fileName)
        }
      }
    }

    for (scheme in schemesToSave) {
      errors.catch {
        saveScheme(scheme, nameGenerator)
      }
    }

    val dir = getDirectory()
    deleteFiles(dir, errors)

    if (!hasSchemes && dir != null) {
      removeDirectoryIfEmpty(dir, errors)
    }
  }

  private fun removeDirectoryIfEmpty(dir: VirtualFile, errors: MutableList<Throwable>) {
    for (file in dir.getChildren()) {
      if (!file.`is`(VFileProperty.HIDDEN)) {
        LOG.info("Directory " + dir.getNameSequence() + " is not deleted: at least one file " + file.getNameSequence() + " exists")
        return
      }
    }

    LOG.info("Remove schemes directory " + dir.getNameSequence())
    directory = null

    val token = WriteAction.start()
    try {
      dir.delete(this)
    }
    catch (e: Throwable) {
      errors.add(e)
    }
    finally {
      token.finish()
    }
  }

  private fun getState(scheme: E): BaseSchemeProcessor.State {
    return if (processor is BaseSchemeProcessor<*>) {
      (processor as BaseSchemeProcessor<E>).getState(scheme)
    }
    else {
      @suppress("DEPRECATED_SYMBOL_WITH_MESSAGE")
      if (processor.shouldBeSaved(scheme)) BaseSchemeProcessor.State.POSSIBLY_CHANGED else BaseSchemeProcessor.State.NON_PERSISTENT
    }
  }

  private fun saveScheme(scheme: E, nameGenerator: UniqueNameGenerator) {
    var externalInfo: ExternalInfo? = schemeToInfo.get(scheme)
    val currentFileNameWithoutExtension = if (externalInfo == null) null else externalInfo.fileNameWithoutExtension
    val parent = processor.writeScheme(scheme)
    val element = if (parent == null || parent is Element) parent as Element? else (parent as Document).detachRootElement()
    if (JDOMUtil.isEmpty(element)) {
      externalInfo?.scheduleDelete()
      return
    }

    var fileNameWithoutExtension = currentFileNameWithoutExtension
    if (fileNameWithoutExtension == null || isRenamed(scheme)) {
      fileNameWithoutExtension = nameGenerator.generateUniqueName(FileUtil.sanitizeName(scheme.getName()))
    }

    val newHash = JDOMUtil.getTreeHash(element!!, true)
    if (externalInfo != null && currentFileNameWithoutExtension === fileNameWithoutExtension && newHash == externalInfo.hash) {
      return
    }

    // save only if scheme differs from bundled
    val bundledScheme = readOnlyExternalizableSchemes.get(scheme.getName())
    if (bundledScheme != null && schemeToInfo.get(bundledScheme)!!.hash == newHash) {
      externalInfo?.scheduleDelete()
      return
    }

    val fileName = fileNameWithoutExtension!! + schemeExtension
    // file will be overwritten, so, we don't need to delete it
    filesToDelete.remove(fileName)

    // stream provider always use LF separator
    val byteOut = StorageUtil.writeToBytes(element, "\n")

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
      var file: VirtualFile? = null
      var dir = getDirectory()
      if (dir == null || !dir.isValid()) {
        dir = DirectoryBasedStorage.createDir(ioDirectory, this)
        directory = dir!!
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
        file = DirectoryBasedStorage.getFile(fileName, dir, this)
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
      provider!!.saveContent(providerPath, byteOut.getInternalBuffer(), byteOut.size(), roamingType)
    }

    if (externalInfo == null) {
      externalInfo = ExternalInfo(fileNameWithoutExtension, schemeExtension)
      schemeToInfo.put(scheme, externalInfo)
    }
    else {
      externalInfo.setFileNameWithoutExtension(fileNameWithoutExtension, schemeExtension)
    }
    externalInfo.hash = newHash
    externalInfo.schemeName = scheme.getName()
  }


  private fun ExternalInfo.scheduleDelete() {
    filesToDelete.add(fileName)
  }

  private fun isRenamed(scheme: ExternalizableScheme): Boolean {
    val info = schemeToInfo.get(scheme)
    return info != null && scheme.getName() != info.schemeName
  }

  private fun deleteFiles(dir: VirtualFile?, errors: MutableList<Throwable>) {
    if (filesToDelete.isEmpty()) {
      return
    }

    if (provider != null && provider.enabled) {
      for (name in filesToDelete) {
        errors.catch {
          StorageUtil.delete(provider, fileSpec + '/' + name, roamingType)
        }
      }
    }
    else if (dir != null) {
      var token: AccessToken? = null
      try {
        for (file in dir.getChildren()) {
          if (filesToDelete.contains(file.getName())) {
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

    filesToDelete.clear()
  }

  private fun getDirectory(): VirtualFile? {
    var result = directory
    if (result == null) {
      result = LocalFileSystem.getInstance().findFileByIoFile(ioDirectory)
      directory = result
    }
    return result
  }

  override fun getRootDirectory() = ioDirectory

  override fun setSchemes(newSchemes: List<T>, newCurrentScheme: T?, removeCondition: Condition<T>?) {
    val oldCurrentScheme = currentScheme
    if (removeCondition == null) {
      schemes.clear()
    }
    else {
      for (i in schemes.indices.reversed()) {
        if (removeCondition.value(schemes.get(i))) {
          schemes.remove(i)
        }
      }
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
    if (schemeToInfo.isEmpty()) {
      return
    }

    schemeToInfo.retainEntries(object : TObjectObjectProcedure<E, ExternalInfo> {
      override fun execute(scheme: E, info: ExternalInfo): Boolean {
        if (readOnlyExternalizableSchemes.get(scheme.getName()) == scheme) {
          return true
        }

        for (t in newSchemes) {
          // by identity
          if (t === scheme) {
            if (filesToDelete.isNotEmpty()) {
              filesToDelete.remove("${info.fileName}")
            }
            return true
          }
        }

        info.scheduleDelete()
        return false
      }
    })
  }

  override fun addNewScheme(scheme: T, replaceExisting: Boolean) {
    var toReplace = -1
    for (i in schemes.indices) {
      val existing = schemes.get(i)
      if (existing.getName() == scheme.getName()) {
        if (!Comparing.equal<Class<out Scheme>>(existing.javaClass, scheme.javaClass)) {
          LOG.warn("'${scheme.getName()}' ${existing.javaClass.getSimpleName()} replaced with ${scheme.javaClass.getSimpleName()}")
        }

        toReplace = i
        if (replaceExisting && existing is ExternalizableScheme) {
          val oldInfo = schemeToInfo.remove(existing)
          if (oldInfo != null && scheme is ExternalizableScheme && !schemeToInfo.containsKey(scheme)) {
            @suppress("UNCHECKED_CAST")
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
      schemes.set(toReplace, scheme)
    }
    else {
      scheme.renameScheme(UniqueNameGenerator.generateUniqueName(scheme.getName(), collectExistingNames(schemes)))
      schemes.add(scheme)
    }

    if (scheme is ExternalizableScheme && filesToDelete.isNotEmpty()) {
      val info = schemeToInfo.get(scheme)
      if (info != null) {
        filesToDelete.remove("${info.fileName}")
      }
    }
  }

  private fun collectExistingNames(schemes: Collection<T>): Collection<String> {
    val result = THashSet<String>(schemes.size())
    for (scheme in schemes) {
      result.add(scheme.getName())
    }
    return result
  }

  override fun clearAllSchemes() {
    schemeToInfo.forEachValue(object : TObjectProcedure<ExternalInfo> {
      override fun execute(info: ExternalInfo): Boolean {
        info.scheduleDelete()
        return true
      }
    })

    currentScheme = null
    schemes.clear()
    schemeToInfo.clear()
  }

  override fun getAllSchemes() = Collections.unmodifiableList(schemes)

  override fun findSchemeByName(schemeName: String): T? {
    for (scheme in schemes) {
      if (scheme.getName() == schemeName) {
        return scheme
      }
    }
    return null
  }

  override fun setCurrent(scheme: T?, notify: Boolean) {
    val oldCurrent = currentScheme
    currentScheme = scheme
    if (notify && oldCurrent != scheme) {
      processor.onCurrentSchemeChanged(oldCurrent)
    }
  }

  override fun getCurrentScheme() = currentScheme

  override fun removeScheme(scheme: T) {
    for (i in schemes.size() - 1 downTo 0) {
      val s = schemes.get(i)
      if (scheme.getName() == s.getName()) {
        if (currentScheme == s) {
          currentScheme = null
        }

        if (s is ExternalizableScheme) {
          schemeToInfo.remove(s)?.scheduleDelete()
        }
        schemes.remove(i)
        break
      }
    }
  }

  override fun getAllSchemeNames(): Collection<String> {
    if (schemes.isEmpty()) {
      return emptyList()
    }

    val names = ArrayList<String>(schemes.size())
    for (scheme in schemes) {
      names.add(scheme.getName())
    }
    return names
  }

  override fun isMetadataEditable(scheme: E) = !readOnlyExternalizableSchemes.containsKey(scheme.name)

  private class ExternalInfo(var fileNameWithoutExtension: String, var fileExtension: String?) {
    // we keep it to detect rename
    var schemeName: String? = null
    var hash: Int = 0

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
  if (newName != getName()) {
    setName(newName)
    LOG.assertTrue(newName == getName())
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

inline val Scheme.name: String
  get() = getName()