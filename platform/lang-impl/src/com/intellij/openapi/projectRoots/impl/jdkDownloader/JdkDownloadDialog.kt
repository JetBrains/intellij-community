// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.*
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.text.VersionComparatorUtil
import java.awt.Component
import java.awt.event.ItemEvent
import java.nio.file.Path
import java.util.function.Function
import javax.swing.*
import javax.swing.event.DocumentEvent

class JdkDownloaderModel(
  val versionGroups: List<JdkVersionItem>,
  val defaultItem: JdkItem,
  val defaultVersion: JdkVersionItem,
  val defaultVersionVendor: JdkVersionVendorItem,
)

class JdkVersionItem(
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
    require(this.defaultSelectedItem in this.includedItems) { "Default selected item must be in the list of items for $jdkVersion" }

    val allItems = when {
      this.excludedItems.isNotEmpty() -> this.includedItems + JdkVersionVendorGroupSeparator + this.excludedItems
      else                            -> this.includedItems
    }

    DefaultComboBoxModel(allItems.toTypedArray()).also {
      it.selectedItem = defaultSelectedItem
    }
  }
}

sealed class JdkVersionVendorElement
object JdkVersionVendorGroupSeparator : JdkVersionVendorElement()

class JdkVersionVendorItem(
  val item: JdkItem
) : JdkVersionVendorElement() {
  var parent: JdkVersionItem? = null
  val selectItem get() = parent?.includedItems?.find { it.item == item } ?: this

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

        val jdkVersion = value.item.jdkVersion
        if (jdkVersion != value.parent?.jdkVersion) {
          append(" $jdkVersion", SimpleTextAttributes.GRAYED_ATTRIBUTES, false)
        }

        value.item.presentableArchIfNeeded?.let { archIfNeeded ->
          append("  $archIfNeeded", SimpleTextAttributes.GRAYED_ATTRIBUTES, false)
        }
      }
    }
  }
}

private fun List<JdkVersionVendorItem>.sortedForUI() = this.sortedBy { it.item.product.packagePresentationText.toLowerCase() }

fun buildJdkDownloaderModel(allItems: List<JdkItem>): JdkDownloaderModel {
  @NlsSafe
  fun JdkItem.versionGroupId() = this.presentableMajorVersionString

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
            .minWithOrNull(comparator)
          // if not, let's try an older version too
          ?: jdkItems
            .filter { it.jdkMajorVersion < majorVersion }
            .maxWithOrNull(comparator)

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

  val defaultJdkVersionItem = versionItems.firstOrNull { group -> group.includedItems.any { it.item == defaultItem } }
                              ?: error("Default item is not found in the list")

  val defaultVersionVendor = defaultJdkVersionItem.includedItems.find { it.item == defaultItem }
                             ?: defaultJdkVersionItem.includedItems.first()

  return JdkDownloaderModel(
    versionGroups = versionItems,
    defaultItem = defaultItem,
    defaultVersion = defaultJdkVersionItem,
    defaultVersionVendor = defaultVersionVendor,
  )
}

private val jdkVersionItemRenderer = object: ColoredListCellRenderer<JdkVersionItem>() {
  override fun customizeCellRenderer(list: JList<out JdkVersionItem>,
                                     value: JdkVersionItem?,
                                     index: Int,
                                     selected: Boolean,
                                     hasFocus: Boolean) {
    append(value?.jdkVersion ?: return, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }
}

internal class JdkDownloaderMergedModel(
  private val mainModel: JdkDownloaderModel,
  private val wslModel: JdkDownloaderModel?,
  val wslDistributions: List<WSLDistribution>,
  val projectWSLDistribution: WSLDistribution?
) {
  val hasWsl get() = wslModel != null

  fun selectModel(wsl: Boolean): JdkDownloaderModel = when {
    wsl && wslModel != null -> wslModel
    else -> mainModel
  }
}

internal class JdkDownloadDialog(
  val project: Project?,
  val parentComponent: Component?,
  val sdkType: SdkTypeId,
  val mergedModel: JdkDownloaderMergedModel,
) : DialogWrapper(project, parentComponent, false, IdeModalityType.PROJECT) {
  private val panel: JComponent
  private val versionComboBox : ComboBox<JdkVersionItem>
  private val vendorComboBox: JdkVersionVendorCombobox
  private val installDirTextField: TextFieldWithBrowseButton?
  private val installDirCombo: ComboBox<String>?
  private val installDirComponent: JComponent

  private var currentModel : JdkDownloaderModel? = null

  private lateinit var selectedItem: JdkItem
  private lateinit var selectedPath: String

  init {
    title = ProjectBundle.message("dialog.title.download.jdk")
    setResizable(false)

    versionComboBox = ComboBox()
    versionComboBox.renderer = jdkVersionItemRenderer
    versionComboBox.isSwingPopup = false

    vendorComboBox = JdkVersionVendorCombobox()

    if (mergedModel.hasWsl) {
      installDirCombo = ComboBox<String>()
      installDirCombo.isEditable = true
      installDirCombo.initBrowsableEditor(
        BrowseFolderRunnable(
          ProjectBundle.message("dialog.title.select.path.to.install.jdk"),
          null,
          project,
          FileChooserDescriptorFactory.createSingleFolderDescriptor(),
          installDirCombo,
          TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT
        ), disposable)
      installDirCombo.addActionListener {
        onTargetPathChanged(installDirCombo.editor.item as String)
      }
      installDirTextField = null
      installDirComponent = installDirCombo
    }
    else {
      installDirTextField = textFieldWithBrowseButton(
        project = project,
        browseDialogTitle = ProjectBundle.message("dialog.title.select.path.to.install.jdk"),
        fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
      )
      installDirTextField.onTextChange {
        onTargetPathChanged(it)
      }
      installDirTextField.textField.columns = 36
      installDirCombo = null
      installDirComponent = installDirTextField
    }

    vendorComboBox.onActionItemSelected(::onVendorActionItemSelected)
    vendorComboBox.onSelectionChange(::onVendorSelectionChange)
    versionComboBox.onSelectionChange(::onVersionSelectionChange)


    panel = panel {
      row(ProjectBundle.message("dialog.row.jdk.version")) { cell(versionComboBox).widthGroup("combo") }
      row(ProjectBundle.message("dialog.row.jdk.vendor")) { cell(vendorComboBox).widthGroup("combo").focused() }
      row(ProjectBundle.message("dialog.row.jdk.location")) { cell(installDirComponent) }
    }

    myOKAction.putValue(Action.NAME, ProjectBundle.message("dialog.button.download.jdk"))

    setModel(mergedModel.projectWSLDistribution != null)
    init()
  }

  private fun setModel(forWsl: Boolean) {
    val model = mergedModel.selectModel(forWsl)
    if (currentModel === model) return

    val prevSelectedVersion = versionComboBox.selectedItem as? JdkVersionItem
    val prevSelectedJdk = (vendorComboBox.selectedItem as? JdkVersionVendorItem)?.takeIf { it.canBeSelected }

    currentModel = model
    versionComboBox.model = DefaultComboBoxModel(model.versionGroups.toTypedArray())

    val newVersionItem = if (prevSelectedVersion != null) {
      model.versionGroups.singleOrNull { it.jdkVersion == prevSelectedVersion.jdkVersion }
    } else null

    val newVendorItem = if (newVersionItem != null && prevSelectedJdk != null) {
      (newVersionItem.includedItems + newVersionItem.excludedItems).singleOrNull {
          it.canBeSelected && it.item.suggestedSdkName == prevSelectedJdk.item.suggestedSdkName
        }
    } else null

    onVersionSelectionChange(newVersionItem ?: model.defaultVersion)
    onVendorSelectionChange(newVendorItem ?: model.defaultVersionVendor)
  }

  private fun onTargetPathChanged(path: String) {
    @Suppress("NAME_SHADOWING")
    val path = FileUtil.expandUserHome(path)
    selectedPath = path

    setModel(WslDistributionManager.isWslPath(path))
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
    val path = JdkInstaller.getInstance().defaultInstallDir(newVersion, mergedModel.projectWSLDistribution).toString()
    val relativePath = FileUtil.getLocationRelativeToUserHome(path)
    if (installDirTextField != null) {
      installDirTextField.text = relativePath
    }
    else {
      installDirCombo!!.model = CollectionComboBoxModel(getSuggestedInstallDirs(newVersion), relativePath)
    }
    selectedPath = path
    selectedItem = newVersion
  }

  private fun getSuggestedInstallDirs(newVersion: JdkItem): List<String> {
    return (listOf(null) + mergedModel.wslDistributions).mapTo(LinkedHashSet()) {
      JdkInstaller.getInstance().defaultInstallDir(newVersion, it).toString()
    }.map {
      FileUtil.getLocationRelativeToUserHome(it)
    }
  }

  private fun onVersionSelectionChange(it: JdkVersionItem?) {
    if (it == null) return
    versionComboBox.selectedItem = it
    vendorComboBox.model = it.model
  }

  override fun doValidate(): ValidationInfo? {
    super.doValidate()?.let { return it }

    val (_, error) = JdkInstaller.getInstance().validateInstallDir(selectedPath)
    return error?.let { ValidationInfo(error, installDirComponent) }
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

    JdkDownloaderLogger.logSelected(selectedItem)

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
