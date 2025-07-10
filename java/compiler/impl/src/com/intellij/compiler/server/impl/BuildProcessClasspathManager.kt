// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server.impl

import com.google.common.collect.HashBiMap
import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.compiler.server.CompileServerPlugin
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.platform.diagnostic.telemetry.TracerLevel
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectSerializers
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.PathUtilRt
import com.intellij.util.io.URLUtil
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.cmdline.ClasspathBootstrap
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarFile

class BuildProcessClasspathManager(parentDisposable: Disposable) {
  @Volatile
  private var compileServerPluginsClasspath: List<String>? = null

  private val lastClasspathLock = Any()
  private var lastRawClasspath: List<String>? = null
  private var lastFilteredClasspath: List<String>? = null

  init {
    CompileServerPlugin.EP_NAME.addChangeListener({ compileServerPluginsClasspath = null }, parentDisposable)
  }

  fun getBuildProcessClasspath(project: Project): List<String> {
    val appClassPath = ClasspathBootstrap.getBuildProcessApplicationClasspath() +
                       getAdditionalApplicationClasspath()
    val pluginClassPath = getBuildProcessPluginsClasspath(project)
    val rawClasspath = appClassPath + pluginClassPath
    synchronized(lastClasspathLock) {
      if (rawClasspath != lastRawClasspath) {
        if (LOG.isDebugEnabled) {
          LOG.debug("buildProcessAppClassPath: $appClassPath")
          LOG.debug("buildProcessPluginClassPath: $pluginClassPath")
        }

        lastRawClasspath = rawClasspath
        lastFilteredClasspath = filterOutOlderVersions(rawClasspath)
        if (LOG.isDebugEnabled && lastRawClasspath != lastFilteredClasspath) {
          LOG.debug("older versions of libraries were removed from classpath:")
          LOG.debug("original classpath: $lastRawClasspath")
          LOG.debug("actual classpath: $lastFilteredClasspath")
        }
      }
      return lastFilteredClasspath!!
    }
  }

  private fun getAdditionalApplicationClasspath(): List<String> {
    return if (Registry.`is`("jps.build.use.workspace.model")) {
      listOf(
        PathManager.getJarPathForClass(WorkspaceEntity::class.java)!!, //intellij.platform.workspace.storage
        PathManager.getJarPathForClass(JpsProjectSerializers::class.java)!!, //intellij.platform.workspace.jps
        PathManager.getJarPathForClass(TracerLevel::class.java)!!, //intellij.platform.diagnostic.telemetry
        PathManager.getJarPathForClass(Activity::class.java)!!, //intellij.platform.diagnostic
        PathManager.getJarPathForClass(HashBiMap::class.java)!!, //Guava
        PathManager.getJarPathForClass(kotlinx.coroutines.CoroutineScope::class.java)!!, //kotlinx-coroutines-core
        PathManager.getJarPathForClass(kotlin.reflect.full.NoSuchPropertyException::class.java)!!, //kotlin-reflect
        PathManager.getJarPathForClass(io.opentelemetry.api.OpenTelemetry::class.java)!!, //opentelemetry
        PathManager.getJarPathForClass(io.opentelemetry.context.propagation.ContextPropagators::class.java)!!, //opentelemetry
        PathManager.getJarPathForClass(com.esotericsoftware.kryo.kryo5.Kryo::class.java)!!, //Kryo5
      )
    }
    else emptyList()
  }

  /**
   * For internal use only, use [getBuildProcessClasspath] to get full classpath instead.
   */
  @ApiStatus.Internal
  fun getBuildProcessPluginsClasspath(project: Project): List<String> {
    val dynamicClasspath = BuildProcessParametersProvider.EP_NAME.getExtensions(project).flatMapTo(ArrayList()) { parametersProvider ->
      val classPath = parametersProvider.classPath
      if (LOG.isTraceEnabled) {
        classPath.forEach { path ->
          LOG.trace("$path added to classpath from ${parametersProvider.javaClass.name}")
        }
      }
      classPath 
    }
    if (dynamicClasspath.isEmpty()) {
      return staticClasspath
    }
    else {
      dynamicClasspath.addAll(staticClasspath)
      return dynamicClasspath
    }
  }

  private val staticClasspath: List<String>
    get() {
      var classpath = compileServerPluginsClasspath
      if (classpath == null) {
        classpath = computeCompileServerPluginsClasspath()
        compileServerPluginsClasspath = classpath
      }
      return classpath
    }

  companion object {
    @TestOnly
    fun filterOutOlderVersionsForTests(classpath: List<String>): List<String> = filterOutOlderVersions(classpath)

    @JvmStatic
    fun getLauncherClasspath(project: Project): List<String> {
      return BuildProcessParametersProvider.EP_NAME.getExtensions(project).flatMap { it.launcherClassPath }
    }
  }
}

private val LOG = logger<BuildProcessClasspathManager>()

private fun findClassesRoot(relativePath: String, plugin: IdeaPluginDescriptor, baseFile: Path): String? {
  val jarFile = baseFile.resolve("lib/$relativePath")
  if (Files.exists(jarFile)) {
    return jarFile.toString()
  }

  if (AppMode.isDevServer()) {
    check(Files.isDirectory(baseFile))
    return baseFile.toString()
  }

  // ... 'plugin run configuration': all module outputs are copied to 'classes' folder
  val classesDir = baseFile.resolve("classes")
  if (Files.isDirectory(classesDir)) {
    return classesDir.toString()
  }

  // development mode
  if (PluginManagerCore.isRunningFromSources()) {
    val fileName = FileUtilRt.getNameWithoutExtension(PathUtilRt.getFileName(relativePath))
    //try restoring module name from JAR name automatically generated by BaseLayout.convertModuleNameToFileName
    val moduleName = if (relativePath.startsWith("modules/")) fileName else "intellij." + fileName.replace('-', '.')
    val mapping = PathManager.getArchivedCompiledClassesMapping()
    if (mapping != null) {
      // baseFile is ".../idea-compile-parts-v2/production/<module-name>/"
      // We should take ".../idea-compile-parts-v2/production/<module-name-2>/<hash>.jar"
      val moduleJar = mapping["production/$moduleName"]?.let(Path::of)
      if (moduleJar != null) {
        if (Files.exists(moduleJar)) {
          return moduleJar.toString()
        }
      }
    }
    else {
      // ... try "out/classes/production/<module-name>", assuming that JAR name was automatically generated from module name
      var baseOutputDir = baseFile.parent
      if (baseOutputDir.fileName.toString() == "test") {
        baseOutputDir = baseOutputDir.parent.resolve("production")
      }
      val moduleDir = baseOutputDir.resolve(moduleName)
      if (Files.isDirectory(moduleDir)) {
        return moduleDir.toString()
      }
    }
    // ... try "<plugin-dir>/lib/<jar-name>", assuming that <jar-name> is a module library committed to VCS
    val pluginDir = getPluginDir(plugin)
    if (pluginDir != null) {
      val libraryFile = File(pluginDir, "lib/" + PathUtilRt.getFileName(relativePath))
      if (libraryFile.exists()) {
        return libraryFile.path
      }
    }
    // ... look for <jar-name> on the classpath, assuming that <jar-name> is an external (read: Maven) library
    try {
      val urls = BuildProcessClasspathManager::class.java.classLoader.getResources(JarFile.MANIFEST_NAME).asSequence()
      val jarPath = urls.mapNotNull { URLUtil.splitJarUrl(it.file)?.first }.firstOrNull { PathUtilRt.getFileName(it) == relativePath }
      if (jarPath != null) {
        return jarPath
      }
    }
    catch (ignored: IOException) {
    }
  }
  LOG.error(PluginException("Cannot add '$relativePath' from '${plugin.name} ${plugin.version}'" +
                            " (plugin path: $baseFile) to compiler classpath", plugin.pluginId))
  return null
}

private fun computeCompileServerPluginsClasspath(): List<String> {
  val classpath = ArrayList<String>()
  for (serverPlugin in CompileServerPlugin.EP_NAME.extensionList) {
    val pluginId = serverPlugin.pluginDescriptor.pluginId
    val plugin = PluginManagerCore.getPlugin(pluginId)
    LOG.assertTrue(plugin != null, pluginId)
    val baseFile = plugin!!.pluginPath
    if (Files.isRegularFile(baseFile)) {
      classpath.add(baseFile.toString())
      LOG.trace { "$baseFile added to process classpath from $pluginId" }
    }
    else {
      serverPlugin.classpath.splitToSequence(';').mapNotNullTo(classpath) {
        val classesRoot = findClassesRoot(relativePath = it, plugin = plugin, baseFile = baseFile)
        if (classesRoot != null) {
          LOG.trace { "$classesRoot added to process classpath from $pluginId" }
        }
        classesRoot
      }
    }
  }
  return classpath
}

private fun getPluginDir(plugin: IdeaPluginDescriptor): File? {
  val pluginDirName = StringUtilRt.getShortName(plugin.pluginId.idString)
  val extraDir = System.getProperty("idea.external.build.development.plugins.dir")
  if (extraDir != null) {
    val extraDirFile = File(extraDir, pluginDirName)
    if (extraDirFile.isDirectory) {
      return extraDirFile
    }
  }
  var pluginHome = PluginPathManager.getPluginHome(pluginDirName)
  if (!pluginHome.isDirectory && StringUtil.isCapitalized(pluginDirName)) {
    pluginHome = PluginPathManager.getPluginHome(StringUtil.decapitalize(pluginDirName))
  }
  return if (pluginHome.isDirectory) pluginHome else null
}

private fun filterOutOlderVersions(classpath: List<String>): List<String> {
  data class JarInfo(@JvmField val path: String, @JvmField val title: String, @JvmField val version: String)

  fun readTitleAndVersion(path: String): JarInfo? {
    val file = Path.of(path)
    if (!Files.isRegularFile(file) || !FileUtil.extensionEquals(file.fileName.toString(), "jar")) {
      return null
    }

    JarFile(file.toFile()).use {
      val attributes = it.manifest?.mainAttributes ?: return null
      val title = attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE) ?: return null
      val version = attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION) ?: return null
      return JarInfo(path, title, version)
    }
  }

  val jarInfos = classpath.mapNotNull(::readTitleAndVersion)
  val titleToInfo = jarInfos.groupBy { it.title }
  val pathToInfo = jarInfos.associateBy { it.path }
  return classpath.filter { path ->
    val pathInfo = pathToInfo[path] ?: return@filter true
    val sameTitle = titleToInfo[pathInfo.title]
    if (sameTitle == null || sameTitle.size <= 1) return@filter true
    sameTitle.all { VersionComparatorUtil.compare(it.version, pathInfo.version) <= 0 }
  }
}