// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.ide.util.ElementsChooser
import com.intellij.ide.util.MultiStateElementsChooser
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.FieldPanel
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
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
      ObjectOpenHashSet(value.trim { it <= ' ' }.split("|"))
    }
  }

private fun addToExistingListElement(item: ExportableItem,
                                     itemToContainingListElement: MutableMap<ExportableItem, ComponentElementProperties>,
                                     fileToItem: Map<Path, List<ExportableItem>>): Boolean {
  val list = fileToItem[item.file]
  if (list == null || list.isEmpty()) {
    return false
  }

  var file: Path? = null
  for (tiedItem in list) {
    if (tiedItem === item) {
      continue
    }

    val elementProperties = itemToContainingListElement[tiedItem]
    if (elementProperties != null && item.file !== file) {
      LOG.assertTrue(file == null, "Component $item serialize itself into $file and ${item.file}")
      // found
      elementProperties.items.add(item)
      itemToContainingListElement[item] = elementProperties
      file = item.file
    }
  }
  return file != null
}

fun chooseSettingsFile(oldPath: String?, parent: Component?, title: String, description: String): Promise<VirtualFile> {
  val chooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
  chooserDescriptor.description = description
  chooserDescriptor.isHideIgnored = false
  chooserDescriptor.title = title
  chooserDescriptor.withFileFilter {
    ConfigImportHelper.isSettingsFile(it) ||
    it.toPathOrNull()?.let { p -> ConfigImportHelper.isConfigDirectory(p) } == true
  }

  var initialDir: VirtualFile?
  if (oldPath != null) {
    val oldFile = File(oldPath)
    initialDir = LocalFileSystem.getInstance().findFileByIoFile(oldFile)
    if (initialDir == null && oldFile.parentFile != null) {
      initialDir = LocalFileSystem.getInstance().findFileByIoFile(oldFile.parentFile)
    }
  }
  else {
    initialDir = null
  }
  val result = AsyncPromise<VirtualFile>()
  FileChooser.chooseFiles(chooserDescriptor, null, parent, initialDir, object : FileChooser.FileChooserConsumer {
    override fun consume(files: List<VirtualFile>) {
      val file = files[0]
      result.setResult(file)
    }

    override fun cancelled() {
      result.setError("")
    }
  })
  return result
}

internal class ChooseComponentsToExportDialog(fileToComponents: Map<Path, List<ExportableItem>>,
                                              private val isShowFilePath: Boolean,
                                              title: @NlsContexts.DialogTitle String,
                                              private val description: String) : DialogWrapper(false) {
  private val chooser: ElementsChooser<ComponentElementProperties>
  private val pathPanel = FieldPanel(ConfigurationStoreBundle.message("editbox.export.settings.to"), null, { browse() }, null)

  internal val exportableComponents: Set<ExportableItem>
    get() {
      val components = ObjectOpenHashSet<ExportableItem>()
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
    chooseSettingsFile(pathPanel.text, window, ConfigurationStoreBundle.message("title.export.file.location"),
                       ConfigurationStoreBundle.message("prompt.choose.export.settings.file.path"))
      .onSuccess { file ->
        val path = if (file.isDirectory) "${file.path}/$DEFAULT_FILE_NAME" else file.path
        pathPanel.text = FileUtil.toSystemDependentName(path)
      }
  }

  private fun updateControls() {
    isOKActionEnabled = !pathPanel.text.isNullOrBlank()
  }

  override fun createLeftSideActions(): Array<Action> {
    val selectAll = object : AbstractAction("Select &All") {
      override fun actionPerformed(e: ActionEvent) {
        chooser.setAllElementsMarked(true)
      }
    }
    val selectNone = object : AbstractAction("Select &None") {
      override fun actionPerformed(e: ActionEvent) {
        chooser.setAllElementsMarked(false)
      }
    }
    val invert = object : AbstractAction("&Invert") {
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
  val items = ObjectOpenHashSet<ExportableItem>()

  val fileName: String
    get() = items.first().file.fileName.toString()

  override fun toString(): String {
    val names = LinkedHashSet<String>()
    for (item in items) {
      names.add(item.presentableName)
    }
    return names.joinToString(", ")
  }
}