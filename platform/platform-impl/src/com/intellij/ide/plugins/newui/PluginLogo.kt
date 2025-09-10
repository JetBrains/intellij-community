// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.plugins.newui

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JetBrainsProtocolHandler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.JBColor
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.svg.loadWithSizes
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.URLUtil
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.ui.JBImageIcon
import kotlinx.coroutines.*
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.swing.Icon
import kotlin.coroutines.CoroutineContext
import com.intellij.util.lang.ZipFile as IntelliJZipFile

private val iconCache = CollectionFactory.createConcurrentWeakValueMap<String, Pair<PluginLogoIconProvider?, PluginLogoIconProvider?>>()
private val MISSING: Pair<PluginLogoIconProvider?, PluginLogoIconProvider?> = Pair(null, null)

private const val CACHE_DIR = "imageCache"
private const val PLUGIN_ICON = "pluginIcon.svg"
private const val PLUGIN_ICON_DARK = "pluginIcon_dark.svg"
private const val PLUGIN_ICON_SIZE = 40
private const val PLUGIN_ICON_SIZE_SCALED = 50
private const val PLUGIN_ICON_SIZE_SCALE = PLUGIN_ICON_SIZE_SCALED.toFloat() / PLUGIN_ICON_SIZE

private val LOG = logger<PluginLogo>()

private var Default: PluginLogoIconProvider? = null
private var lafListenerAdded = false

private fun initLafListener() {
  if (!lafListenerAdded) {
    lafListenerAdded = true
    if (GraphicsEnvironment.isHeadless()) {
      return
    }
    val application = ApplicationManager.getApplication()
    application.messageBus.connect().subscribe(LafManagerListener.TOPIC, LafManagerListener {
      Default = null
      HiDPIPluginLogoIcon.clearCache()
    })
    UIThemeProvider.EP_NAME.addChangeListener({
                                                Default = null
                                                HiDPIPluginLogoIcon.clearCache()
                                              }, application)
  }
}

private fun getIcon(descriptor: IdeaPluginDescriptor): PluginLogoIconProvider {
  val icons = getOrLoadIcon(descriptor)
  return if (icons == null) PluginLogo.getDefault() else if (JBColor.isBright()) icons.first!! else icons.second!!
}

object PluginLogo {
  @JvmStatic
  fun getIcon(descriptor: IdeaPluginDescriptor, big: Boolean, error: Boolean, disabled: Boolean): Icon {
    initLafListener()
    return getIcon(descriptor).getIcon(big, error, disabled)
  }

  @JvmStatic
  fun startBatchMode() {
    service<PluginLogoLoader>().startBatchMode()
  }

  @JvmStatic
  fun endBatchMode() {
    service<PluginLogoLoader>().endBatchMode()
  }

  @JvmName("getDefault")
  internal fun getDefault(): PluginLogoIconProvider {
    if (Default == null) {
      Default = if (AllIcons.Plugins.PluginLogo is CachedImageIcon) {
        HiDPIPluginLogoIcon(AllIcons.Plugins.PluginLogo,
                            AllIcons.Plugins.PluginLogoDisabled,
                            (AllIcons.Plugins.PluginLogo as CachedImageIcon).scale(PLUGIN_ICON_SIZE_SCALE),
                            (AllIcons.Plugins.PluginLogoDisabled as CachedImageIcon).scale(PLUGIN_ICON_SIZE_SCALE))
      }
      else {
        // headless
        HiDPIPluginLogoIcon(AllIcons.Plugins.PluginLogo,
                            AllIcons.Plugins.PluginLogoDisabled,
                            AllIcons.Plugins.PluginLogo,
                            AllIcons.Plugins.PluginLogoDisabled)
      }
    }
    return Default!!
  }

  @JvmStatic
  fun toURL(file: Any): URL? {
    try {
      when (file) {
        is File -> return file.toURI().toURL()
        is Path -> return file.toUri().toURL()
        is ZipFile -> return File(file.name).toURI().toURL()
      }
    }
    catch (e: MalformedURLException) {
      LOG.warn(e)
    }
    return null
  }

  fun height(): Int = PLUGIN_ICON_SIZE

  fun width(): Int = PLUGIN_ICON_SIZE
}

internal fun reloadPluginIcon(icon: Icon, width: Int, height: Int): Icon {
  if (icon is CachedImageIcon) {
    assert(width == height)
    return icon.scale(width.toFloat() / icon.getIconWidth())
  }
  return icon
}

internal fun getPluginIconFileName(light: Boolean): String = PluginManagerCore.META_INF + if (light) PLUGIN_ICON else PLUGIN_ICON_DARK

private fun tryLoadIcon(zipFile: IntelliJZipFile, light: Boolean): PluginLogoIconProvider? {
  val pluginIconFileName = getPluginIconFileName(light)
  try {
    return zipFile.getData(pluginIconFileName)?.let { loadFileIcon(it) }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.warn("Cannot load plugin icon (zipFile=$zipFile, pluginIconFileName=$pluginIconFileName)")
    LOG.debug(e)
    return null
  }
}

private fun getIdForKey(descriptor: IdeaPluginDescriptor): String {
  return descriptor.pluginId.idString +
         if (descriptor.pluginPath == null ||
             MyPluginModel.installingPlugins.any { it.pluginId == descriptor.pluginId } ||
             InstalledPluginsState.getInstance().wasInstalled(descriptor.pluginId)) {
           ""
         }
         else {
           "#local"
         }
}

private fun loadFileIcon(data: ByteArray): PluginLogoIconProvider {
  val images = loadWithSizes(listOf(PLUGIN_ICON_SIZE, PLUGIN_ICON_SIZE_SCALED), data)
  return HiDPIPluginLogoIcon(JBImageIcon(images.get(0)), JBImageIcon(images.get(1)))
}

private fun loadPluginIconsFromUrl(idPlugin: String, lazyIcon: LazyPluginLogoIcon, coroutineContext: CoroutineContext) {
  val idFileName = sanitizeFileName(idPlugin)
  val cache = Path.of(PathManager.getPluginTempPath(), CACHE_DIR)
  val lightFile = cache.resolve("$idFileName.svg")
  val darkFile = cache.resolve(idFileName + "_dark.svg")

  coroutineContext.ensureActive()
  try {
    downloadOrCheckUpdateFile(idPlugin, lightFile, "")
    downloadOrCheckUpdateFile(idPlugin, darkFile, "&theme=DARCULA")
  }
  catch (e: Exception) {
    LOG.debug(e)
    putMissingIcon(idPlugin)
    return
  }

  coroutineContext.ensureActive()
  val light = tryLoadIcon(lightFile)
  val dark = tryLoadIcon(darkFile)
  putIcon(idPlugin = idPlugin, lazyIcon = lazyIcon, light = light, dark = dark)
}

private fun loadPluginIconsFromExploded(paths: Collection<Path>, idPlugin: String, lazyIcon: LazyPluginLogoIcon) {
  for (path in paths) {
    if (Files.isDirectory(path)) {
      if (tryLoadDirIcons(idPlugin = idPlugin, lazyIcon = lazyIcon, path = path)) {
        return
      }
    }
    else {
      // todo why some JARs do not have pluginIcon.svg packed?
      if (tryLoadJarIcons(idPlugin = idPlugin, lazyIcon = lazyIcon, path = path, put = false)) {
        return
      }
    }
  }

  putMissingIcon(idPlugin = idPlugin)
}

private fun loadPluginIconsFromFile(path: Path, idPlugin: String, lazyIcon: LazyPluginLogoIcon) {
  if (Files.isDirectory(path)) {
    if (System.getProperty(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY) != null) {
      if (tryLoadDirIcons(idPlugin = idPlugin, lazyIcon = lazyIcon, path = path.resolve("classes"))) {
        return
      }
    }

    if (tryLoadDirIcons(idPlugin = idPlugin, lazyIcon = lazyIcon, path = path)) {
      return
    }

    val libFile = path.resolve("lib")
    val files = try {
      Files.newDirectoryStream(libFile).use { it.toList() }
    }
    catch (_: NoSuchFileException) {
      null
    }
    catch (_: NotDirectoryException) {
      null
    }
    catch (e: Exception) {
      LOG.error(e)
      null
    }

    if (files.isNullOrEmpty()) {
      putMissingIcon(idPlugin = idPlugin)
      return
    }

    for (file in files) {
      if (!file.toString().endsWith(".jar") && tryLoadDirIcons(idPlugin = idPlugin, lazyIcon = lazyIcon, path = file)) {
        return
      }
      if (tryLoadJarIcons(idPlugin = idPlugin, lazyIcon = lazyIcon, path = file, put = false)) {
        return
      }
    }
  }
  else {
    tryLoadJarIcons(idPlugin = idPlugin, lazyIcon = lazyIcon, path = path, put = true)
    return
  }

  putMissingIcon(idPlugin = idPlugin)
}

private fun tryLoadDirIcons(idPlugin: String, lazyIcon: LazyPluginLogoIcon, path: Path): Boolean {
  val light = tryLoadIcon(path.resolve(getPluginIconFileName(light = true)))
  val dark = tryLoadIcon(path.resolve(getPluginIconFileName(light = false)))
  if (light == null && dark == null) {
    return false
  }

  putIcon(idPlugin = idPlugin, lazyIcon = lazyIcon, light = light, dark = dark)
  return true
}

private fun tryLoadJarIcons(
  idPlugin: String,
  lazyIcon: LazyPluginLogoIcon,
  path: Path,
  put: Boolean,
): Boolean {
  val pathString = path.toString()
  if (!(pathString.endsWith(".zip", ignoreCase = true) || pathString.endsWith(".jar", ignoreCase = true)) || !Files.exists(path)) {
    return false
  }

  try {
    IntelliJZipFile.load(path).use { zipFile ->
      val light = tryLoadIcon(zipFile = zipFile, light = true)
      val dark = tryLoadIcon(zipFile = zipFile, light = false)
      if (put || light != null || dark != null) {
        putIcon(idPlugin = idPlugin, lazyIcon = lazyIcon, light = light, dark = dark)
        return true
      }
    }
  }
  catch (e: Exception) {
    LOG.debug(e)
  }
  return false
}

private fun downloadOrCheckUpdateFile(idPlugin: String, file: Path, theme: String) {
  try {
    val url = MarketplaceUrls.getPluginManagerUrl() + "/api/icon?pluginId=" +
              URLUtil.encodeURIComponent(idPlugin) + theme
    MarketplaceRequests.readOrUpdateFile(file, url, null, "", InputStream::close)
  }
  catch (ignore: HttpRequests.HttpStatusException) {
  }
  catch (e: IOException) {
    LOG.debug(e)
  }
}

private fun getOrLoadIcon(descriptor: IdeaPluginDescriptor): Pair<PluginLogoIconProvider?, PluginLogoIconProvider?>? {
  val idPlugin = getIdForKey(descriptor)
  val icons = iconCache.get(idPlugin)
  if (icons != null) {
    return if (icons.first == null && icons.second == null) null else icons
  }

  val lazyIcon = LazyPluginLogoIcon(PluginLogo.getDefault())
  val lazyIcons = lazyIcon to lazyIcon
  iconCache.put(idPlugin, lazyIcons)
  val info = descriptor to lazyIcon
  val pluginLogoLoader = service<PluginLogoLoader>()
  val prepareToLoad = pluginLogoLoader.prepareToLoad
  if (prepareToLoad == null) {
    pluginLogoLoader.schedulePluginIconLoading(listOf(info))
  }
  else {
    prepareToLoad.add(info)
  }
  return lazyIcons
}

private fun putIcon(idPlugin: String, lazyIcon: LazyPluginLogoIcon, light: PluginLogoIconProvider?, dark: PluginLogoIconProvider?) {
  if (light == null && dark == null) {
    iconCache.put(idPlugin, Pair(PluginLogo.getDefault(), PluginLogo.getDefault()))
    return
  }

  val icons = Pair(light ?: dark!!, dark ?: light)
  iconCache.put(idPlugin, icons)
  lazyIcon.setLogoIcon((if (JBColor.isBright()) icons.first else icons.second)!!)
}

private fun putMissingIcon(idPlugin: String) {
  iconCache.put(idPlugin, MISSING)
}

private fun tryLoadIcon(iconFile: Path): PluginLogoIconProvider? {
  try {
    val data = Files.readAllBytes(iconFile)
    return if (data.isEmpty()) null else loadFileIcon(data)
  }
  catch (ignore: NoSuchFileException) {
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.warn("Cannot load plugin icon (file=$iconFile)")
    LOG.debug(e)
  }
  return null
}

@Service(Service.Level.APP)
private class PluginLogoLoader(private val coroutineScope: CoroutineScope) {
  @JvmField
  var prepareToLoad: MutableList<Pair<IdeaPluginDescriptor, LazyPluginLogoIcon>>? = null

  fun startBatchMode() {
    assert(prepareToLoad == null)
    prepareToLoad = ArrayList()
  }

  fun endBatchMode() {
    assert(prepareToLoad != null)
    val descriptors = prepareToLoad
    prepareToLoad = null
    schedulePluginIconLoading(descriptors!!)
  }

  fun schedulePluginIconLoading(loadInfo: List<Pair<IdeaPluginDescriptor, LazyPluginLogoIcon>>) {
    val app = ApplicationManager.getApplication()
    if (app.isHeadlessEnvironment) {
      return
    }

    coroutineScope.launch(Dispatchers.IO) {
      for (info in loadInfo) {
        launch {
          val idPlugin = getIdForKey(descriptor = info.first)
          val path = info.first.pluginPath
          if (path == null) {
            loadPluginIconsFromUrl(idPlugin = idPlugin, lazyIcon = info.second, coroutineContext = coroutineContext)
          }
          else if (AppMode.isDevServer()) {
            val descriptor = info.first as? IdeaPluginDescriptorImpl
            loadPluginIconsFromExploded(paths = descriptor?.jarFiles ?: listOf(path), idPlugin = idPlugin, lazyIcon = info.second)
          }
          else {
            loadPluginIconsFromFile(path = path, idPlugin = idPlugin, lazyIcon = info.second)
          }
        }
      }
    }
  }
}