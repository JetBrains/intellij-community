// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.ide.util.ElementsChooser
import com.intellij.ide.util.MultiStateElementsChooser
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.FieldPanel
import com.intellij.util.containers.CollectionFactory
import java.awt.Component
import java.awt.event.ActionEvent
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.swing.*

private const val DEFAULT_FILE_NAME = "settings.zip"
private val DEFAULT_PATH = FileUtil.toSystemDependentName(PathManager.getConfigPath() + "/") + DEFAULT_FILE_NAME

private const val KEY_MARKED_NAMES = "export.settings.marked"

private val markedElementNames: Set<String>
  get() {
    val value = PropertiesComponent.getInstance().getValue(KEY_MARKED_NAMES)
    return if (value.isNullOrEmpty()) {
      emptySet()
    }
    else {
      CollectionFactory.createSmallMemoryFootprintSet(value.trim { it <= ' ' }.split("|"))
    }
  }

private fun addToExistingListElement(item: ExportableItem,
                                     itemToContainingListElement: MutableMap<ExportableItem, ComponentElementProperties>,
                                     fileToItem: Map<FileSpec, List<ExportableItem>>): Boolean {
  val list = fileToItem[item.fileSpec]
  if (list == null || list.isEmpty()) {
    return false
  }

  var file: FileSpec? = null
  for (tiedItem in list) {
    if (tiedItem === item) {
      continue
    }

    val elementProperties = itemToContainingListElement[tiedItem]
    if (elementProperties != null && item.fileSpec !== file) {
      LOG.assertTrue(file == null, "Component $item serialize itself into $file and ${item.fileSpec}")
      // found
      elementProperties.items.add(item)
      itemToContainingListElement[item] = elementProperties
      file = item.fileSpec
    }
  }
  return file != null
}

internal fun chooseSettingsFile(descriptor: FileChooserDescriptor,
                                initialPath: String?,
                                parent: Component?,
                                onFileChosen: (VirtualFile) -> Unit) {
  FileChooser.chooseFile(descriptor, null, parent, getFileOrParent(initialPath), onFileChosen)
}

private fun getFileOrParent(path: String?): VirtualFile? {
  if (path != null) {
    val oldFile = File(path)
    val initialDir = LocalFileSystem.getInstance().findFileByIoFile(oldFile)
    return initialDir ?: oldFile.parentFile?.let {
      LocalFileSystem.getInstance().findFileByIoFile(it)
    }
  }
  return null
}

internal class ChooseComponentsToExportDialog(fileToComponents: Map<FileSpec, List<ExportableItem>>,
                                              private val isShowFilePath: Boolean,
                                              @NlsContexts.DialogTitle title: String,
                                              @NlsContexts.Label private val description: String) : DialogWrapper(false) {
  private val chooser: ElementsChooser<ComponentElementProperties>
  private val pathPanel = FieldPanel(ConfigurationStoreBundle.message("editbox.export.settings.to"), null, { browse() }, null)

  internal val exportableComponents: Set<ExportableItem>
    get() {
      val components = CollectionFactory.createSmallMemoryFootprintSet<ExportableItem>()
      for (elementProperties in chooser.markedElements) {
        components.addAll(elementProperties.items)
      }
      return components
    }

  internal val exportFile: Path
    get() = Paths.get(pathPanel.text)

  init {
    val componentToContainingListElement = LinkedHashMap<ExportableItem, ComponentElementProperties>()
    for (list in fileToComponents.values) {
      for (item in list) {
        if (!addToExistingListElement(item, componentToContainingListElement, fileToComponents)) {
          val componentElementProperties = ComponentElementProperties()
          componentElementProperties.items.add(item)
          componentToContainingListElement[item] = componentElementProperties
        }
      }
    }
    chooser = ElementsChooser(true)
    chooser.setColorUnmarkedElements(false)
    val markedElementNames = markedElementNames
    for (componentElementProperty in LinkedHashSet(componentToContainingListElement.values)) {
      chooser.addElement(componentElementProperty, markedElementNames.isEmpty() || markedElementNames.contains(componentElementProperty.fileName), componentElementProperty)
    }
    chooser.sort(Comparator.comparing<ComponentElementProperties, String> { it.toString() })

    val exportPath = PropertiesComponent.getInstance().getValue("export.settings.path", DEFAULT_PATH)
    pathPanel.text = exportPath
    pathPanel.changeListener = Runnable { this.updateControls() }
    updateControls()

    setTitle(title)
    init()
  }

  private fun browse() {
    val descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor().apply {
      title = ConfigurationStoreBundle.message("title.export.file.location")
      description = ConfigurationStoreBundle.message("prompt.choose.export.settings.file.path")
      isHideIgnored = false
      withFileFilter { ConfigImportHelper.isSettingsFile(it) }
    }
    chooseSettingsFile(descriptor, pathPanel.text, window) { file ->
      val path = if (file.isDirectory) "${file.path}/$DEFAULT_FILE_NAME" else file.path
      pathPanel.text = FileUtil.toSystemDependentName(path)
    }
  }

  private fun updateControls() {
    isOKActionEnabled = !pathPanel.text.isNullOrBlank()
  }

  override fun createLeftSideActions(): Array<Action> {
    val selectAll = object : AbstractAction(ConfigurationStoreBundle.message("export.components.list.action.select.all")) {
      override fun actionPerformed(e: ActionEvent) {
        chooser.setAllElementsMarked(true)
      }
    }
    val selectNone = object : AbstractAction(ConfigurationStoreBundle.message("export.components.list.action.select.none")) {
      override fun actionPerformed(e: ActionEvent) {
        chooser.setAllElementsMarked(false)
      }
    }
    val invert = object : AbstractAction(ConfigurationStoreBundle.message("export.components.list.action.invert.selection")) {
      override fun actionPerformed(e: ActionEvent) {
        chooser.invertSelection()
      }
    }
    return arrayOf(selectAll, selectNone, invert)
  }

  override fun doOKAction() {
    PropertiesComponent.getInstance().setValue("export.settings.path", pathPanel.text, DEFAULT_PATH)

    val builder = StringBuilder()
    if (chooser.hasUnmarkedElements()) {
      val marked = chooser.getElements(true)
      for (element in marked) {
        builder.append(element.fileName)
        builder.append("|")
      }
    }
    PropertiesComponent.getInstance().setValue(KEY_MARKED_NAMES, if (builder.isEmpty()) null else builder.toString())

    super.doOKAction()
  }

  override fun getPreferredFocusedComponent(): JTextField? = pathPanel.textField

  override fun createNorthPanel() = JLabel(description)

  override fun createCenterPanel(): JComponent = chooser

  override fun createSouthPanel(): JComponent {
    val buttons = super.createSouthPanel()
    if (!isShowFilePath) {
      return buttons
    }
    val panel = JPanel(VerticalFlowLayout())
    panel.add(pathPanel)
    panel.add(buttons)
    return panel
  }

  override fun getDimensionServiceKey() = "#com.intellij.ide.actions.ChooseComponentsToExportDialog"
}

private class ComponentElementProperties : MultiStateElementsChooser.ElementProperties {
  val items = CollectionFactory.createSmallMemoryFootprintSet<ExportableItem>()

  val fileName: String
    get() = items.first().fileSpec.relativePath

  override fun toString(): String {
    val names = LinkedHashSet<String>()
    for (item in items) {
      names.add(item.presentableName)
    }
    return names.joinToString(", ")
  }
}