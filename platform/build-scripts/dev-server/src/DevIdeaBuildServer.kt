// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.methvin.watcher.DirectoryChangeListener
import io.methvin.watcher.DirectoryWatcher
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.impl.DistributionJARsBuilder
import org.jetbrains.intellij.build.impl.LayoutBuilder
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping
import org.jetbrains.jps.model.artifact.JpsArtifactService
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

private const val SERVER_PORT = 20854

val skippedPluginModules = hashSetOf(
  // skip intellij.codeWithMe.plugin - quiche downloading should be implemented as a maven lib
  "intellij.codeWithMe.plugin",
  // both these plugins wants Kotlin plugin - not installed in IDEA running from sources
  "intellij.gradle.dsl.kotlin.impl",
  "intellij.android.plugin"
)

internal class DevIdeaBuildServer {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      try {
        start()
      }
      catch (e: ConfigurationException) {
        System.err.println(e.message)
        exitProcess(1)
      }
    }
  }
}

private fun start() {
  val productPropertiesClass = System.getProperty("product.properties")?.takeIf { it.isNotBlank() }?.split(':')
  if (productPropertiesClass == null || productPropertiesClass.size != 2) {
    @Suppress("SpellCheckingInspection")
    throw ConfigurationException("Please specify product properties module and class via system property `product.properties.class`, " +
                                 "e.g. `-Dproduct.properties=intellij.idea.ultimate.build:org.jetbrains.intellij.build.IdeaUltimateProperties`")
  }

  val homePath = getHomePath()

  val propertiesClassModuleOutDir = homePath.resolve("out/classes/production/${productPropertiesClass.get(0)}")
  if (!Files.exists(propertiesClassModuleOutDir)) {
    throw ConfigurationException("$propertiesClassModuleOutDir doesn't exist")
  }

  val productProperties = URLClassLoader.newInstance(arrayOf(propertiesClassModuleOutDir.toUri().toURL()))
    .loadClass(productPropertiesClass.get(1))
    .getConstructor(String::class.java).newInstance(homePath.toString()) as ProductProperties


  val allNonTrivialPlugins = productProperties.productLayout.allNonTrivialPlugins
  val bundledMainModuleNames = getBundledMainModuleNames(productProperties, productPropertiesClass)

  val platformPrefix = productProperties.platformPrefix ?: "idea"
  val runDir = createRunDirForProduct(homePath, platformPrefix)

  val buildContext = BuildContext.createContext(getCommunityHomePath(homePath).toString(), homePath.toString(), productProperties,
                                                ProprietaryBuildTools.DUMMY, createBuildOptions(homePath))
  val pluginsDir = runDir.resolve("plugins")

  val mainModuleToNonTrivialPlugin = HashMap<String, BuildItem>(allNonTrivialPlugins.size)
  val moduleNameToPlugin = HashMap<String, BuildItem>()
  for (plugin in allNonTrivialPlugins) {
    if (skippedPluginModules.contains(plugin.mainModule)) {
      continue
    }

    val item = BuildItem(pluginsDir.resolve(DistributionJARsBuilder.getActualPluginDirectoryName(plugin, buildContext)), plugin)
    mainModuleToNonTrivialPlugin.put(plugin.mainModule, item)
    moduleNameToPlugin.put(plugin.mainModule, item)

    plugin.moduleJars.entrySet()
      .asSequence()
      .filter { !it.key.contains('/') }
      .forEach {
        for (name in it.value) {
          moduleNameToPlugin.put(name, item)
        }
      }
  }

  val pluginLayouts = bundledMainModuleNames.mapNotNull { mainModuleName ->
    if (skippedPluginModules.contains(mainModuleName)) {
      return@mapNotNull null
    }

    // by intention we don't use buildContext.findModule as getPluginsByModules does - module name must match
    // (old module names are not supported)
    var item = mainModuleToNonTrivialPlugin.get(mainModuleName)
    if (item == null) {
      val pluginLayout = PluginLayout.plugin(mainModuleName)
      val pluginDir = pluginsDir.resolve(DistributionJARsBuilder.getActualPluginDirectoryName(pluginLayout, buildContext))
      item = BuildItem(pluginDir, PluginLayout.plugin(mainModuleName))
      moduleNameToPlugin.put(mainModuleName, item)
    }
    item
  }

  val artifactOutDir = homePath.resolve("out/classes/artifacts").toString()
  JpsArtifactService.getInstance().getArtifacts(buildContext.project).forEach {
    it.outputPath = "$artifactOutDir/${PathUtilRt.getFileName(it.outputPath)}"
  }

  val builder = DistributionJARsBuilder(buildContext, null)

  Files.writeString(runDir.resolve("libClassPath.txt"), createLibClassPath(buildContext, builder, homePath))

  // initial building
  val start = System.currentTimeMillis()
  // Ant is not able to build in parallel — not clear how correctly clone with all defined custom tasks
  buildPlugins(parallelCount = 1, buildContext, pluginLayouts, builder)
  println("Initial full build of ${pluginLayouts.size} plugins in ${TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start)}s")

  val pluginBuilder = PluginBuilder(builder, buildContext)
  watchChanges(pluginBuilder, moduleNameToPlugin, buildContext)

  val httpServer = createHttpServer(pluginBuilder, buildContext)
  println("Listening on ${httpServer.address.hostString}:${httpServer.address.port}")
  httpServer.start()

  val doneSignal = CountDownLatch(1)

  // wait for ctrl-c
  Runtime.getRuntime().addShutdownHook(object : Thread() {
    override fun run() {
      doneSignal.countDown()
    }
  })

  try {
    doneSignal.await()
  }
  catch (ignore: InterruptedException) {
  }

  println("Server stopping...")
  httpServer.stop(10)
}

private fun getBundledMainModuleNames(productProperties: ProductProperties, productPropertiesClass: List<String>): List<String> {
  val bundledPlugins = productProperties.productLayout.bundledPluginModules
  if (productPropertiesClass.get(1) == "org.jetbrains.intellij.build.IdeaUltimateProperties") {
    // add extra plugins (as IDEA from sources does and as we want to test it)
    return bundledPlugins + listOf("intellij.clouds.kubernetes")
  }
  return bundledPlugins
}

private fun createHttpServer(pluginBuilder: PluginBuilder, buildContext: BuildContext): HttpServer {
  val httpServer = HttpServer.create()
  httpServer.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), SERVER_PORT), 4)
  httpServer.createContext("/build", HttpHandler { exchange ->
    val statusMessage: String?
    try {
      exchange.responseHeaders.add("Content-Type", "text/plain")
      statusMessage = pluginBuilder.buildChanged()
      buildContext.messages.info(statusMessage)
    }
    catch (e: Throwable) {
      exchange.sendResponseHeaders(HttpURLConnection.HTTP_UNAVAILABLE, 0)
      e.printStackTrace(System.err)
      return@HttpHandler
    }

    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
    exchange.responseBody.bufferedWriter().use {
      it.write(statusMessage)
    }
  })
  return httpServer
}

private fun createRunDirForProduct(homePath: Path, platformPrefix: String): Path {
  // if symlinked to ram disk, use real path for performance reasons and avoid any issues in ant/other code
  var rootDir = homePath.resolve("out/dev-run")
  if (Files.exists(rootDir)) {
    // toRealPath must be called only on existing file
    rootDir = rootDir.toRealPath()
  }

  val runDir = rootDir.resolve(platformPrefix)
  // on start delete everything to avoid stale data
  clearDirContent(runDir)
  Files.createDirectories(runDir)
  return runDir
}

fun clearDirContent(dir: Path) {
  if (Files.isDirectory(dir)) {
    Files.newDirectoryStream(dir).use {
      for (path in it) {
        FileUtil.delete(dir)
      }
    }
  }
}

private fun createLibClassPath(buildContext: BuildContext,
                               builder: DistributionJARsBuilder,
                               homePath: Path): String {
  val layoutBuilder = LayoutBuilder(buildContext, false)
  val projectStructureMapping = ProjectStructureMapping()
  builder.processLibDirectoryLayout(layoutBuilder, projectStructureMapping, false)
  // for some reasons maybe duplicated paths - use set
  val classPath = LinkedHashSet<String>()
  for (entry in projectStructureMapping.entries) {
    when (entry) {
      is ModuleOutputEntry -> {
        // File.toURL adds ending slash for directory
        classPath.add(buildContext.getModuleOutputPath(buildContext.findRequiredModule(entry.moduleName)) + File.separatorChar)
      }
      is ProjectLibraryEntry -> {
        classPath.add(entry.libraryFilePath)
      }
      is ModuleLibraryFileEntry -> {
        classPath.add(entry.libraryFilePath)
      }
      else -> throw UnsupportedOperationException("Entry $entry is not supported")
    }
  }

  val projectLibDir = homePath.resolve("lib")
  @Suppress("SpellCheckingInspection")
  val extraJarNames = listOf("ideaLicenseDecoder.jar", "ls-client-api.jar", "y.jar", "ysvg.jar")
  for (extraJarName in extraJarNames) {
    val extraJar = projectLibDir.resolve(extraJarName)
    if (Files.exists(extraJar)) {
      classPath.add(extraJar.toAbsolutePath().toString())
    }
  }
  return classPath.joinToString(separator = "\n")
}

private fun getHomePath(): Path {
  val homePath: Path? = (PathManager.getHomePath(false) ?: PathManager.getHomePathFor(DevIdeaBuildServer::class.java))?.let {
    Paths.get(it)
  }
  if (homePath == null) {
    throw ConfigurationException("Could not find installation home path. Please specify explicitly via `idea.path` system property")
  }
  return homePath
}

private class ConfigurationException(message: String) : RuntimeException(message)

private fun createBuildOptions(homePath: Path): BuildOptions {
  val buildOptions = BuildOptions()
  buildOptions.useCompiledClassesFromProjectOutput = true
  buildOptions.targetOS = BuildOptions.OS_NONE
  buildOptions.cleanOutputFolder = false
  buildOptions.skipDependencySetup = true
  buildOptions.outputRootPath = homePath.resolve("out/dev-server").toString()
  return buildOptions
}

private fun watchChanges(pluginBuilder: PluginBuilder,
                         moduleNameToPlugin: Map<String, BuildItem>,
                         buildContext: BuildContext) {
  val moduleDirToPlugin = HashMap<Path, BuildItem>(moduleNameToPlugin.size)
  val moduleDirs = ArrayList<Path>(moduleNameToPlugin.size)
  for (entry in moduleNameToPlugin) {
    val dir = Paths.get(buildContext.getModuleOutputPath(buildContext.findRequiredModule(entry.key)))
    moduleDirToPlugin.put(dir, entry.value)
    moduleDirs.add(dir)
  }
  val watcher = DirectoryWatcher.builder()
    .paths(moduleDirs)
    .fileHashing(false)
    .listener(DirectoryChangeListener { event ->
      val path = event.path()
      if (path.endsWith("classpath.index") || path.endsWith(".DS_Store")) {
        return@DirectoryChangeListener
      }

      getPluginDir(moduleDirToPlugin, path, moduleDirs)?.let {
        pluginBuilder.addDirtyPluginDir(it, path)
      }
    })
    .build()
  watcher.watchAsync(AppExecutorUtil.getAppExecutorService())
  Runtime.getRuntime().addShutdownHook(object : Thread() {
    override fun run() {
      watcher.close()
    }
  })
}

private fun getPluginDir(moduleDirToPlugin: Map<Path, BuildItem>,
                         path: Path,
                         moduleDirs: List<Path>): BuildItem? {
  moduleDirToPlugin.get(path)?.let {
    return it
  }

  for (dir in moduleDirs) {
    if (path.startsWith(dir)) {
      return moduleDirToPlugin.get(dir)
    }
  }
  return null
}

private fun getCommunityHomePath(homePath: Path): Path {
  val communityDotIdea = homePath.resolve("community/.idea")
  return if (Files.isDirectory(communityDotIdea)) communityDotIdea.parent else homePath
}