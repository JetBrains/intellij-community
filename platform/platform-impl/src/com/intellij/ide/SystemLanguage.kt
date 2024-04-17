// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.DynamicBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.PopupBorder
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.messages.AlertDialog
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Frame
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.swing.*

/**
 * @author Alexander Lobas
 */
class SystemLanguage private constructor() {
  private var locale: Locale = Locale.getDefault()
  private var needInstallPlugin = false
  private var pluginId: String? = null
  private var pluginUrl: String? = null

  @NlsSafe
  private var pluginSize: String? = null

  companion object {
    private val LOG: Logger get() = logger<SystemLanguage>()

    private const val TOOLBOX_KEY = "toolbox.run.with.language"
    private const val LANGUAGE_PLUGINS_FILE = "language-plugins.xml"
    private const val SAVED_LANGUAGE_FILE = "before-start-language.txt"

    private var ourInstance: SystemLanguage? = null

    @JvmStatic
    fun getInstance(): SystemLanguage {
      if (ourInstance == null) {
        ourInstance = SystemLanguage()
      }
      return ourInstance!!
    }
  }

  init {
    loadState()
  }

  private fun loadState() {
    needInstallPlugin = false
    pluginSize = null
    pluginUrl = null
    pluginId = null

    val property = System.getProperty(TOOLBOX_KEY)
    if (property != null) {
      locale = Locale(property)
      return
    }

    val savedLocale = loadSavedLanguage()
    if (savedLocale != null) {
      locale = savedLocale
      return
    }

    locale = Locale.getDefault()

    if (ConfigImportHelper.isNewUser() || ConfigImportHelper.isConfigImported()) {
      if (Locale.ENGLISH.language != locale.language) {
        val path = Path.of(PathManager.getPreInstalledPluginsPath(), LANGUAGE_PLUGINS_FILE)
        if (Files.exists(path)) {
          try {
            val root = JDOMUtil.load(path)
            for (plugin in root.getChildren("plugin")) {
              val language = plugin.getAttributeValue("language")
              if (locale.language == language || locale.language.startsWith(language)) {
                pluginId = plugin.getAttributeValue("id")
                pluginSize = plugin.getAttributeValue("size")
                pluginUrl = plugin.text
                break
              }
            }
          }
          catch (e: Exception) {
            LOG.warn(e)
          }
        }
        needInstallPlugin = pluginId != null && pluginUrl != null

        if (needInstallPlugin && ConfigImportHelper.isConfigImported()) {
          needInstallPlugin = PluginManagerCore.findPlugin(PluginId.getId(pluginId!!)) == null
          if (!needInstallPlugin) {
            saveLanguage(locale.language)
          }
        }
      }
    }
  }

  private fun getSavedLanguageFile(): Path = Path.of(PathManager.getConfigPath(), SAVED_LANGUAGE_FILE)

  private fun loadSavedLanguage(): Locale? {
    val path = getSavedLanguageFile()
    if (Files.exists(path)) {
      try {
        return Locale(Files.readString(path))
      }
      catch (e: IOException) {
        LOG.warn(e)
      }
    }
    return null
  }

  private fun saveLanguage(language: String?) {
    val path = getSavedLanguageFile()
    try {
      if (language == null) {
        Files.delete(path)
      }
      else {
        Files.writeString(path, language)
      }
    }
    catch (e: IOException) {
      LOG.debug(e)
    }
  }

  fun getLocale() = locale

  fun needInstallPlugin() = needInstallPlugin

  fun doChooseLanguage(args: List<String>) {
    if (!needInstallPlugin) {
      return
    }

    val bundle = DynamicBundle.getResourceBundle(DynamicBundle::class.java.classLoader, "messages.ChooseLanguageBundle", locale)
    val title = bundle.getString("chooseLanguage.dialog.title")
    val message = StringUtil.replace(bundle.getString("chooseLanguage.dialog.message"), "{0}", pluginSize!!)
    val buttons = arrayOf(bundle.getString("chooseLanguage.dialog.okButton"), bundle.getString("chooseLanguage.dialog.cancelButton"))

    val dialog = AlertDialog(null, null, message, title, buttons, 0, -1, AllIcons.General.InformationDialog, null, null)
    dialog.show()

    if (dialog.exitCode == Messages.YES) {
      val progress = ProgressDialog(bundle)
      SwingUtilities.invokeLater { Thread({ doInstallPlugin(progress, args) }, "Plugin downloader").start() }
      progress.isVisible = true
    }
    else {
      locale = Locale.ENGLISH
      saveLanguage(null)
      clearCaches()
    }
  }

  private fun doInstallPlugin(progress: ProgressDialog, args: List<String>) {
    var result = false

    try {
      val node = PluginNode(PluginId.getId(pluginId!!))
      node.downloadUrl = pluginUrl
      val downloader = PluginDownloader.createDownloader(node, "", null)
      if (downloader.prepareToInstall(progress.indicator)) {
        PluginInstaller.unpackPlugin(downloader.filePath, PathManager.getPluginsDir())
        result = true
        LOG.warn("=== Early plugin installed: $pluginId ===")
      }
      else {
        LOG.warn("=== Early plugin install: not prepared $pluginId ===")
      }
    }
    catch (e: IOException) {
      LOG.warn(e)
    }

    if (!result) {
      locale = Locale.ENGLISH
    }
    saveLanguage(if (result) locale.language else null)

    SwingUtilities.invokeLater {
      progress.isVisible = false
      if (result) {
        ConfigImportHelper.restartWithContinue(PathManager.getConfigDir(), args, null)
      }
      else {
        clearCaches()
      }
    }
  }

  private fun clearCaches() {
    JBUIScale.drop()
    IdeBundle.clearCache()
  }

  private class ProgressDialog(bundle: ResourceBundle) : JDialog(null as Frame?, null, true) {
    private val progressBar = JProgressBar(0, 100)
    private var canceled = false

    val indicator: ProgressIndicator = object : AbstractProgressIndicatorBase() {
      override fun setFraction(fraction: Double) {
        progressBar.value = (fraction * 100).toInt()
      }

      override fun isCanceled(): Boolean {
        return canceled
      }

      override fun isCancelable(): Boolean {
        return true
      }
    }

    init {
      isUndecorated = true
      getRootPane().windowDecorationStyle = JRootPane.NONE
      getRootPane().border = PopupBorder.Factory.create(true, true)

      val panel = JPanel(BorderLayout(JBUI.scale(10), JBUI.scale(20)))
      panel.border = JBUI.Borders.empty(10, 20, 30, 20)

      panel.add(JLabel(bundle.getString("chooseLanguage.progress.dialog.title"), SwingConstants.CENTER), BorderLayout.NORTH)

      val progressPanel = JPanel(BorderLayout())
      progressPanel.border = JBUI.Borders.emptyBottom(15)
      progressPanel.add(JLabel(bundle.getString("chooseLanguage.progress.dialog.message")), BorderLayout.NORTH)
      progressPanel.add(progressBar, BorderLayout.SOUTH)
      panel.add(Wrapper(progressPanel))

      val cancelButton = JButton(bundle.getString("chooseLanguage.progress.dialog.cancelButton"))
      cancelButton.addActionListener {
        canceled = true
      }
      panel.add(Wrapper(cancelButton), BorderLayout.EAST)
      contentPane = panel
      pack()

      setLocationRelativeTo(null)
    }
  }
}