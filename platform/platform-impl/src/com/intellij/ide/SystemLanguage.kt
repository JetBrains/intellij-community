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
  private var myLocale: Locale = Locale.getDefault()
  private var myNeedInstallPlugin = false
  private var myPluginId: String? = null
  private var myPluginUrl: String? = null

  @NlsSafe
  private var myPluginSize: String? = null

  companion object {
    private val LOG = Logger.getInstance(SystemLanguage::class.java)

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
    myNeedInstallPlugin = false
    myPluginSize = null
    myPluginUrl = null
    myPluginId = null

    val property = System.getProperty(TOOLBOX_KEY)
    if (property != null) {
      myLocale = Locale(property)
      return
    }

    val locale = loadSavedLanguage()
    if (locale != null) {
      myLocale = locale
      return
    }

    myLocale = Locale.getDefault()

    if (ConfigImportHelper.isNewUser() || ConfigImportHelper.isConfigImported()) {
      if (Locale.ENGLISH.language != myLocale.language) {
        val path = Path.of(PathManager.getPreInstalledPluginsPath(), LANGUAGE_PLUGINS_FILE)
        if (Files.exists(path)) {
          try {
            val root = JDOMUtil.load(path)
            for (plugin in root.getChildren("plugin")) {
              val language = plugin.getAttributeValue("language")
              if (myLocale.language == language) {
                myPluginId = plugin.getAttributeValue("id")
                myPluginSize = plugin.getAttributeValue("size")
                myPluginUrl = plugin.text
                break
              }
            }
          }
          catch (e: Exception) {
            LOG.warn(e)
          }
        }
        myNeedInstallPlugin = myPluginId != null && myPluginUrl != null

        if (myNeedInstallPlugin && ConfigImportHelper.isConfigImported()) {
          myNeedInstallPlugin = PluginManagerCore.findPlugin(PluginId.getId(myPluginId!!)) == null
          if (!myNeedInstallPlugin) {
            saveLanguage(myLocale.language)
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

  fun getLocale() = myLocale

  fun needInstallPlugin() = myNeedInstallPlugin

  fun doChooseLanguage(args: List<String>) {
    if (!myNeedInstallPlugin) {
      return
    }

    val bundle = DynamicBundle.getResourceBundle(DynamicBundle::class.java.classLoader, "messages.ChooseLanguageBundle", myLocale)
    val title = bundle.getString("chooseLanguage.dialog.title")
    val message = StringUtil.replace(bundle.getString("chooseLanguage.dialog.message"), "{0}", myPluginSize!!)
    val buttons = arrayOf(bundle.getString("chooseLanguage.dialog.okButton"), bundle.getString("chooseLanguage.dialog.cancelButton"))

    val dialog = AlertDialog(null, null, message, title, buttons, 0, -1, AllIcons.General.InformationDialog, null, null)
    dialog.show()

    if (dialog.exitCode == Messages.YES) {
      val progress = ProgressDialog(bundle)
      SwingUtilities.invokeLater { Thread({ doInstallPlugin(progress, args) }, "Plugin downloader").start() }
      progress.isVisible = true
    }
    else {
      myLocale = Locale.ENGLISH
      saveLanguage(null)
      clearCaches()
    }
  }

  private fun doInstallPlugin(progress: ProgressDialog, args: List<String>) {
    var result = false

    try {
      val node = PluginNode(PluginId.getId(myPluginId!!))
      node.downloadUrl = myPluginUrl
      val downloader = PluginDownloader.createDownloader(node, "", null)
      if (downloader.prepareToInstall(progress.indicator)) {
        PluginInstaller.unpackPlugin(downloader.filePath, PathManager.getPluginsDir())
        result = true
        LOG.warn("=== Early plugin installed: $myPluginId ===")
      }
      else {
        LOG.warn("=== Early plugin install: not prepared $myPluginId ===")
      }
    }
    catch (e: IOException) {
      LOG.warn(e)
    }

    if (!result) {
      myLocale = Locale.ENGLISH
    }
    saveLanguage(if (result) myLocale.language else null)

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
    private val myProgressBar = JProgressBar(0, 100)
    private var myCanceled = false

    val indicator: ProgressIndicator = object : AbstractProgressIndicatorBase() {
      override fun setFraction(fraction: Double) {
        myProgressBar.value = (fraction * 100).toInt()
      }

      override fun isCanceled(): Boolean {
        return myCanceled
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
      progressPanel.add(myProgressBar, BorderLayout.SOUTH)
      panel.add(Wrapper(progressPanel))

      val cancelButton = JButton(bundle.getString("chooseLanguage.progress.dialog.cancelButton"))
      cancelButton.addActionListener {
        myCanceled = true
      }
      panel.add(Wrapper(cancelButton), BorderLayout.EAST)
      contentPane = panel
      pack()

      setLocationRelativeTo(null)
    }
  }
}