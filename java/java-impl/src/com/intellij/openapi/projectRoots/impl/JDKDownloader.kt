// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.ui.layout.*
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.ItemEvent
import java.io.File
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class JDKDownloader {
  companion object {
    @JvmStatic
    fun getInstance(): JDKDownloader? = if (!isEnabled) null else ApplicationManager.getApplication().getService(JDKDownloader::class.java)

    private val LOG = logger<JDKDownloader>()

    @JvmStatic
    val isEnabled
      get() = Registry.`is`("jdk.downloader.ui")
  }

  fun showCustomCreateUI(javaSdk: JavaSdkImpl,
                         sdkModel: SdkModel,
                         parentComponent: JComponent,
                         selectedSdk: Sdk?,
                         sdkCreatedCallback: Consumer<Sdk>) {
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent)) ?: return

    ProgressManager.getInstance().run(object : Task.Modal(project, "Downloading JDK list...", true) {
      override fun run(indicator: ProgressIndicator) {
        val model = JDKListDownloader.downloadModel(progress = indicator)
        if (model.feedError != null) return /*Handle error?*/

        invokeLater {
          if (project.isDisposedOrDisposeInProgress) return@invokeLater
          SelectOrDownloadJDKDialog(project, parentComponent, model.items).showDialog()
        }
      }
    })
  }
}

private data class JDKInstallRequest(val item: JDKDownloadItem,
                                     val targetDir: File)

private class SelectOrDownloadJDKDialog(
  val project: Project,
  val parentComponent: Component?,
  val items: List<JDKDownloadItem>
): DialogWrapper(project, parentComponent, false, IdeModalityType.PROJECT) {

  private val panel : JComponent
  private val diskButton = DialogWrapperExitAction("Find on the disk...", NEXT_USER_EXIT_CODE)

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
      //TODO: should not be changed if updated by user
      //TODO: default for Windows!
      installDirTextField.text = "~/.jdks/${newVersion.installFolderName}"
    }
    selectInstallPath(defaultItem)

    vendorComboBox.onSelectionChange(::selectVersions)
    versionComboBox.onSelectionChange(::selectInstallPath)

    panel = panel {
      row("Vendor:") { vendorComboBox.invoke() }
      row("Version:") { versionComboBox.invoke() }
      row("Install JDK to:") { installDirTextField.invoke() }
    }

    init()
  }

  override fun createActions() = arrayOf(diskButton, *super.createActions())
  override fun createCenterPanel() = panel

  fun showDialog() : JDKInstallRequest? {
    show()
    return null
  }

  private inline fun <reified T> ComboBox<T>.onSelectionChange(crossinline action: (T) -> Unit) {
    this.addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED) action(e.item as T)
    }
  }
}
