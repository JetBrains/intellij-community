// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkDownloader

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.ui.layout.*
import java.awt.Component
import java.awt.event.ItemEvent
import javax.swing.Action
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.event.DocumentEvent

class JdkDownloadDialog(
  val project: Project?,
  val parentComponent: Component?,
  val sdkType: SdkTypeId,
  val items: List<JdkItem>
) : DialogWrapper(project, parentComponent, false, IdeModalityType.PROJECT) {
  companion object {
    const val DIALOG_TITLE = "Download JDK"
  }

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
    vendorComboBox.renderer = object: ColoredListCellRenderer<JdkProduct>() {
      override fun customizeCellRenderer(list: JList<out JdkProduct>, value: JdkProduct?, index: Int, selected: Boolean, hasFocus: Boolean) {
        value ?: return
        append(value.packagePresentationText)
      }
    }
    vendorComboBox.isSwingPopup = false

    val versionModel = DefaultComboBoxModel<JdkItem>()
    val versionComboBox = ComboBox(versionModel)
    versionComboBox.renderer = object: ColoredListCellRenderer<JdkItem>() {
      override fun customizeCellRenderer(list: JList<out JdkItem>, value: JdkItem?, index: Int, selected: Boolean, hasFocus: Boolean) {
        value ?: return
        append(value.versionPresentationText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append(" ")
        append(value.downloadSizePresentationText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
    versionComboBox.isSwingPopup = false

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
      installDirTextField.text = JdkInstaller.defaultInstallDir(newVersion)
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
