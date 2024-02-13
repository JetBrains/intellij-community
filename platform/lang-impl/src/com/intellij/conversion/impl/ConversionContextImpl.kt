// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.conversion.impl

import com.intellij.application.options.PathMacrosImpl.Companion.getInstanceEx
import com.intellij.application.options.ReplacePathToMacroMap
import com.intellij.conversion.*
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.highlighter.WorkspaceFileType
import com.intellij.ide.impl.convert.JDomConvertingUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.Strings
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.URLUtil
import it.unimi.dsi.fastutil.objects.Object2LongMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.concurrent.*
import java.util.function.BiFunction
import kotlin.concurrent.Volatile

private val LOG = logger<ConversionContextImpl>()

class ConversionContextImpl(projectPath: Path) : ConversionContext {
  private val fileToSettings = HashMap<Path, SettingsXmlFile>()
  private var storageScheme: StorageScheme? = null
  private var projectBaseDir: Path? = null
  private val projectFile = SettingsXmlFile(projectPath)
  private var workspaceFile: SettingsXmlFile? = null

  @Volatile
  private var myModuleFiles: List<Path>? = null
  val nonExistingModuleFiles: List<Path> = ArrayList()
  private val fileToModuleSettings = HashMap<Path, ModuleSettingsImpl>()
  private val nameToModuleSettings = HashMap<String, ModuleSettingsImpl>()
  private var runManagerSettings: RunManagerSettingsImpl? = null
  private var settingsBaseDir: Path? = null
  private var compilerManagerSettings: ComponentManagerSettings? = null
  private var projectRootManagerSettings: ComponentManagerSettings? = null
  private var moduleSettings: SettingsXmlFile? = null
  private var projectLibrariesSettings: MultiFilesSettings? = null
  private var artifactsSettings: MultiFilesSettings? = null

  private val conversionResult: NotNullLazyValue<CachedConversionResult>

  private var moduleListFile: Path? = null

  init {
    if (projectPath.toString().endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      storageScheme = StorageScheme.DEFAULT
      projectBaseDir = projectPath.parent
      moduleListFile = projectPath
      workspaceFile = SettingsXmlFile(projectPath.parent.resolve(
        Strings.trimEnd(projectPath.fileName.toString(), ProjectFileType.DOT_DEFAULT_EXTENSION) + WorkspaceFileType.DOT_DEFAULT_EXTENSION))
    }
    else {
      storageScheme = StorageScheme.DIRECTORY_BASED
      projectBaseDir = projectPath
      settingsBaseDir = projectPath.resolve(Project.DIRECTORY_STORE_FOLDER)
      moduleListFile = projectPath.resolve("modules.xml")
      workspaceFile = SettingsXmlFile(settingsBaseDir!!.resolve("workspace.xml"))
    }

    conversionResult = NotNullLazyValue.createValue {
      try {
        return@createValue CachedConversionResult.load(CachedConversionResult.getConversionInfoFile(projectFile.path), projectBaseDir!!)
      }
      catch (e: Exception) {
        LOG.error(e)
        return@createValue CachedConversionResult.createEmpty()
      }
    }
  }

  companion object {
    fun collapsePath(path: String, moduleSettings: ComponentManagerSettings): String {
      val map = createCollapseMacroMap(PathMacroUtil.MODULE_DIR_MACRO_NAME, moduleSettings.path.parent)
      return map.substitute(path, SystemInfoRt.isFileSystemCaseSensitive)
    }

    private fun createCollapseMacroMap(macroName: String, dir: Path): ReplacePathToMacroMap {
      val map = ReplacePathToMacroMap()
      map.addMacroReplacement(FileUtilRt.toSystemIndependentName(dir.toAbsolutePath().toString()), macroName)
      getInstanceEx().addMacroReplacements(map)
      return map
    }

    @Throws(CannotConvertException::class)
    private fun findGlobalLibraryElement(name: String): Element? {
      val file = PathManager.getOptionsFile("applicationLibraries")
      if (file.exists()) {
        val root = JDomConvertingUtil.load(file.toPath())
        val libraryTable = JDomSerializationUtil.findComponent(root, "libraryTable")
        if (libraryTable != null) {
          return findLibraryInTable(libraryTable, name)
        }
      }
      return null
    }

    private fun findLibraryInTable(tableElement: Element, name: String): Element? {
      val filter = JDomConvertingUtil.createElementWithAttributeFilter(JpsLibraryTableSerializer.LIBRARY_TAG,
                                                                       JpsLibraryTableSerializer.NAME_ATTRIBUTE, name)
      return JDomConvertingUtil.findChild(tableElement, filter)
    }
  }

  @JvmOverloads
  @Throws(CannotConvertException::class, IOException::class)
  fun saveConversionResult(allProjectFiles: Object2LongMap<String?> = this.allProjectFiles) {
    CachedConversionResult.saveConversionResult(allProjectFiles, CachedConversionResult.getConversionInfoFile(projectFile.path),
                                                projectBaseDir!!)
  }

  val projectFileTimestamps: Object2LongMap<String>
    get() = conversionResult.value.projectFilesTimestamps

  val appliedConverters: Set<String>
    get() = conversionResult.value.appliedConverters

  @get:Throws(CannotConvertException::class)
  val allProjectFiles: Object2LongMap<String?>
    get() {
      if (storageScheme == StorageScheme.DEFAULT) {
        val moduleFiles = modulePaths
        val totalResult: Object2LongMap<String?> = Object2LongOpenHashMap(moduleFiles.size + 2)
        addLastModifiedTme(projectFile.path, totalResult)
        addLastModifiedTme(workspaceFile!!.path, totalResult)
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

      val executor: Executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Conversion: Project Files Collecting", 3, false)
      val futures: MutableList<CompletableFuture<List<Object2LongMap<String>>>> = ArrayList(dirs.size + 1)
      futures.add(CompletableFuture.supplyAsync(
        { this.modulePaths }, executor)
                    .thenComposeAsync<List<Object2LongMap<String>>>(
                      { moduleFiles: List<Path> ->
                        val moduleCount = moduleFiles.size
                        if (moduleCount < 50) {
                          return@thenComposeAsync computeModuleFilesTimestamp(moduleFiles, executor)
                        }

                        val secondOffset = moduleCount / 2
                        computeModuleFilesTimestamp(moduleFiles.subList(0, secondOffset), executor)
                          .thenCombine(
                            computeModuleFilesTimestamp(moduleFiles.subList(secondOffset, moduleCount), executor),
                            BiFunction { list1, list2 ->
                              ContainerUtil.concat(list1, list2)
                            })
                      }, executor))

      for (subDirName in dirs) {
        futures.add(CompletableFuture.supplyAsync(
          {
            val result = CachedConversionResult.createPathToLastModifiedMap()
            addXmlFilesFromDirectory(subDirName, result)
            listOf(result)
          }, executor))
      }

      val totalResult = CachedConversionResult.createPathToLastModifiedMap()
      try {
        for (future in futures) {
          for (result in future.get()) {
            totalResult.putAll(result)
          }
        }
      }
      catch (e: ExecutionException) {
        throw CannotConvertException(e)
      }
      catch (e: InterruptedException) {
        throw CannotConvertException(e)
      }
      return totalResult
    }

  override fun getProjectBaseDir(): Path {
    return projectBaseDir!!
  }

  @Throws(CannotConvertException::class)
  override fun getModulePaths(): List<Path> {
    var result = myModuleFiles
    if (result == null) {
      result = try {
        findModuleFiles(JDOMUtil.load(moduleListFile!!))
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
      myModuleFiles = result
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
    val files: MutableList<Path> = ArrayList()
    for (module in modules.getChildren(JpsProjectLoader.MODULE_TAG)) {
      var filePath = module.getAttributeValue(JpsProjectLoader.FILE_PATH_ATTRIBUTE)
      if (filePath != null) {
        filePath = macros.substitute(filePath, true)
        files.add(Paths.get(filePath))
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
    val map = createCollapseMacroMap(PathMacroUtil.PROJECT_DIR_MACRO_NAME, projectBaseDir!!)
    return map.substitute(path, SystemInfoRt.isFileSystemCaseSensitive)
  }

  override fun getLibraryClassRoots(name: String, level: String): Collection<Path> {
    try {
      var libraryElement: Element? = null
      if (LibraryTablesRegistrar.PROJECT_LEVEL == level) {
        libraryElement = findProjectLibraryElement(name)
      }
      else if (LibraryTablesRegistrar.APPLICATION_LEVEL == level) {
        libraryElement = findGlobalLibraryElement(name)
      }
      return if (libraryElement == null) emptyList() else getClassRootPaths(libraryElement, null)
    }
    catch (e: CannotConvertException) {
      return emptyList()
    }
  }

  fun getClassRoots(libraryElement: Element, moduleSettings: ModuleSettings?): List<File> {
    return getClassRootUrls(libraryElement, moduleSettings)
      .map { File(Strings.trimEnd(URLUtil.extractPath(it), URLUtil.JAR_SEPARATOR)) }
      .toList()
  }

  fun getClassRootPaths(libraryElement: Element, moduleSettings: ModuleSettings?): List<Path> {
    return getClassRootUrls(libraryElement, moduleSettings)
      .map { Path.of(Strings.trimEnd(URLUtil.extractPath(it), URLUtil.JAR_SEPARATOR)) }
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

  override fun getModulesSettings(): ComponentManagerSettings {
    if (moduleSettings == null) {
      moduleSettings = createProjectSettings("modules.xml")
    }
    return moduleSettings!!
  }

  override fun createProjectSettings(fileName: String): SettingsXmlFile {
    return if (storageScheme == StorageScheme.DEFAULT) {
      projectFile
    }
    else {
      SettingsXmlFile(settingsBaseDir!!.resolve(fileName))
    }
  }

  @Throws(CannotConvertException::class)
  private fun findProjectLibraryElement(name: String): Element? {
    val libraries = projectLibrariesSettings.projectLibraries
    val filter = JDomConvertingUtil.createElementWithAttributeFilter(JpsLibraryTableSerializer.LIBRARY_TAG,
                                                                     JpsLibraryTableSerializer.NAME_ATTRIBUTE, name)
    return libraries.find(filter::test)
  }

  private fun createExpandMacroMap(): ExpandMacroToPathMap {
    val macros = ExpandMacroToPathMap()
    val projectDir = FileUtilRt.toSystemIndependentName(projectBaseDir!!.toAbsolutePath().toString())
    macros.addMacroExpand(PathMacroUtil.PROJECT_DIR_MACRO_NAME, projectDir)
    getInstanceEx().addMacroExpands(macros)
    return macros
  }

  override fun getSettingsBaseDir(): Path? {
    return settingsBaseDir
  }

  override fun getProjectFile(): Path {
    return projectFile.path
  }

  override fun getProjectSettings(): ComponentManagerSettings {
    return projectFile
  }

  @Throws(CannotConvertException::class)
  override fun getRunManagerSettings(): RunManagerSettingsImpl {
    if (runManagerSettings == null) {
      runManagerSettings = if (storageScheme == StorageScheme.DEFAULT) {
        RunManagerSettingsImpl(workspaceFile!!, projectFile, null, this)
      }
      else {
        RunManagerSettingsImpl(workspaceFile!!, null, settingsBaseDir!!.resolve("runConfigurations"), this)
      }
    }
    return runManagerSettings!!
  }

  override fun getWorkspaceSettings(): WorkspaceSettings {
    return workspaceFile!!
  }

  @Throws(CannotConvertException::class)
  override fun getModuleSettings(moduleFile: Path): ModuleSettings {
    var settings = fileToModuleSettings[moduleFile]
    if (settings == null) {
      settings = ModuleSettingsImpl(moduleFile, this)
      fileToModuleSettings[moduleFile] = settings
      nameToModuleSettings[settings.getModuleName()] = settings
    }
    return settings
  }

  override fun getModuleSettings(moduleName: String): ModuleSettings? {
    if (!nameToModuleSettings.containsKey(moduleName)) {
      for (moduleFile in myModuleFiles!!) {
        try {
          getModuleSettings(moduleFile)
        }
        catch (ignored: CannotConvertException) {
        }
      }
    }
    return nameToModuleSettings[moduleName]
  }

  override fun getStorageScheme(): StorageScheme {
    return storageScheme!!
  }

  @Throws(IOException::class)
  fun saveFiles(files: Collection<Path>) {
    for (file in files) {
      var xmlFile = fileToSettings[file]
      if (xmlFile == null) {
        xmlFile = fileToModuleSettings[file]
      }
      xmlFile?.save()
    }
    if (files.contains(workspaceFile!!.path)) {
      workspaceFile.save()
    }
    if (files.contains(projectFile.path)) {
      projectFile.save()
    }
  }

  @Throws(CannotConvertException::class)
  fun getOrCreateFile(file: Path): SettingsXmlFile {
    return fileToSettings.computeIfAbsent(file) { file: Path? ->
      SettingsXmlFile(
        file!!)
    }
  }

  @Throws(CannotConvertException::class)
  override fun getProjectLibrariesSettings(): MultiFilesSettings {
    if (projectLibrariesSettings == null) {
      projectLibrariesSettings = if (storageScheme == StorageScheme.DEFAULT
      ) MultiFilesSettings(projectFile, null, this)
      else MultiFilesSettings(null, settingsBaseDir!!.resolve("libraries"), this)
    }
    return projectLibrariesSettings!!
  }

  @Throws(CannotConvertException::class)
  override fun getArtifactsSettings(): MultiFilesSettings {
    if (artifactsSettings == null) {
      artifactsSettings = if (storageScheme == StorageScheme.DEFAULT
      ) MultiFilesSettings(projectFile, null, this)
      else MultiFilesSettings(null, settingsBaseDir!!.resolve("artifacts"), this)
    }
    return artifactsSettings!!
  }
}

private fun computeModuleFilesTimestamp(moduleFiles: List<Path>, executor: Executor): CompletableFuture<List<Object2LongMap<String>>> {
  return CompletableFuture.supplyAsync(
    {
      val result = Object2LongOpenHashMap<String>(moduleFiles.size)
      result.defaultReturnValue(-1)
      addLastModifiedTime(moduleFiles, result)
      listOf(result)
    }, executor)
}

private fun addLastModifiedTime(moduleFiles: List<Path>, result: Object2LongMap<String?>) {
  for (file in moduleFiles) {
    addLastModifiedTme(file, result)
  }
}

private fun addLastModifiedTme(file: Path, files: Object2LongMap<String?>) {
  try {
    files.put(file.toString(), Files.getLastModifiedTime(file).to(TimeUnit.SECONDS))
  }
  catch (ignore: IOException) {
  }
}

private fun addXmlFilesFromDirectory(dir: Path, result: Object2LongMap<String>) {
  try {
    Files.newDirectoryStream(dir).use { children ->
      for (child in children) {
        val childPath = child.toString()
        if (!childPath.endsWith(".xml") || child.fileName.toString().startsWith(".")) {
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

        result.put(childPath, attributes.lastModifiedTime().to(TimeUnit.SECONDS))
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