// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.provider.localEel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.system.CpuArch
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.event.ItemEvent
import java.nio.file.Path
import java.util.function.Function
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.event.DocumentEvent

@Internal
class JdkDownloaderModel(
  val versionGroups: List<JdkVersionItem>,
  val defaultItem: JdkItem,
  val defaultVersion: JdkVersionItem,
  val defaultVersionVendor: JdkVersionVendorItem,
)

@Internal
class JdkVersionItem(
  val jdkVersion: @NlsSafe String,
  /* we should prefer the default selected item from the JDKs.json feed,
   * the list below is sorted by vendor, and default item is not necessarily first
   */
  private val defaultSelectedItem: JdkVersionVendorItem,
  val includedItems: List<JdkVersionVendorItem>,
  val excludedItems: List<JdkVersionVendorItem>
)  {
  //we reuse model to keep selected element in-memory!
  val model: ComboBoxModel<JdkVersionVendorItem> by lazy {
    require(this.includedItems.isNotEmpty()) { "No included items for $jdkVersion" }
    require(this.defaultSelectedItem in this.includedItems) { "Default selected item must be in the list of items for $jdkVersion" }

    val allItems = when {
      this.excludedItems.isNotEmpty() -> this.includedItems + this.excludedItems
      else                            -> this.includedItems
    }

    DefaultComboBoxModel(allItems.toTypedArray()).also {
      it.selectedItem = defaultSelectedItem
    }
  }
}

@Internal
class JdkVersionVendorItem(
  val item: JdkItem
) {
  var parent: JdkVersionItem? = null
  val selectItem: JdkVersionVendorItem get() = parent?.includedItems?.find { it.item == item } ?: this

  val canBeSelected: Boolean get() = parent == null
}

private class JdkVersionVendorCombobox: ComboBox<JdkVersionVendorItem>() {
  var itemWithSeparator: JdkVersionVendorItem? = null
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

  override fun setModel(aModel: ComboBoxModel<JdkVersionVendorItem>?) {
    if (model === aModel) return

    super.setModel(aModel)
    //change of data model does not file selected item change, which we'd like to receive
    selectedItemChanged()
  }

  init {
    isSwingPopup = false

    renderer = listCellRenderer<JdkVersionVendorItem>("") {
      text(value.item.product.packagePresentationText)

      text(value.item.jdkVersion) {
        foreground = greyForeground
      }

      value.item.presentableArchIfNeeded?.let {
        text(it) {
          foreground = greyForeground
        }
      }

      if (itemWithSeparator == value) {
        separator {
          text = ProjectBundle.message("dialog.row.jdk.other.versions")
        }
      }
    }
  }
}

private fun List<JdkVersionVendorItem>.sortedForUI() = this.sortedBy { it.item.product.packagePresentationText.lowercase() }

@VisibleForTesting
@Internal
fun buildJdkDownloaderModel(allItems: List<JdkItem>, eel: EelApi, itemFilter: (JdkItem) -> Boolean = { true }): JdkDownloaderModel {
  @NlsSafe
  fun JdkItem.versionGroupId() = this.presentableMajorVersionString

  val availableItems = allItems
    .filter { itemFilter.invoke(it) }
    .filter { Registry.`is`("jdk.downloader.show.other.arch", false) || CpuArch.fromString(it.arch) == CpuArch.CURRENT }

  val groups = availableItems
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
        .filter { EelPlatform.resolveArch(it.arch) == eel.platform.arch || Registry.`is`("jdk.downloader.show.other.arch", false) }
        .groupBy { it.product }
        .mapValues { (_, jdkItems) ->
          val comparator = Comparator.comparing(Function<JdkItem, String> { it.jdkVersion }, VersionComparatorUtil.COMPARATOR)
          // first try to find the closest newer version
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

      val defaultSelectedItem = includedItems.firstOrNull { it.item.isDefaultItem }
                                ?: includedItems.firstOrNull { !it.item.isPreview }
                                ?: includedItems.firstOrNull()

      JdkVersionItem(jdkVersion,
                     defaultSelectedItem ?: error("Empty group of includeItems for $jdkVersion"),
                     includedItems.sortedForUI(),
                     excludedItems.sortedForUI())
    }

  // assign the parent relation
  groups.values.forEach { parent -> parent.excludedItems.forEach { it.parent = groups[it.item.versionGroupId()] } }

  val versionItems = groups.values
    .sortedWith(Comparator.comparing(Function<JdkVersionItem, String> { it.jdkVersion }, VersionComparatorUtil.COMPARATOR).reversed())

  val latestVersion = availableItems.filter { !it.isPreview }.maxByOrNull { it.jdkMajorVersion }?.jdkMajorVersion
  val defaultItem = availableItems    /* pick the newest OpenJDK */
                      .filter { it.isDefaultItem }
                      .maxByOrNull { it.jdkMajorVersion }
                    ?: availableItems /* pick a "lightweight" non-preview JDK if no default option is available */
                      .filter { it.jdkMajorVersion == latestVersion && !it.isPreview }
                      .minByOrNull { it.archiveSize }
                    ?: availableItems.firstOrNull() /* strange case, e.g., only preview JDKs aren't filtered */
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

internal class JdkDownloadDialog(
  val project: Project?,
  val parentComponent: Component?,
  val sdkType: SdkTypeId,
  private val eel: EelApi,
  private val model: JdkDownloaderModel,
  okActionText: @NlsContexts.Button String = ProjectBundle.message("dialog.button.download.jdk"),
  val text: @Nls String? = null
) : DialogWrapper(project, parentComponent, false, IdeModalityType.IDE) {
  private lateinit var versionComboBox : ComboBox<JdkVersionItem>
  private val vendorComboBox: JdkVersionVendorCombobox = JdkVersionVendorCombobox()

  private lateinit var installDirTextField: TextFieldWithBrowseButton

  private var currentModel : JdkDownloaderModel? = null

  private lateinit var selectedItem: JdkItem
  private lateinit var selectedPath: String

  private val panel: DialogPanel = panel {
    if (text != null) {
      row {
        label(text)
      }
    }

    var archiveSizeCell: Cell<*>? = null

    row(ProjectBundle.message("dialog.row.jdk.version")) {
      versionComboBox = comboBox(listOf<JdkVersionItem>().toMutableList(), textListCellRenderer { it?.jdkVersion })
        .align(AlignX.FILL)
        .component
    }
    row(ProjectBundle.message("dialog.row.jdk.vendor")) {
      cell(vendorComboBox)
        .align(AlignX.FILL)
        .focused()
        .validationInfo {
          val itemArch = EelPlatform.resolveArch(it.item.item.arch)
          when {
            itemArch != eel.platform.arch -> warning(ProjectBundle.message("dialog.jdk.arch.validation", itemArch, eel.platform.arch))
            it.item.item.isPreview -> warning(ProjectBundle.message("dialog.jdk.preview.validation"))
            else -> null
          }
        }
        .apply {
          vendorComboBox.onSelectionChange {
            archiveSizeCell?.comment?.text = ProjectBundle.message("dialog.jdk.archive.size", it.item.archiveSizeInMB)
          }
        }
    }
    row(ProjectBundle.message("dialog.row.jdk.location")) {
      installDirTextField = textFieldWithBrowseButton(
        project,
        FileChooserDescriptorFactory.singleDir().withTitle(ProjectBundle.message("dialog.title.select.path.to.install.jdk"))
      ).apply {
        onTextChange { onTargetPathChanged(it) }
        textField.columns = 36
      }
      cell(installDirTextField)
        .align(AlignX.FILL)
        .apply { archiveSizeCell = comment("") }
    }
  }

  init {
    title = ProjectBundle.message("dialog.title.download.jdk")
    isResizable = false

    vendorComboBox.onActionItemSelected(::onVendorActionItemSelected)
    vendorComboBox.onSelectionChange(::onVendorSelectionChange)
    versionComboBox.onSelectionChange(::onVersionSelectionChange)

    setOKButtonText(okActionText)

    currentModel = model
    versionComboBox.model = DefaultComboBoxModel(model.versionGroups.toTypedArray())
    onVersionSelectionChange(model.defaultVersion)
    onVendorSelectionChange(model.defaultVersionVendor)
    init()
  }

  private fun onTargetPathChanged(path: String) {
    @Suppress("NAME_SHADOWING")
    val path = OSAgnosticPathUtil.expandUserHome(path)
    selectedPath = path
    initValidation()
  }

  private fun onVendorActionItemSelected(it: JdkVersionVendorItem?) {
    if (it == null) return
    val parent = it.parent ?: return
    onVersionSelectionChange(parent)
    vendorComboBox.selectedItem = it.selectItem
  }

  private fun onVendorSelectionChange(it: JdkVersionVendorItem?) {
    if (it == null || !it.canBeSelected) return

    vendorComboBox.selectedItem = it.selectItem
    val newVersion = it.item
    val path = JdkInstaller.getInstance().defaultInstallDir(newVersion, localEel).toString()
    val relativePath = FileUtil.getLocationRelativeToUserHome(path)
    installDirTextField.text = relativePath
    selectedPath = path
    selectedItem = newVersion
  }

  private fun onVersionSelectionChange(it: JdkVersionItem?) {
    if (it == null) return
    versionComboBox.selectedItem = it
    vendorComboBox.model = it.model
    vendorComboBox.itemWithSeparator = it.excludedItems.firstOrNull()
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
