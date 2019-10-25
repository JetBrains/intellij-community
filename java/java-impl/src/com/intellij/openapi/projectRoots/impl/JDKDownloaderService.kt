// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.ui.layout.*
import com.intellij.util.Consumer
import com.intellij.util.Urls
import com.intellij.util.io.Decompressor
import com.intellij.util.io.HttpRequests
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.io.File
import java.io.IOException
import java.lang.RuntimeException
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.math.absoluteValue

private val LOG = logger<JDKDownloaderService>()

internal class JDKDownloaderService {
  companion object {
    @JvmStatic
    fun getInstance(): JDKDownloaderService? = if (!isEnabled) null else ApplicationManager.getApplication().getService(JDKDownloaderService::class.java)

    @JvmStatic
    val isEnabled
      get() = Registry.`is`("jdk.downloader.ui")
  }

  fun showCustomCreateUI(javaSdkType: JavaSdkImpl,
                         sdkModel: SdkModel,
                         parentComponent: JComponent,
                         callback: Consumer<Sdk>) {
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent)) ?: return

    ProgressManager.getInstance().run(object : Task.Modal(project, "Downloading JDK list...", true) {
      override fun run(indicator: ProgressIndicator) {
        val items = try {
          JDKListDownloader.downloadModel(progress = indicator)
        } catch (t: IOException) {
          LOG.warn(t.message, t)
          return
        }

        invokeLater {
          if (project.isDisposedOrDisposeInProgress) return@invokeLater
          val jdkHome = SelectOrDownloadJDKDialog(project, parentComponent, javaSdkType, items).selectOrDownloadAndUnpackJDK()

          if (jdkHome != null) {
            (sdkModel as ProjectSdksModel).addSdk(javaSdkType, jdkHome, callback)
          }
        }
      }
    })
  }
}

private class SelectOrDownloadJDKDialog(
  val project: Project,
  val parentComponent: Component?,
  val sdkType: SdkType,
  val items: List<JDKDownloadItem>
): DialogWrapper(project, parentComponent, false, IdeModalityType.PROJECT) {

  private val panel : JComponent

  private val selectFromDiskAction = object: DialogWrapperAction("Find on the disk...") {
    override fun doAction(e: ActionEvent?) = doSelectFromDiskAction()
  }

  private lateinit var selectedItem: JDKDownloadItem
  private lateinit var selectedPath: String

  private lateinit var resultingJDKHome: String

  init {
    title = "Download JDK"
    setResizable(false)

    val defaultItem = items.first()

    val vendorComboBox = ComboBox(items.map { it.vendor }.distinct().sortedBy { it.vendor.toUpperCase() }.toTypedArray())
    vendorComboBox.selectedItem = defaultItem.vendor
    vendorComboBox.renderer = listCellRenderer { vendor, _, _ -> setText(vendor.vendor) }

    val versionModel = DefaultComboBoxModel<JDKDownloadItem>()
    val versionComboBox = ComboBox(versionModel)
    versionComboBox.renderer = listCellRenderer { it, _, _ ->
      setText("${it.version} (${StringUtil.formatFileSize(it.size)})")
    }

    fun selectVersions(newVendor: JDKVendor) {
      val newVersions = items.filter { it.vendor == newVendor }.sortedBy { it.version.toLowerCase() }
      versionModel.removeAllElements()
      for (version in newVersions) {
        versionModel.addElement(version)
      }
    }
    selectVersions(defaultItem.vendor)

    val installDirTextField = textFieldWithBrowseButton(
      project = project,
      browseDialogTitle = "Select installation path for the JDK",
      fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    )

    fun selectInstallPath(newVersion: JDKDownloadItem) {
      val path = when {
        SystemInfo.isLinux || SystemInfo.isMac -> "~/.jdks/${newVersion.installFolderName}"
        SystemInfo.isWindows -> System.getProperty("user.home") + "\\.jdks\\${newVersion.installFolderName}"
        else -> error("Unsupported OS")
      }
      installDirTextField.text = path
      selectedPath = path
      selectedItem = newVersion
    }
    selectInstallPath(defaultItem)

    vendorComboBox.onSelectionChange(::selectVersions)
    versionComboBox.onSelectionChange(::selectInstallPath)
    installDirTextField.onTextChange { selectedPath = it } //TODO: validate paths?

    panel = panel {
      row("Vendor:") { vendorComboBox.invoke() }
      row("Version:") { versionComboBox.invoke() }
      row("Install JDK to:") { installDirTextField.invoke() }
    }

    init()
  }

  override fun createActions() = arrayOf(selectFromDiskAction, *super.createActions())
  override fun createCenterPanel() = panel

  private fun doSelectFromDiskAction() {
    var jdkHome: String? = null
    SdkConfigurationUtil.selectSdkHome(sdkType) {
      jdkHome = it
    }

    jdkHome?.let {
      resultingJDKHome = it
      close(OK_EXIT_CODE)
    }
  }

  override fun doOKAction() {
    val installItem = selectedItem
    val installDir = File(FileUtil.expandUserHome(selectedPath))

    val validateError = runCatching {
      JDKInstaller.validateInstallDir(installDir)
    }.exceptionOrNull()

    if (validateError != null) {
      //TODO: review
      setErrorText(validateError.message)
      return
    }

    ProgressManager.getInstance().run(object: Task.Modal(project, "Installing JDK...", true) {
      override fun run(indicator: ProgressIndicator) {
        val installError = runCatching {
          JDKInstaller.installJDK(installItem, installDir, indicator)
        }.exceptionOrNull()

        if (installError == null) {
          return invokeLater {
            resultingJDKHome = installDir.absolutePath
            superDoOKAction()
          }
        }

        setErrorText(installError.message)
        LOG.warn("Failed to install JDK $installItem to $installDir. ${installError.message}", installError)
      }
    })
  }

  private fun superDoOKAction() = super.doOKAction()

  // returns unpacked JDK location (if any) or null if cancelled
  fun selectOrDownloadAndUnpackJDK(): String? = when {
    showAndGet() -> selectedPath
    else -> null
  }

  private inline fun TextFieldWithBrowseButton.onTextChange(crossinline  action: (String) -> Unit) {
    textField.document.addDocumentListener(object : DocumentListener {
      override fun changedUpdate(e: DocumentEvent?) = action(text)
      override fun insertUpdate(e: DocumentEvent?) = action(text)
      override fun removeUpdate(e: DocumentEvent?) = action(text)
    })
  }

  private inline fun <reified T> ComboBox<T>.onSelectionChange(crossinline action: (T) -> Unit) {
    this.addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED) action(e.item as T)
    }
  }
}

object JDKInstaller {
  fun validateInstallDir(targetDir: File) {
    if (targetDir.isFile) throw RuntimeException("Failed to extract JDK. Target path is an existing file")
    if (targetDir.isDirectory && targetDir.listFiles()?.isNotEmpty() == true) {
      throw RuntimeException("Failed to extract JDK. Target path is an existing non-empty directory")
    }
  }

  fun installJDK(item: JDKDownloadItem, targetDir: File, indicator: ProgressIndicator?) {
    indicator?.text = "Installing ${item.vendor.vendor} ${item.version}..."

    validateInstallDir(targetDir)

    val url = Urls.parse(item.url, false) ?: error("Cannot parse download URL: ${item.url}")
    if (!url.scheme.equals("https", ignoreCase = true)) error("URL must use https:// protocol, but was: $url")

    indicator?.text2 = "Downloading $url"
    val downloadPath = File(PathManager.getTempPath(), "jdk-${item.installFolderName}")
    try {
      try {
        HttpRequests
          .request(item.url)
          .productNameAsUserAgent()
          .connect { processor -> processor.saveToFile(downloadPath, indicator) }

      }
      catch (t: IOException) {
        throw RuntimeException("Failed to download JDK from $url. ${t.message}", t)
      }

      val sizeDiff = downloadPath.length() - item.archiveSize
      if (sizeDiff != 0L) {
        throw RuntimeException("Downloaded JDK distribution has incorrect size, difference is ${sizeDiff.absoluteValue} bytes")
      }

      val actualHashCode = Files.asByteSource(downloadPath).hash(Hashing.sha256()).toString()
      if (!actualHashCode.equals(item.sha256, ignoreCase = true)) {
        throw RuntimeException("SHA-256 checksum does not match. Actual value is $actualHashCode, expected ${item.sha256}")
      }

      indicator?.isIndeterminate = true
      indicator?.text2 = "Unpacking"

      val decompressor = when (item.archiveType) {
        "zip" -> Decompressor.Zip(downloadPath)
        "targz" -> Decompressor.Tar(downloadPath)
        else -> error("Unsupported archiveType: ${item.archiveSize}")
      }
      //handle cancellation via postProcessor (instead of inheritance)
      decompressor.postprocessor { indicator?.checkCanceled() }
      decompressor.extract(targetDir)
    } catch (t: Throwable) {
      //if we were cancelled in the middle or failed, let's clean up
      FileUtil.delete(targetDir)
      throw t
    } finally {
      FileUtil.delete(downloadPath)
    }
  }
}
