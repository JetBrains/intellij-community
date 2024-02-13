// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.conversion.impl

import com.intellij.application.options.PathMacrosImpl
import com.intellij.application.options.ReplacePathToMacroMap
import com.intellij.conversion.*
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.highlighter.WorkspaceFileType
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.io.URLUtil
import com.intellij.util.xml.dom.readXmlAsModel
import it.unimi.dsi.fastutil.objects.Object2LongMap
import it.unimi.dsi.fastutil.objects.Object2LongMaps
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.concurrent.*
import kotlin.concurrent.Volatile

private val LOG = logger<ConversionContextImpl>()

class ConversionContextImpl(projectPath: Path) : ConversionContext {
  private val fileToSettings = HashMap<Path, SettingsXmlFile>()
  private var storageScheme: StorageScheme
  private val projectBaseDir: Path
  private val projectFile = SettingsXmlFile(projectPath)
  private val workspaceFile: SettingsXmlFile

  @Volatile
  private var moduleFiles: List<Path>? = null
  val nonExistingModuleFiles: List<Path> = ArrayList()
  private val fileToModuleSettings = HashMap<Path, ModuleSettingsImpl>()
  private val nameToModuleSettings = HashMap<String, ModuleSettingsImpl>()
  private var runManagerSettings: RunManagerSettingsImpl? = null
  private val settingsBaseDir: Path?
  private var compilerManagerSettings: ComponentManagerSettings? = null
  private var projectRootManagerSettings: ComponentManagerSettings? = null
  private var projectLibrariesSettings: MultiFilesSettings? = null
  private var artifactSettings: MultiFilesSettings? = null

  private var moduleListFile: Path

  init {
    if (projectPath.toString().endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      storageScheme = StorageScheme.DEFAULT
      projectBaseDir = projectPath.parent
      moduleListFile = projectPath
      workspaceFile = SettingsXmlFile(projectPath.parent.resolve(
        projectPath.fileName.toString().removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION) + WorkspaceFileType.DOT_DEFAULT_EXTENSION))
      settingsBaseDir = null
    }
    else {
      storageScheme = StorageScheme.DIRECTORY_BASED
      projectBaseDir = projectPath
      settingsBaseDir = projectPath.resolve(Project.DIRECTORY_STORE_FOLDER)
      moduleListFile = projectPath.resolve("modules.xml")
      workspaceFile = SettingsXmlFile(settingsBaseDir!!.resolve("workspace.xml"))
    }
  }

  companion object {
    @JvmStatic
    fun collapsePath(path: String, moduleSettings: ComponentManagerSettings): String {
      val map = createCollapseMacroMap(PathMacroUtil.MODULE_DIR_MACRO_NAME, moduleSettings.path.parent)
      return map.substitute(path, SystemInfoRt.isFileSystemCaseSensitive)
    }
  }

  fun loadConversionResult(): CachedConversionResult {
    try {
      return loadCachedConversionResult(CachedConversionResult.getConversionInfoFile(projectFile.path), projectBaseDir)
    }
    catch (e: Exception) {
      LOG.error(e)
      return CachedConversionResult.createEmpty()
    }
  }

  suspend fun saveConversionResult() {
    val projectFileMap = getAllProjectFiles()
    val root = Element("conversion")
    val appliedConverters = Element("applied-converters")
    root.addContent(appliedConverters)
    val extensionIterator = ConverterProvider.EP_NAME.filterableLazySequence().iterator()
    while (extensionIterator.hasNext()) {
      extensionIterator.next().id?.let {
        appliedConverters.addContent(Element("converter").setAttribute("id", it))
      }
    }

    val projectFiles = Element("project-files")
    root.addContent(projectFiles)

    val basePathWithSlash = projectBaseDir.toString() + File.separator
    val iterator = Object2LongMaps.fastIterator(projectFileMap)
    while (iterator.hasNext()) {
      val entry = iterator.next()
      val element = Element("f")
      val path = entry.key
      element.setAttribute("p", if (path.startsWith(basePathWithSlash)) {
        CachedConversionResult.RELATIVE_PREFIX + path.substring(basePathWithSlash.length)
      }
      else {
        path
      })
      element.setAttribute("t", entry.longValue.toString())
      projectFiles.addContent(element)
    }

    withContext(Dispatchers.IO) {
      JDOMUtil.write(root, CachedConversionResult.getConversionInfoFile(projectFile.path))
    }
  }

  suspend fun getAllProjectFiles(): Object2LongMap<String> {
    if (storageScheme == StorageScheme.DEFAULT) {
      val moduleFiles = modulePaths
      val totalResult = Object2LongOpenHashMap<String>(moduleFiles.size + 2)
      collectLastModifiedTme(projectFile.path, totalResult)
      collectLastModifiedTme(workspaceFile.path, totalResult)
      addLastModifiedTime(moduleFiles, totalResult)
      return totalResult
    }

    val dotIdeaDirectory = settingsBaseDir!!
    val dirs = listOf(
      dotIdeaDirectory,
      dotIdeaDirectory.resolve("libraries"),
      dotIdeaDirectory.resolve("artifacts"),
      dotIdeaDirectory.resolve("runConfigurations")
    )

    val tasks = withContext(CoroutineName("Conversion: Project Files Collecting") + Dispatchers.IO) {
      val tasks = ArrayList<Deferred<Object2LongMap<String>>>(dirs.size + 1)
      modulePaths.asSequence()
        .chunked(500)
        .mapTo(tasks) {
          async { computeModuleFilesTimestamp(it) }
        }

      dirs.mapTo(tasks) { subDirName ->
        async {
          val result = Object2LongOpenHashMap<String>()
          result.defaultReturnValue(-1)
          collectXmlFilesFromDirectory(subDirName, result)
          result
        }
      }
    }

    val totalResult = Object2LongOpenHashMap<String>()
    totalResult.defaultReturnValue(-1)
    try {
      @Suppress("OPT_IN_USAGE")
      for (future in tasks) {
        totalResult.putAll(future.getCompleted())
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      throw CannotConvertException(e)
    }
    return totalResult
  }

  override fun getProjectBaseDir(): Path = projectBaseDir

  @Throws(CannotConvertException::class)
  override fun getModulePaths(): List<Path> {
    var result = moduleFiles
    if (result == null) {
      result = try {
        findModuleFiles(JDOMUtil.load(moduleListFile))
      }
      catch (e: NoSuchFileException) {
        emptyList()
      }
      catch (e: JDOMException) {
        throw CannotConvertException(moduleListFile.toString() + ": " + e.message, e)
      }
      catch (e: IOException) {
        throw CannotConvertException(moduleListFile.toString() + ": " + e.message, e)
      }
      moduleFiles = result
    }
    return result!!
  }

  private fun findModuleFiles(root: Element): List<Path> {
    val moduleManager = JDomSerializationUtil.findComponent(root, JpsProjectLoader.MODULE_MANAGER_COMPONENT)
    val modules = moduleManager?.getChild(JpsProjectLoader.MODULES_TAG)
    if (modules == null) {
      return emptyList()
    }

    val macros = createExpandMacroMap()
    val files = ArrayList<Path>()
    for (module in modules.getChildren(JpsProjectLoader.MODULE_TAG)) {
      var filePath = module.getAttributeValue(JpsProjectLoader.FILE_PATH_ATTRIBUTE)
      if (filePath != null) {
        filePath = macros.substitute(filePath, true)
        files.add(Path.of(filePath))
      }
    }
    return files
  }

  fun expandPath(path: String, moduleSettings: ComponentManagerSettings): String {
    return createExpandMacroMap(moduleSettings).substitute(path, true)
  }

  private fun createExpandMacroMap(moduleSettings: ComponentManagerSettings?): ExpandMacroToPathMap {
    val map = createExpandMacroMap()
    if (moduleSettings != null) {
      val modulePath = FileUtilRt.toSystemIndependentName(moduleSettings.path.parent.toAbsolutePath().toString())
      map.addMacroExpand(PathMacroUtil.MODULE_DIR_MACRO_NAME, modulePath)
    }
    return map
  }

  override fun expandPath(path: String): String {
    val map = createExpandMacroMap(null)
    return map.substitute(path, SystemInfoRt.isFileSystemCaseSensitive)
  }

  override fun collapsePath(path: String): String {
    val map = createCollapseMacroMap(PathMacroUtil.PROJECT_DIR_MACRO_NAME, projectBaseDir)
    return map.substitute(path, SystemInfoRt.isFileSystemCaseSensitive)
  }

  fun getClassRoots(libraryElement: Element, moduleSettings: ModuleSettings?): List<File> {
    return getClassRootUrls(libraryElement, moduleSettings)
      .map { File(URLUtil.extractPath(it).removeSuffix(URLUtil.JAR_SEPARATOR)) }
      .toList()
  }

  fun getClassRootPaths(libraryElement: Element, moduleSettings: ModuleSettings?): List<Path> {
    return getClassRootUrls(libraryElement, moduleSettings)
      .map { Path.of(URLUtil.extractPath(it).removeSuffix(URLUtil.JAR_SEPARATOR)) }
      .toList()
  }

  fun getClassRootUrls(libraryElement: Element, moduleSettings: ModuleSettings?): Sequence<String> {
    //todo support jar directories
    val classesChild = libraryElement.getChild("CLASSES") ?: return emptySequence()
    val pathMap = createExpandMacroMap(moduleSettings)
    return classesChild.getChildren("root").asSequence().mapNotNull { root ->
      val url = root.getAttributeValue("url")
      if (url == null) null else pathMap.substitute(url, true)
    }
  }

  override fun getCompilerSettings(): ComponentManagerSettings? {
    if (compilerManagerSettings == null) {
      compilerManagerSettings = createProjectSettings("compiler.xml")
    }
    return compilerManagerSettings
  }

  override fun getProjectRootManagerSettings(): ComponentManagerSettings? {
    if (projectRootManagerSettings == null) {
      projectRootManagerSettings = createProjectSettings("misc.xml")
    }
    return projectRootManagerSettings
  }

  override fun createProjectSettings(fileName: String): ComponentManagerSettings {
    return if (storageScheme == StorageScheme.DEFAULT) {
      projectFile
    }
    else {
      SettingsXmlFile(settingsBaseDir!!.resolve(fileName))
    }
  }

  private fun createExpandMacroMap(): ExpandMacroToPathMap {
    val macros = ExpandMacroToPathMap()
    val projectDir = FileUtilRt.toSystemIndependentName(projectBaseDir.toAbsolutePath().toString())
    macros.addMacroExpand(PathMacroUtil.PROJECT_DIR_MACRO_NAME, projectDir)
    PathMacrosImpl.getInstanceEx().addMacroExpands(macros)
    return macros
  }

  override fun getSettingsBaseDir(): Path? = settingsBaseDir

  override fun getProjectFile(): Path = projectFile.path

  override fun getProjectSettings(): ComponentManagerSettings = projectFile

  @Throws(CannotConvertException::class)
  internal fun getRunManagerSettings(): RunManagerSettingsImpl {
    if (runManagerSettings == null) {
      runManagerSettings = if (storageScheme == StorageScheme.DEFAULT) {
        RunManagerSettingsImpl(workspaceFile, projectFile, null, this)
      }
      else {
        RunManagerSettingsImpl(workspaceFile, null, settingsBaseDir!!.resolve("runConfigurations"), this)
      }
    }
    return runManagerSettings!!
  }

  override fun getWorkspaceSettings(): WorkspaceSettings = workspaceFile

  @Throws(CannotConvertException::class)
  override fun getModuleSettings(moduleFile: Path): ModuleSettings {
    var settings = fileToModuleSettings.get(moduleFile)
    if (settings == null) {
      settings = ModuleSettingsImpl(moduleFile, this)
      fileToModuleSettings.put(moduleFile, settings)
      nameToModuleSettings.put(settings.moduleName, settings)
    }
    return settings
  }

  override fun getModuleSettings(moduleName: String): ModuleSettings? {
    if (!nameToModuleSettings.containsKey(moduleName)) {
      for (moduleFile in moduleFiles!!) {
        try {
          getModuleSettings(moduleFile)
        }
        catch (ignored: CannotConvertException) {
        }
      }
    }
    return nameToModuleSettings.get(moduleName)
  }

  override fun getStorageScheme(): StorageScheme = storageScheme

  fun saveFiles(files: Collection<Path>) {
    for (file in files) {
      var xmlFile = fileToSettings.get(file)
      if (xmlFile == null) {
        xmlFile = fileToModuleSettings.get(file)
      }
      xmlFile?.save()
    }
    if (files.contains(workspaceFile.path)) {
      workspaceFile.save()
    }
    if (files.contains(projectFile.path)) {
      projectFile.save()
    }
  }

  @Throws(CannotConvertException::class)
  internal fun getOrCreateFile(file: Path): SettingsXmlFile {
    return fileToSettings.computeIfAbsent(file) { SettingsXmlFile(it) }
  }

  override fun getProjectLibrarySettings(): ProjectLibrariesSettings = doGetProjectLibrarySettings()

  @Throws(CannotConvertException::class)
  internal fun doGetProjectLibrarySettings(): MultiFilesSettings {
    if (projectLibrariesSettings == null) {
      projectLibrariesSettings = if (storageScheme == StorageScheme.DEFAULT) {
        MultiFilesSettings(projectFile, null, this)
      }
      else {
        MultiFilesSettings(null, settingsBaseDir!!.resolve("libraries"), this)
      }
    }
    return projectLibrariesSettings!!
  }

  @Throws(CannotConvertException::class)
  internal fun getArtifactSettings(): MultiFilesSettings {
    if (artifactSettings == null) {
      artifactSettings = if (storageScheme == StorageScheme.DEFAULT) {
        MultiFilesSettings(projectFile, null, this)
      }
      else {
        MultiFilesSettings(null, settingsBaseDir!!.resolve("artifacts"), this)
      }
    }
    return artifactSettings!!
  }
}

private fun computeModuleFilesTimestamp(moduleFiles: List<Path>): Object2LongMap<String> {
  val result = Object2LongOpenHashMap<String>(moduleFiles.size)
  result.defaultReturnValue(-1)
  addLastModifiedTime(moduleFiles, result)
  return result
}

private fun addLastModifiedTime(moduleFiles: List<Path>, result: Object2LongOpenHashMap<String>) {
  for (file in moduleFiles) {
    collectLastModifiedTme(file = file, files = result)
  }
}

private fun collectLastModifiedTme(file: Path, files: Object2LongOpenHashMap<String>) {
  try {
    files.put(file.toString(), Files.getLastModifiedTime(file).to(TimeUnit.SECONDS))
  }
  catch (ignore: IOException) {
  }
}

private fun collectXmlFilesFromDirectory(dir: Path, result: Object2LongOpenHashMap<String>) {
  try {
    Files.newDirectoryStream(dir).use { children ->
      for (child in children) {
        val fileName = child.fileName.toString()
        if (!fileName.endsWith(".xml") || fileName.startsWith(".")) {
          continue
        }

        var attributes: BasicFileAttributes
        try {
          attributes = Files.readAttributes(child, BasicFileAttributes::class.java)
          if (attributes.isDirectory) {
            continue
          }
        }
        catch (ignore: IOException) {
          continue
        }

        result.put(child.toString(), attributes.lastModifiedTime().to(TimeUnit.SECONDS))
      }
    }
  }
  catch (ignore: NotDirectoryException) {
  }
  catch (ignore: NoSuchFileException) {
  }
  catch (e: IOException) {
    LOG.warn(e)
  }
}

private fun createCollapseMacroMap(macroName: String, dir: Path): ReplacePathToMacroMap {
  val map = ReplacePathToMacroMap()
  map.addMacroReplacement(FileUtilRt.toSystemIndependentName(dir.toAbsolutePath().toString()), macroName)
  PathMacrosImpl.getInstanceEx().addMacroReplacements(map)
  return map
}

private fun loadCachedConversionResult(infoFile: Path, baseDir: Path): CachedConversionResult {
  val root = try {
    readXmlAsModel(infoFile)
  }
  catch (ignore: NoSuchFileException) {
    return CachedConversionResult.createEmpty()
  }

  val projectFilesTimestamps = Object2LongOpenHashMap<String>()
  projectFilesTimestamps.defaultReturnValue(-1)
  val appliedConverters = HashSet<String>()
  val basePathWithSlash = baseDir.toString() + File.separator
  for (child in root.children) {
    if (child.name == "applied-converters") {
      for (element in child.children) {
        val id = element.getAttributeValue("id")
        if (id != null) {
          appliedConverters.add(id)
        }
      }
    }
    else if (child.name == "project-files") {
      val projectFiles = child.children
      for (element in projectFiles) {
        var path = element.getAttributeValue("p")
        if (path == null) {
          path = element.getAttributeValue("path")
        }
        else if (path.startsWith(CachedConversionResult.RELATIVE_PREFIX)) {
          path = basePathWithSlash + path.substring(CachedConversionResult.RELATIVE_PREFIX.length)
        }

        if (path.isNullOrEmpty()) {
          continue
        }

        try {
          var timestamp = element.getAttributeValue("t")
          if (timestamp == null) {
            timestamp = element.getAttributeValue("timestamp")
            if (timestamp != null) {
              projectFilesTimestamps.put(path, TimeUnit.MILLISECONDS.toSeconds(timestamp.toLong()))
            }
          }
          else {
            projectFilesTimestamps.put(path, timestamp.toLong())
          }
        }
        catch (ignore: NumberFormatException) {
        }
      }
    }
  }
  return CachedConversionResult(appliedConverters, projectFilesTimestamps)
}