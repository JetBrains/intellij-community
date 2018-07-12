// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.ElementsChooser
import com.intellij.ide.util.MultiStateElementsChooser
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.FieldPanel
import com.intellij.util.ArrayUtil
import gnu.trove.THashSet
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

private const val KEY_UNMARKED_NAMES = "export.settings.unmarked"

private val unmarkedElementNames: Set<String>
  get() {
    val value = PropertiesComponent.getInstance().getValue(KEY_UNMARKED_NAMES)
    return if (StringUtil.isEmpty(value)) {
      emptySet()
    }
    else THashSet(StringUtil.split(value!!.trim { it <= ' ' }, "|"))
  }

private fun addToExistingListElement(item: ExportableItem,
                                     itemToContainingListElement: MutableMap<ExportableItem, ComponentElementProperties>,
                                     fileToItem: Map<Path, List<ExportableItem>>): Boolean {
  val list = fileToItem.get(item.file)
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
      LOG.assertTrue(file == null, "Component " + item + " serialize itself into " + file + " and " + item.file)
      // found
      elementProperties.items.add(item)
      itemToContainingListElement[item] = elementProperties
      file = item.file
    }
  }
  return file != null
}

fun chooseSettingsFile(oldPath: String?, parent: Component?, title: String, description: String): Promise<String> {
  val chooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
  chooserDescriptor.description = description
  chooserDescriptor.isHideIgnored = false
  chooserDescriptor.title = title
  chooserDescriptor.withFileFilter { ConfigImportHelper.isSettingsFile(it) }

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
  val result = AsyncPromise<String>()
  FileChooser.chooseFiles(chooserDescriptor, null, parent, initialDir, object : FileChooser.FileChooserConsumer {
    override fun consume(files: List<VirtualFile>) {
      val file = files[0]
      if (file.isDirectory) {
        result.setResult(file.path + '/'.toString() + DEFAULT_FILE_NAME)
      }
      else {
        result.setResult(file.path)
      }
    }

    override fun cancelled() {
      result.setError("")
    }
  })
  return result
}

class ChooseComponentsToExportDialog(fileToComponents: Map<Path, List<ExportableItem>>,
                                     private val myShowFilePath: Boolean, title: String, private val myDescription: String) : DialogWrapper(false) {
  private val myChooser: ElementsChooser<ComponentElementProperties>
  private val pathPanel = FieldPanel(IdeBundle.message("editbox.export.settings.to"), null, null                                     , null)

  internal val exportableComponents: Set<ExportableItem>
    get() {
      val components = THashSet<ExportableItem>()
      for (elementProperties in myChooser.markedElements) {
        components.addAll(elementProperties.items)
      }
      return components
    }

  internal val exportFile: Path
    get() = Paths.get(pathPanel.text)

  init {
    pathPanel.setBrowseButtonActionListener {
      chooseSettingsFile(pathPanel.text, window, IdeBundle.message("title.export.file.location"), IdeBundle.message("prompt.choose.export.settings.file.path"))
        .onSuccess { path -> pathPanel.text = FileUtil.toSystemDependentName(path) }
    }

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
    myChooser = ElementsChooser(true)
    myChooser.setColorUnmarkedElements(false)
    val unmarkedElementNames = unmarkedElementNames
    for (componentElementProperty in LinkedHashSet(componentToContainingListElement.values)) {
      myChooser.addElement(componentElementProperty, !unmarkedElementNames.contains(componentElementProperty.toString()), componentElementProperty)
    }
    myChooser.sort(Comparator.comparing<ComponentElementProperties, String> { it.toString() })

    val exportPath = PropertiesComponent.getInstance().getValue("export.settings.path", DEFAULT_PATH)
    pathPanel.text = exportPath
    pathPanel.changeListener = Runnable { this.updateControls() }
    updateControls()

    setTitle(title)
    init()
  }

  private fun updateControls() {
    isOKActionEnabled = !StringUtil.isEmptyOrSpaces(pathPanel.text)
  }

  override fun createLeftSideActions(): Array<Action> {
    val selectAll = object : AbstractAction("Select &All") {
      override fun actionPerformed(e: ActionEvent) {
        myChooser.setAllElementsMarked(true)
      }
    }
    val selectNone = object : AbstractAction("Select &None") {
      override fun actionPerformed(e: ActionEvent) {
        myChooser.setAllElementsMarked(false)
      }
    }
    val invert = object : AbstractAction("&Invert") {
      override fun actionPerformed(e: ActionEvent) {
        myChooser.invertSelection()
      }
    }
    return arrayOf(selectAll, selectNone, invert)
  }

  override fun doOKAction() {
    PropertiesComponent.getInstance().setValue("export.settings.path", pathPanel.text, DEFAULT_PATH)
    val unmarked = myChooser.getElements(false)
    val builder = StringBuilder()
    for (element in unmarked) {
      builder.append(element.toString())
      builder.append("|")
    }
    PropertiesComponent.getInstance().setValue(KEY_UNMARKED_NAMES, if (builder.isEmpty()) null else builder.toString())

    super.doOKAction()
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return pathPanel.textField
  }

  override fun createNorthPanel(): JComponent? {
    return JLabel(myDescription)
  }

  override fun createCenterPanel(): JComponent? {
    return myChooser
  }

  override fun createSouthPanel(): JComponent {
    val buttons = super.createSouthPanel()
    if (!myShowFilePath) return buttons
    val panel = JPanel(VerticalFlowLayout())
    panel.add(pathPanel)
    panel.add(buttons)
    return panel
  }

  override fun getDimensionServiceKey(): String? {
    return "#com.intellij.ide.actions.ChooseComponentsToExportDialog"
  }
}

private class ComponentElementProperties : MultiStateElementsChooser.ElementProperties {
  val items = THashSet<ExportableItem>()

  override fun toString(): String {
    val names = LinkedHashSet<String>()
    for ((_, presentableName) in items) {
      names.add(presentableName)
    }
    return StringUtil.join(ArrayUtil.toStringArray(names), ", ")
  }
}