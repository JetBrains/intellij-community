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
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.ui.layout.*
import com.intellij.util.text.VersionComparatorUtil
import java.awt.Component
import java.awt.event.ItemEvent
import java.nio.file.Path
import java.util.*
import java.util.function.Function
import javax.swing.*
import javax.swing.event.DocumentEvent

private class JdkDownloaderModel(
  val versionGroups : List<JdkVersionItem>,
  val defaultItem: JdkItem,
  val defaultVersion: JdkVersionItem,
  val defaultVersionVendor: JdkVersionVendorItem
)

private class JdkVersionItem(
  @NlsSafe
  val jdkVersion: String,
  /* we should prefer the default selected item from the JDKs.json feed,
   * the list below is sorted by vendor, and default item is not necessarily first
   */
  val defaultSelectedItem: JdkVersionVendorItem,
  val includedItems: List<JdkVersionVendorItem>,
  val excludedItems: List<JdkVersionVendorItem>
)  {
  //we reuse model to keep selected element in-memory!
  val model: ComboBoxModel<JdkVersionVendorElement> by lazy {
    require(this.includedItems.isNotEmpty()) { "No included items for $jdkVersion" }
    require(this.defaultSelectedItem in this.includedItems) { "Dedfault selected item must be in the list of items for $jdkVersion" }

    val allItems = when {
      this.excludedItems.isNotEmpty() -> this.includedItems + JdkVersionVendorGroupSeparator + this.excludedItems
      else                            -> this.includedItems
    }

    DefaultComboBoxModel(allItems.toTypedArray()).also {
      it.selectedItem = defaultSelectedItem
    }
  }
}

private sealed class JdkVersionVendorElement
private object JdkVersionVendorGroupSeparator : JdkVersionVendorElement()

private class JdkVersionVendorItem(
  val item: JdkItem
) : JdkVersionVendorElement() {
  var parent: JdkVersionItem? = null
  val selectItem get() = parent?.includedItems?.find { it.item == item } ?: this

  val showItemVersion: Boolean get() = parent != null
  val canBeSelected: Boolean get() = parent == null
}

private class JdkVersionVendorCombobox: ComboBox<JdkVersionVendorElement>() {
  private val myActionItemSelectedListeners = mutableListOf<(item: JdkVersionVendorItem) -> Unit>()

  override fun setSelectedItem(anObject: Any?) {
    if (anObject !is JdkVersionVendorItem || selectedItem === anObject) return

    if (anObject.canBeSelected) {
      super.setSelectedItem(anObject)
    } else {
      myActionItemSelectedListeners.forEach { it(anObject) }
    }
  }

  // the listener is called for all JdkVersionVendorItem, event for non-selectable ones
  fun onActionItemSelected(action: (item: JdkVersionVendorItem) -> Unit) {
    myActionItemSelectedListeners += action
  }

  override fun setModel(aModel: ComboBoxModel<JdkVersionVendorElement>?) {
    if (model === aModel) return

    super.setModel(aModel)
    //change of data model does not file selected item change, which we'd like to receive
    selectedItemChanged()
  }

  init {
    isSwingPopup = false

    renderer = object: ColoredListCellRenderer<JdkVersionVendorElement>() {
      override fun getListCellRendererComponent(list: JList<out JdkVersionVendorElement>?,
                                                value: JdkVersionVendorElement?,
                                                index: Int,
                                                selected: Boolean,
                                                hasFocus: Boolean): Component {
        if (value === JdkVersionVendorGroupSeparator ) {
          return SeparatorWithText().apply { caption = ProjectBundle.message("dialog.row.jdk.other.versions") }
        }

        return super.getListCellRendererComponent(list, value, index, selected, hasFocus)
      }

      override fun customizeCellRenderer(list: JList<out JdkVersionVendorElement>, value: JdkVersionVendorElement?, index: Int, selected: Boolean, hasFocus: Boolean) {
        if (value !is JdkVersionVendorItem) {
          append("???")
          return
        }

        append(value.item.product.packagePresentationText, SimpleTextAttributes.REGULAR_ATTRIBUTES)

        if (value.showItemVersion) {
          append(" " + value.item.jdkVersion, SimpleTextAttributes.GRAYED_ATTRIBUTES, false)
        }
      }
    }
  }
}

private fun List<JdkVersionVendorItem>.sortedForUI() = this.sortedBy { it.item.product.packagePresentationText.toLowerCase() }

private fun buildJdkDownloaderModel(allItems: List<JdkItem>): JdkDownloaderModel {
  @NlsSafe
  fun JdkItem.versionGroupId() = this.jdkVersion

  val groups =  allItems
    .groupBy { it.versionGroupId() }
    .mapValues { (jdkVersion, groupItems) ->
      val majorVersion = groupItems.first().jdkMajorVersion

      val includedItems = groupItems
        .map { JdkVersionVendorItem(item = it) }

      val includedProducts = groupItems.map { it.product }.toHashSet()

      val excludedItems = allItems
        .asSequence()
        .filter { it.product !in includedProducts }
        .filter { it !in groupItems }
        .groupBy { it.product }
        .mapValues { (_, jdkItems) ->
          val comparator = Comparator.comparing(Function<JdkItem, String> { it.jdkVersion }, VersionComparatorUtil.COMPARATOR)
          //first try to find closest newer version
          jdkItems
            .filter { it.jdkMajorVersion >= majorVersion }
            .minWith(comparator)
          // if not, let's try an older version too
          ?: jdkItems
            .filter { it.jdkMajorVersion < majorVersion }
            .maxWith(comparator)

        }
        //we assume the initial order of feed items contains vendors in the right order
        .mapNotNull { it.value }
        .map { JdkVersionVendorItem(item = it) }

      JdkVersionItem(jdkVersion,
                     includedItems.firstOrNull() ?: error("Empty group of includeItems for $jdkVersion"),
                     includedItems.sortedForUI(),
                     excludedItems.sortedForUI())
    }

  //assign parent relation
  groups.values.forEach { parent -> parent.excludedItems.forEach { it.parent = groups.getValue(it.item.versionGroupId()) } }

  val versionItems = groups.values
    .sortedWith(Comparator.comparing(Function<JdkVersionItem, String> { it.jdkVersion }, VersionComparatorUtil.COMPARATOR).reversed())

  val defaultItem = allItems.firstOrNull { it.isDefaultItem } /*pick the newest default JDK */
                    ?: allItems.firstOrNull() /* pick just the newest JDK is no default was set (aka the JSON is broken) */
                    ?: error("There must be at least one JDK to install") /* totally broken JSON */

  val defaultJdkVersionItem = versionItems.first { it.jdkVersion == defaultItem.jdkVersion }

  val defaultVersionVendor = defaultJdkVersionItem.includedItems.find { it.item == defaultItem }
                             ?: defaultJdkVersionItem.includedItems.first()

  return JdkDownloaderModel(
    versionGroups = versionItems,
    defaultItem = defaultItem,
    defaultVersion = defaultJdkVersionItem,
    defaultVersionVendor = defaultVersionVendor
  )
}

private val jdkVersionItemRenderer = object: ColoredListCellRenderer<JdkVersionItem>() {
  override fun customizeCellRenderer(list: JList<out JdkVersionItem>, value: JdkVersionItem?, index: Int, selected: Boolean, hasFocus: Boolean) {
    append(value?.jdkVersion ?: return, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }
}

internal class JdkDownloadDialog(
  val project: Project?,
  val parentComponent: Component?,
  val sdkType: SdkTypeId,
  val items: List<JdkItem>
) : DialogWrapper(project, parentComponent, false, IdeModalityType.PROJECT) {
  private val panel: JComponent
  private val versionComboBox : ComboBox<JdkVersionItem>
  private val vendorComboBox: JdkVersionVendorCombobox
  private val installDirTextField: TextFieldWithBrowseButton

  private lateinit var selectedItem: JdkItem
  private lateinit var selectedPath: String

  init {
    title = ProjectBundle.message("dialog.title.download.jdk")
    setResizable(false)

    val model = buildJdkDownloaderModel(items)
    versionComboBox = ComboBox(model.versionGroups.toTypedArray())
    versionComboBox.renderer = jdkVersionItemRenderer
    versionComboBox.isSwingPopup = false

    vendorComboBox = JdkVersionVendorCombobox()

    installDirTextField = textFieldWithBrowseButton(
      project = project,
      browseDialogTitle = ProjectBundle.message("dialog.title.select.path.to.install.jdk"),
      fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    )

    vendorComboBox.onActionItemSelected(::onVendorActionItemSelected)
    vendorComboBox.onSelectionChange(::onVendorSelectionChange)
    versionComboBox.onSelectionChange(::onVersionSelectionChange)

    installDirTextField.onTextChange {
      selectedPath = FileUtil.expandUserHome(it)
    }

    panel = panel {
      row(ProjectBundle.message("dialog.row.jdk.version")) { versionComboBox.invoke().sizeGroup("combo") }
      row(ProjectBundle.message("dialog.row.jdk.vendor")) { vendorComboBox.invoke().sizeGroup("combo").focused() }
      row(ProjectBundle.message("dialog.row.jdk.location")) { installDirTextField.invoke() }
    }

    myOKAction.putValue(Action.NAME, ProjectBundle.message("dialog.button.download.jdk"))

    init()
    onVersionSelectionChange(model.defaultVersion)
    onVendorSelectionChange(model.defaultVersionVendor)
  }

  private fun onVendorActionItemSelected(it: JdkVersionVendorElement?) {
    if (it !is JdkVersionVendorItem) return
    val parent = it.parent ?: return
    onVersionSelectionChange(parent)
    vendorComboBox.selectedItem = it.selectItem
  }

  private fun onVendorSelectionChange(it: JdkVersionVendorElement?) {
    if (it !is JdkVersionVendorItem || !it.canBeSelected) return

    vendorComboBox.selectedItem = it.selectItem
    val newVersion = it.item
    val path = JdkInstaller.getInstance().defaultInstallDir(newVersion).toString()
    installDirTextField.text = FileUtil.getLocationRelativeToUserHome(path)
    selectedPath = path
    selectedItem = newVersion
  }

  private fun onVersionSelectionChange(it: JdkVersionItem?) {
    if (it == null) return
    versionComboBox.selectedItem = it
    vendorComboBox.model = it.model
  }

  override fun doValidate(): ValidationInfo? {
    super.doValidate()?.let { return it }

    val (_, error) = JdkInstaller.getInstance().validateInstallDir(selectedPath)
    return error?.let { ValidationInfo(error, installDirTextField) }
  }

  override fun createCenterPanel() = panel

  fun selectJdkAndPath(): Pair<JdkItem, Path>? {
    if (!showAndGet()) {
      return null
    }

    val (selectedFile) = JdkInstaller.getInstance().validateInstallDir(selectedPath)
    if (selectedFile == null) {
      return null
    }
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
