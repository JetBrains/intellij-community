// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkDownloader

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.JdkDownloaderService
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.ui.layout.*
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

private const val DIALOG_TITLE = "Download JDK"

internal class JdkDownloaderUI : JdkDownloaderService() {
  private val LOG = logger<JdkDownloaderService>()

  override fun downloadOrSelectJdk(javaSdkType: JavaSdkImpl,
                                   sdkModel: SdkModel,
                                   parentComponent: JComponent,
                                   callback: Consumer<Sdk>) {
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))

    fun addSdkIfNotNull(jdkHome: String?) {
      if (jdkHome == null) return
      val sdk = (sdkModel as ProjectSdksModel).createSdk(javaSdkType, jdkHome)
      callback.consume(sdk)
    }

    ProgressManager.getInstance().run(object : Task.Modal(project, "Downloading JDK list...", true) {
      override fun run(indicator: ProgressIndicator) {
        val items = try {
          JdkListDownloader.downloadModel(progress = indicator)
        }
        catch (t: ProcessCanceledException) {
          return
        }
        catch (t: Exception) {
          LOG.warn(t.message, t)
          invokeLater {
            Messages.showMessageDialog(parentComponent,
                                       "Failed to download the list of installable JDKs. You could still locate installed JDK on the disk",
                                       DIALOG_TITLE,
                                       Messages.getErrorIcon()
            )

            val jdkHome = showJdkSelectorFromDisk(javaSdkType, parentComponent)
            addSdkIfNotNull(jdkHome)
          }
          return
        }

        indicator.checkCanceled()
        invokeLater {
          if (project?.isDisposed == true) return@invokeLater
          val jdkHome = DownloadJdkDialog(project, parentComponent, javaSdkType, items).selectOrDownloadAndUnpackJdk()
          addSdkIfNotNull(jdkHome)
        }
      }
    })
  }
}

private fun showJdkSelectorFromDisk(sdkType: SdkType, component: Component?): String? {
  var jdkHome: String? = null
  SdkConfigurationUtil.selectSdkHome(sdkType, component) {
    jdkHome = it
  }
  return jdkHome
}

private class DownloadJdkDialog(
  val project: Project?,
  val parentComponent: Component?,
  val sdkType: SdkType,
  val items: List<JdkItem>
) : DialogWrapper(project, parentComponent, false, IdeModalityType.PROJECT) {
  private val LOG = logger<DownloadJdkDialog>()

  private val panel: JComponent
  private var installDirTextField: TextFieldWithBrowseButton

  private val selectFromDiskAction = object : DialogWrapperAction("Find on the disk...") {
    override fun doAction(e: ActionEvent?) = doSelectFromDiskAction()
  }

  private lateinit var selectedItem: JdkItem
  private lateinit var selectedPath: String
  private lateinit var resultingJdkHome: String

  init {
    title = DIALOG_TITLE
    setResizable(false)

    val defaultItem = items.filter { it.isDefaultItem }.minBy { it } /*pick the newest default JDK */
                      ?: items.minBy { it } /* pick just the newest JDK is no default was set (aka the JSON is broken) */
                      ?: error("There must be at least one JDK to install") /* totally broken JSON */

    val vendorComboBox = ComboBox(items.map { it.product }.distinct().sorted().toTypedArray())
    vendorComboBox.selectedItem = defaultItem.product
    vendorComboBox.renderer = listCellRenderer { it, _, _ -> setText(it.packagePresentationText) }

    val versionModel = DefaultComboBoxModel<JdkItem>()
    val versionComboBox = ComboBox(versionModel)
    versionComboBox.renderer = listCellRenderer { it, _, _ -> setText(it.versionPresentationText) }

    fun selectVersions(newProduct: JdkProduct) {
      val newVersions = items.filter { it.product == newProduct }.sorted()
      versionModel.removeAllElements()
      for (version in newVersions) {
        versionModel.addElement(version)
      }
    }

    installDirTextField = textFieldWithBrowseButton(
      project = project,
      browseDialogTitle = "Select installation path for the JDK",
      fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    )

    fun selectInstallPath(newVersion: JdkItem) {
      val installFolderName = newVersion.installFolderName
      val path = when {
        SystemInfo.isLinux ->  "~/.jdks/$installFolderName"
        //see https://youtrack.jetbrains.com/issue/IDEA-206163#focus=streamItem-27-3270022.0-0
        SystemInfo.isMac ->  "~/Library/Java/JavaVirtualMachines/$installFolderName"
        SystemInfo.isWindows -> "${System.getProperty("user.home")}\\.jdks\\${installFolderName}"
        else -> error("Unsupported OS")
      }
      installDirTextField.text = path
      selectedItem = newVersion
    }

    vendorComboBox.onSelectionChange(::selectVersions)
    versionComboBox.onSelectionChange(::selectInstallPath)
    installDirTextField.onTextChange {
      selectedPath = it
    }

    panel = panel {
      row("Vendor:") { vendorComboBox.invoke() }
      row("Version:") { versionComboBox.invoke() }
      row("Location:") { installDirTextField.invoke() }
    }

    init()
    selectVersions(defaultItem.product)
  }

  override fun doValidate(): ValidationInfo? {
    super.doValidate()?.let { return it }

    val path = selectedPath
    val (_, error) = JdkInstaller.validateInstallDir(path)
    return error?.let { ValidationInfo(error, installDirTextField) }
  }

  override fun createActions() = arrayOf(selectFromDiskAction, *super.createActions())
  override fun createCenterPanel() = panel

  private fun doSelectFromDiskAction() {
    val jdkHome = showJdkSelectorFromDisk(sdkType, panel)
    if (jdkHome != null) {
      resultingJdkHome = jdkHome
      close(OK_EXIT_CODE)
    }
  }

  override fun doOKAction() {
    val installItem = selectedItem
    val installPath = selectedPath

    ProgressManager.getInstance().run(object : Task.Modal(project, "Installing JDK...", true) {
      override fun run(indicator: ProgressIndicator) {
        try {
          val targetDir = JdkInstaller.installJdk(installItem, installPath, indicator)
          invokeLater {
            resultingJdkHome = targetDir.absolutePath
            superDoOKAction()
          }
        } catch (t: ProcessCanceledException) {
          return
        } catch (e: Exception) {
          LOG.warn("Failed to install JDK $installItem to $installPath. ${e.message}", e)
          invokeLater {
            Messages.showMessageDialog(panel,
                                       "Failed to install JDK. ${e.message}",
                                       DIALOG_TITLE,
                                       Messages.getErrorIcon()
            )
          }
        }
      }
    })
  }

  private fun superDoOKAction() = super.doOKAction()

  // returns unpacked JDK location (if any) or null if cancelled
  fun selectOrDownloadAndUnpackJdk(): String? = when {
    showAndGet() -> resultingJdkHome
    else -> null
  }

  private inline fun TextFieldWithBrowseButton.onTextChange(crossinline action: (String) -> Unit) {
    textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        action(text)
      }
    })
  }

  private inline fun <reified T> ComboBox<T>.onSelectionChange(crossinline action: (T) -> Unit) {
    this.addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED) action(e.item as T)
    }
  }
}

