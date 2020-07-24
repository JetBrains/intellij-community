// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.ui.layout.*
import com.intellij.util.text.VersionComparatorUtil
import java.awt.Component
import java.awt.event.ItemEvent
import java.io.File
import javax.swing.Action
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.event.DocumentEvent

internal class JdkDownloadDialog(
  val project: Project?,
  val parentComponent: Component?,
  val sdkType: SdkTypeId,
  val items: List<JdkItem>
) : DialogWrapper(project, parentComponent, false, IdeModalityType.PROJECT) {
  private val panel: JComponent
  private var installDirTextField: TextFieldWithBrowseButton

  private lateinit var selectedItem: JdkItem
  private lateinit var selectedPath: String

  private class JdkVersionItem(val jdkVersion: String, items: List<JdkItem>) : Comparable<JdkVersionItem> {
    val items = items.map { JdkVersionVendorItem(this, it) }

    //we reuse model to keep selected element in-memory!
    val model = DefaultComboBoxModel(this.items.toTypedArray()).also {
      it.selectedItem = it.getElementAt(0)
    }

    override fun compareTo(other: JdkVersionItem): Int = VersionComparatorUtil.COMPARATOR.compare(other.jdkVersion, this.jdkVersion)
  }

  private data class JdkVersionVendorItem(val versionItem : JdkVersionItem, val item : JdkItem)

  init {
    title = ProjectBundle.message("dialog.title.download.jdk")
    setResizable(false)

    val jdkVersions = items.groupBy { it.jdkVersion }.entries.map { JdkVersionItem(it.key, it.value) }.sorted()

    val defaultItem = items.firstOrNull { it.isDefaultItem } /*pick the newest default JDK */
                      ?: items.firstOrNull() /* pick just the newest JDK is no default was set (aka the JSON is broken) */
                      ?: error("There must be at least one JDK to install") /* totally broken JSON */

    val defaultJdkVersionItem = jdkVersions.firstOrNull { it.jdkVersion == defaultItem.jdkVersion }

    val versionComboBox = ComboBox(jdkVersions.toTypedArray())
    versionComboBox.renderer = object: ColoredListCellRenderer<JdkVersionItem>() {
      override fun customizeCellRenderer(list: JList<out JdkVersionItem>, value: JdkVersionItem?, index: Int, selected: Boolean, hasFocus: Boolean) {
        append(value?.jdkVersion ?: "??", SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
    }
    versionComboBox.isSwingPopup = false

    val vendorComboBox = ComboBox(arrayOf<JdkVersionVendorItem>())
    vendorComboBox.renderer = object: ColoredListCellRenderer<JdkVersionVendorItem>() {
      override fun customizeCellRenderer(list: JList<out JdkVersionVendorItem>, value: JdkVersionVendorItem?, index: Int, selected: Boolean, hasFocus: Boolean) {
        append(value?.item?.product?.packagePresentationText ?: "??", SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
    }
    vendorComboBox.isSwingPopup = false

    installDirTextField = textFieldWithBrowseButton(
      project = project,
      browseDialogTitle = ProjectBundle.message("dialog.title.select.path.to.install.jdk"),
      fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    )

    fun onVendorSelectionChange(it: JdkVersionVendorItem?) {
      // the combobox does not call this event if no actual change was made,
      // we use it to simplify code below and ensure the update and callback were executed
      vendorComboBox.selectedItem = it

      if (it == null) return
      val newVersion = it.item
      installDirTextField.text = JdkInstaller.getInstance().defaultInstallDir(newVersion).toString()
      selectedItem = newVersion
    }

    fun onVersionSelectionChange(it: JdkVersionItem?) {
      // the combobox does not call this event if no actual change was made,
      // we use it to simplify code below and ensure the update and callback were executed
      versionComboBox.selectedItem = it

      if (it == null) return
      vendorComboBox.model = it.model
      onVendorSelectionChange(vendorComboBox.selectedItem as? JdkVersionVendorItem)
    }

    vendorComboBox.onSelectionChange(::onVendorSelectionChange)
    versionComboBox.onSelectionChange(::onVersionSelectionChange)

    installDirTextField.onTextChange { selectedPath = it }

    panel = panel {
      row(ProjectBundle.message("dialog.row.jdk.version")) { versionComboBox.invoke().sizeGroup("combo") }
      row(ProjectBundle.message("dialog.row.jdk.vendor")) { vendorComboBox.invoke().sizeGroup("combo").focused() }
      row(ProjectBundle.message("dialog.row.jdk.location")) { installDirTextField.invoke() }
    }

    myOKAction.putValue(Action.NAME, ProjectBundle.message("dialog.button.download.jdk"))

    init()
    onVersionSelectionChange(defaultJdkVersionItem)
    onVendorSelectionChange(defaultJdkVersionItem?.items?.find { it.item.isDefaultItem } ?: defaultJdkVersionItem?.items?.firstOrNull())
  }

  override fun doValidate(): ValidationInfo? {
    super.doValidate()?.let { return it }

    val (_, error) = JdkInstaller.getInstance().validateInstallDir(selectedPath)
    return error?.let { ValidationInfo(error, installDirTextField) }
  }

  override fun createCenterPanel() = panel

  fun selectJdkAndPath(): Pair<JdkItem, File>? {
    if (!showAndGet()) return null

    val (selectedFile) = JdkInstaller.getInstance().validateInstallDir(selectedPath)
    if (selectedFile == null) return null
    return selectedItem to selectedFile
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
