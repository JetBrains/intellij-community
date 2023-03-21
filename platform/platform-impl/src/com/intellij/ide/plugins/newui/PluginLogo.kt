// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.plugins.newui

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JetBrainsProtocolHandler
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.ui.JBColor
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.util.Urls.newFromEncoded
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.swing.Icon

private val ICONS = ContainerUtil.createWeakValueMap<String, Pair<PluginLogoIconProvider?, PluginLogoIconProvider?>>()

/**
 * @author Alexander Lobas
 */
object PluginLogo {
  val LOG = Logger.getInstance(PluginLogo::class.java)
  private const val CACHE_DIR = "imageCache"
  private const val PLUGIN_ICON = "pluginIcon.svg"
  private const val PLUGIN_ICON_DARK = "pluginIcon_dark.svg"
  private const val PLUGIN_ICON_SIZE = 40
  private const val PLUGIN_ICON_SIZE_SCALED = 50
  private const val PLUGIN_ICON_SIZE_SCALE = PLUGIN_ICON_SIZE_SCALED.toFloat() / PLUGIN_ICON_SIZE
  private var Default: PluginLogoIconProvider? = null
  private var myPrepareToLoad: MutableList<Pair<IdeaPluginDescriptor, LazyPluginLogoIcon>>? = null
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

  fun getIcon(descriptor: IdeaPluginDescriptor, big: Boolean, error: Boolean, disabled: Boolean): Icon? {
    initLafListener()
    return getIcon(descriptor)?.getIcon(big, error, disabled)
  }

  private fun getIcon(descriptor: IdeaPluginDescriptor): PluginLogoIconProvider? {
    val icons = getOrLoadIcon(descriptor)
    return if (icons != null) if (JBColor.isBright()) icons.first else icons.second!! else getDefault()
  }

  @JvmStatic
  fun startBatchMode() {
    assert(myPrepareToLoad == null)
    myPrepareToLoad = ArrayList()
  }

  @JvmStatic
  fun endBatchMode() {
    assert(myPrepareToLoad != null)
    val descriptors: List<Pair<IdeaPluginDescriptor, LazyPluginLogoIcon>>? = myPrepareToLoad
    myPrepareToLoad = null
    service<PluginLogoLoader>().schedulePluginIconLoading(descriptors!!)
  }

  internal fun getDefault(): PluginLogoIconProvider {
    if (Default == null) {
      Default = HiDPIPluginLogoIcon(AllIcons.Plugins.PluginLogo,
                                    AllIcons.Plugins.PluginLogoDisabled,
                                    (AllIcons.Plugins.PluginLogo as CachedImageIcon).scale(
                                      PLUGIN_ICON_SIZE_SCALE),
                                    (AllIcons.Plugins.PluginLogoDisabled as CachedImageIcon).scale(
                                      PLUGIN_ICON_SIZE_SCALE))
    }
    return Default!!
  }

  fun reloadIcon(icon: Icon, width: Int, height: Int): Icon {
    if (icon is CachedImageIcon) {
      assert(width == height)
      return icon.scale(width.toFloat() / icon.getIconWidth())
    }
    return icon
  }

  private fun getOrLoadIcon(descriptor: IdeaPluginDescriptor): Pair<PluginLogoIconProvider?, PluginLogoIconProvider?>? {
    val idPlugin = getIdForKey(descriptor)
    val icons = ICONS.get(idPlugin)
    if (icons != null) {
      return if (icons.first == null && icons.second == null) null else icons
    }
    val lazyIcon = LazyPluginLogoIcon(getDefault())
    val lazyIcons = Pair<PluginLogoIconProvider, PluginLogoIconProvider?>(lazyIcon, lazyIcon)
    ICONS[idPlugin] = lazyIcons
    val info = Pair(descriptor, lazyIcon)
    if (myPrepareToLoad == null) {
      service<PluginLogoLoader>().schedulePluginIconLoading(listOf(info))
    }
    else {
      myPrepareToLoad!!.add(info)
    }
    return lazyIcons
  }

  internal fun loadPluginIcons(descriptor: IdeaPluginDescriptor, lazyIcon: LazyPluginLogoIcon) {
    val idPlugin = getIdForKey(descriptor)
    val path = descriptor.pluginPath
    if (path != null) {
      if (Files.isDirectory(path)) {
        if (System.getProperty(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY) != null) {
          if (tryLoadDirIcons(idPlugin, lazyIcon, path.resolve("classes"))) {
            return
          }
        }
        if (tryLoadDirIcons(idPlugin, lazyIcon, path)) {
          return
        }
        val libFile = path.resolve("lib")
        if (!Files.exists(libFile) || !Files.isDirectory(libFile)) {
          putIcon(idPlugin, lazyIcon, null, null)
          return
        }
        val files = NioFiles.list(libFile)
        if (files.isEmpty()) {
          putIcon(idPlugin, lazyIcon, null, null)
          return
        }
        for (file in files) {
          if (tryLoadDirIcons(idPlugin, lazyIcon, file)) {
            return
          }
          if (tryLoadJarIcons(idPlugin, lazyIcon, file, false)) {
            return
          }
        }
      }
      else {
        tryLoadJarIcons(idPlugin, lazyIcon, path, true)
        return
      }
      putIcon(idPlugin, lazyIcon, null, null)
      return
    }
    val idFileName = FileUtil.sanitizeFileName(idPlugin)
    val cache = Path.of(PathManager.getPluginTempPath(), CACHE_DIR)
    val lightFile = cache.resolve("$idFileName.svg")
    val darkFile = cache.resolve(idFileName + "_dark.svg")
    if (Files.exists(cache)) {
      val light = tryLoadIcon(lightFile)
      val dark = tryLoadIcon(darkFile)
      if (light != null || dark != null) {
        putIcon(idPlugin, lazyIcon, light, dark)
        return
      }
    }
    try {
      downloadFile(idPlugin, lightFile, "")
      downloadFile(idPlugin, darkFile, "&theme=DARCULA")
    }
    catch (e: Exception) {
      LOG.debug(e)
    }
    if (ApplicationManager.getApplication().isDisposed) {
      return
    }
    val light = tryLoadIcon(lightFile)
    val dark = tryLoadIcon(darkFile)
    putIcon(idPlugin, lazyIcon, light, dark)
  }

  private fun getIdForKey(descriptor: IdeaPluginDescriptor): String {
    return descriptor.pluginId.idString +
           if (descriptor.pluginPath == null ||
               MyPluginModel.getInstallingPlugins().contains(descriptor) ||
               InstalledPluginsState.getInstance().wasInstalled(descriptor.pluginId)) ""
           else "#local"
  }

  private fun tryLoadDirIcons(idPlugin: String, lazyIcon: LazyPluginLogoIcon, path: Path): Boolean {
    val light = tryLoadIcon(path, true)
    val dark = tryLoadIcon(path, false)
    if (light != null || dark != null) {
      putIcon(idPlugin, lazyIcon, light, dark)
      return true
    }
    return false
  }

  private fun tryLoadJarIcons(idPlugin: String,
                              lazyIcon: LazyPluginLogoIcon,
                              path: Path,
                              put: Boolean): Boolean {
    val pathString = path.toString()
    if (!(pathString.endsWith(".zip") || pathString.endsWith(".jar")) || !Files.exists(path)) {
      return false
    }
    try {
      ZipFile(path.toFile()).use { zipFile ->
        val light = tryLoadIcon(zipFile, true)
        val dark = tryLoadIcon(zipFile, false)
        if (put || light != null || dark != null) {
          putIcon(idPlugin, lazyIcon, light, dark)
          return true
        }
      }
    }
    catch (e: Exception) {
      LOG.debug(e)
    }
    return false
  }

  private fun downloadFile(idPlugin: String, file: Path, theme: String) {
    if (ApplicationManager.getApplication().isDisposed) {
      return
    }
    try {
      val url = newFromEncoded(ApplicationInfoImpl.getShadowInstance().pluginManagerUrl +
                               "/api/icon?pluginId=" + URLUtil.encodeURIComponent(idPlugin) + theme)
      HttpRequests.request(url).productNameAsUserAgent().saveToFile(file, null)
    }
    catch (ignore: HttpRequests.HttpStatusException) {
    }
    catch (e: IOException) {
      LOG.debug(e)
    }
  }

  private fun putIcon(idPlugin: String,
                      lazyIcon: LazyPluginLogoIcon,
                      light: PluginLogoIconProvider?,
                      dark: PluginLogoIconProvider?) {
    ApplicationManager.getApplication().invokeLater({
                                                      if (light == null && dark == null) {
                                                        ICONS.put(idPlugin, Pair(null, null))
                                                        return@invokeLater
                                                      }
                                                      val icons = Pair(light ?: dark, dark ?: light)
                                                      ICONS.put(idPlugin, icons)
                                                      lazyIcon.setLogoIcon((if (JBColor.isBright()) icons.first else icons.second)!!)
                                                    }, ModalityState.any())
  }

  private fun tryLoadIcon(dirFile: Path, light: Boolean): PluginLogoIconProvider? {
    return tryLoadIcon(dirFile.resolve(getIconFileName(light)))
  }

  private fun tryLoadIcon(iconFile: Path): PluginLogoIconProvider? {
    return try {
      if (Files.exists(iconFile) && Files.size(iconFile) > 0) loadFileIcon(toURL(iconFile)) { Files.newInputStream(iconFile) } else null
    }
    catch (e: NoSuchFileException) {
      null
    }
    catch (e: IOException) {
      LOG.warn(e)
      null
    }
  }

  private fun tryLoadIcon(zipFile: ZipFile, light: Boolean): PluginLogoIconProvider? {
    val iconEntry = zipFile.getEntry(getIconFileName(light))
    return if (iconEntry == null) null else loadFileIcon(toURL(zipFile)) { zipFile.getInputStream(iconEntry) }
  }

  fun toURL(file: Any): URL? {
    try {
      if (file is File) {
        return file.toURI().toURL()
      }
      if (file is Path) {
        return file.toUri().toURL()
      }
      if (file is ZipFile) {
        return File(file.name).toURI().toURL()
      }
    }
    catch (e: MalformedURLException) {
      LOG.warn(e)
    }
    return null
  }

  fun getIconFileName(light: Boolean): String {
    return PluginManagerCore.META_INF + if (light) PLUGIN_ICON else PLUGIN_ICON_DARK
  }

  fun height(): Int {
    return PLUGIN_ICON_SIZE
  }

  fun width(): Int {
    return PLUGIN_ICON_SIZE
  }

  private fun loadFileIcon(url: URL?, provider: ThrowableComputable<out InputStream, out IOException>): PluginLogoIconProvider? {
    return try {
      val logo40 = HiDPIPluginLogoIcon.loadSVG(url, provider.compute(), PLUGIN_ICON_SIZE, PLUGIN_ICON_SIZE)
      val logo80 = HiDPIPluginLogoIcon.loadSVG(url, provider.compute(), PLUGIN_ICON_SIZE_SCALED, PLUGIN_ICON_SIZE_SCALED)
      HiDPIPluginLogoIcon(logo40, logo80)
    }
    catch (e: IOException) {
      LOG.debug(e)
      null
    }
  }
}

private class PluginLogoLoader(private val coroutineScope: CoroutineScope) {
  fun schedulePluginIconLoading(loadInfo: List<Pair<IdeaPluginDescriptor, LazyPluginLogoIcon>>) {
    val app = ApplicationManager.getApplication()
    if (app.isHeadlessEnvironment) {
      return
    }

    coroutineScope.launch(Dispatchers.IO) {
      for (info in loadInfo) {
        launch {
          PluginLogo.loadPluginIcons(info.first, info.second)
        }
      }
    }
  }
}