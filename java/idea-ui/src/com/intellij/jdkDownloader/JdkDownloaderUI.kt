// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkDownloader

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.ui.layout.*
import java.awt.Component
import java.awt.event.ItemEvent
import java.util.function.Consumer
import javax.swing.Action
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.event.DocumentEvent

private const val DIALOG_TITLE = "Download JDK"

internal class JdkDownloaderUI: SdkDownload {
  private val LOG = logger<JdkDownloaderUI>()

  override fun supportsDownload(sdkTypeId: SdkTypeId) = sdkTypeId is JavaSdkImpl

  override fun showDownloadUI(sdkTypeId: SdkTypeId,
                              sdkModel: SdkModel,
                              parentComponent: JComponent,
                              selectedSdk: Sdk?,
                              sdkCreatedCallback: Consumer<Sdk>) {

    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))
    if (project?.isDisposed == true) return
    val items = downloadModelWithProgress(project, parentComponent) ?: return

    if (project?.isDisposed == true) return
    val (jdkItem, jdkHome) = JdkDownloadDialog(project, parentComponent, sdkTypeId, items).selectJdkAndPath() ?: return

    /// prepare the JDK to be installed (e.g. create home dir, write marker file
    val request = prepareJdkInstall(project, parentComponent, jdkItem, jdkHome) ?: return
    sdkTypeId as JavaSdkImpl
    val sdk = JdkInstaller.createSdk(sdkModel, sdkTypeId, request)
    sdkCreatedCallback.accept(sdk)
  }

  //
  //  ProgressManager.getInstance().run(mkdirsTask)
  //
  //
  //  callback.consume(object: InstallableSdk() {
  //    override fun getSdkType() = javaSdkType
  //
  //    override fun getName() = jdkItem.fullPresentationText //TODO
  //
  //    override fun clone(): InstallableSdk = this
  //
  //    override fun getVersionString() = jdkItem.versionPresentationText
  //
  //    override fun prepareSdk(indicator: ProgressIndicator): Sdk {
  //      //TODO: how exception is handled outside?
  //      val actualHome = try {
  //        JdkInstaller.installJdk(jdkItem, jdkHome, indicator)
  //      } catch (t: ProcessCanceledException) {
  //        throw t
  //      } catch (e: Exception) {
  //        LOG.warn("Failed to install JDK $jdkItem to $jdkHome. ${e.message}", e)
  //        throw RuntimeException("Failed to install JDK. ${e.message}", e)
  //      }
  //      return (sdkModel as ProjectSdksModel).createSdk(javaSdkType, actualHome.absolutePath)
  //    }
  //  })
  //}

  private fun prepareJdkInstall(project: Project?, parentComponent: JComponent, jdkItem: JdkItem, jdkHome: String) : JdkInstallRequest? {
    val task = object : Task.WithResult<JdkInstallRequest?, Exception>(project, "Preparing JDK target folder...", true) {
      override fun compute(indicator: ProgressIndicator): JdkInstallRequest? {
        try {
          return JdkInstaller.prepareJdkInstallation(jdkItem, jdkHome)
        } catch (e: ProcessCanceledException) {
          throw e
        } catch (e: Exception) {
          //TODO[jo]: handle error to UI
          val msg = "Failed to prepare JDK installation to $jdkHome for ${jdkItem.fullPresentationText}. ${e.message}"
          LOG.warn(msg, e)
          invokeLater {
            //TODO[jo]: add message from an exception here
            Messages.showMessageDialog(parentComponent,
                                       msg,
                                       DIALOG_TITLE,
                                       Messages.getErrorIcon()
            )
          }
          return null
        }
      }
    }

    return ProgressManager.getInstance().run(task)
  }

  private fun downloadModelWithProgress(project: Project?, parentComponent: JComponent): List<JdkItem>? {
    val task = object : Task.WithResult<List<JdkItem>?, Exception>(project, "Downloading the list of available JDKs...", true) {
      override fun compute(indicator: ProgressIndicator): List<JdkItem>? {
        try {
          return JdkListDownloader.downloadModel(progress = indicator)
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (t: Exception) {
          LOG.warn(t.message, t)
          invokeLater {
            //TODO[jo]: add message from an exception here
            Messages.showMessageDialog(parentComponent,
                                       "Failed to download the list of installable JDKs",
                                       DIALOG_TITLE,
                                       Messages.getErrorIcon()
            )
          }
          return null
        }
      }
    }

    return ProgressManager.getInstance().run(task)
  }
}

private class JdkDownloadDialog(
  val project: Project?,
  val parentComponent: Component?,
  val sdkType: SdkTypeId,
  val items: List<JdkItem>
) : DialogWrapper(project, parentComponent, false, IdeModalityType.PROJECT) {
  private val LOG = logger<JdkDownloadDialog>()

  private val panel: JComponent
  private var installDirTextField: TextFieldWithBrowseButton

  private lateinit var selectedItem: JdkItem
  private lateinit var selectedPath: String

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
    versionComboBox.renderer = object: ColoredListCellRenderer<JdkItem>() {
      override fun customizeCellRenderer(list: JList<out JdkItem>, value: JdkItem, index: Int, selected: Boolean, hasFocus: Boolean) {
        append(value.versionPresentationText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append(" ")
        append(value.downloadSizePresentationText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }

    fun selectVersions(newProduct: JdkProduct) {
      val newVersions = items.filter { it.product == newProduct }.sorted()
      versionModel.removeAllElements()
      for (version in newVersions) {
        versionModel.addElement(version)
      }
    }

    installDirTextField = textFieldWithBrowseButton(
      project = project,
      browseDialogTitle = "Select Path to Install JDK",
      fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    )

    fun selectInstallPath(newVersion: JdkItem) {
      val installFolderName = newVersion.installFolderName
      val home = FileUtil.toCanonicalPath(System.getProperty("user.home") ?: ".")
      val path = when {
        SystemInfo.isLinux ->  "$home/.jdks/$installFolderName"
        //see https://youtrack.jetbrains.com/issue/IDEA-206163#focus=streamItem-27-3270022.0-0
        SystemInfo.isMac ->  "$home/Library/Java/JavaVirtualMachines/$installFolderName"
        SystemInfo.isWindows -> "$home\\.jdks\\${installFolderName}"
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
      row("Vendor:") { vendorComboBox.invoke().sizeGroup("combo").focused() }
      row("Version:") { versionComboBox.invoke().sizeGroup("combo") }
      row("Location:") { installDirTextField.invoke() }
    }

    myOKAction.putValue(Action.NAME, "Download")

    init()
    selectVersions(defaultItem.product)
  }

  override fun doValidate(): ValidationInfo? {
    super.doValidate()?.let { return it }

    val path = selectedPath
    val (_, error) = JdkInstaller.validateInstallDir(path)
    return error?.let { ValidationInfo(error, installDirTextField) }
  }

  override fun createCenterPanel() = panel

  // returns unpacked JDK location (if any) or null if cancelled
  fun selectJdkAndPath() = when {
    showAndGet() -> selectedItem to selectedPath //TODO: validate the path!
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
