// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.distribution

import com.intellij.openapi.application.ex.ClipboardUtil
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.ui.distribution.DistributionComboBox.Item
import com.intellij.openapi.ui.*
import com.intellij.ui.*
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.ui.JBInsets
import java.awt.event.ActionEvent
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.AbstractAction
import javax.swing.JList
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.plaf.basic.BasicComboBoxEditor
import javax.swing.text.DefaultEditorKit
import javax.swing.text.JTextComponent


class DistributionComboBox(
  private val project: Project?,
  private val info: FileChooserInfo
) : ComboBox<Item>(CollectionComboBoxModel()) {

  var sdkListLoadingText: String = ProjectBundle.message("sdk.loading.item")
  var noDistributionText: String = ProjectBundle.message("sdk.missing.item")
  var specifyLocationActionName: String = ProjectBundle.message("sdk.specify.location")

  var defaultDistributionLocation: String? = null

  private val collectionModel: CollectionComboBoxModel<Item>
    get() = model as CollectionComboBoxModel

  var selectedDistribution: DistributionInfo?
    get() = (selectedItem as? Item.Distribution)?.info
    set(distribution) {
      selectedItem = distribution
    }

  override fun setSelectedItem(anObject: Any?) {
    if (anObject is Item.SpecifyDistributionAction) {
      showBrowseDistributionDialog()
      return
    }
    val item = when (anObject) {
      is Item.ListLoading -> Item.ListLoading
      is Item.NoDistribution, null -> Item.NoDistribution
      is Item.Distribution -> addDistributionIfNotExists(anObject.info)
      is DistributionInfo -> addDistributionIfNotExists(anObject)
      else -> throw IllegalArgumentException("Unsupported combobox item: ${anObject.javaClass.name}")
    }
    super.setSelectedItem(item)
  }

  fun addLoadingItem() {
    if (!collectionModel.contains(Item.ListLoading)) {
      val index = collectionModel.getElementIndex(Item.SpecifyDistributionAction)
      collectionModel.add(index, Item.ListLoading)
    }
  }

  fun removeLoadingItem() {
    collectionModel.remove(Item.ListLoading)
  }

  fun addDistributionIfNotExists(info: DistributionInfo): Item {
    val item = Item.Distribution(info)
    val foundItem = collectionModel.items.find { it == item }
    if (foundItem == null) {
      if (info is LocalDistributionInfo) {
        collectionModel.add(item)
      }
      else {
        val index = collectionModel.getElementIndex(Item.SpecifyDistributionAction)
        collectionModel.add(index, item)
        if (collectionModel.contains(Item.NoDistribution)) {
          collectionModel.remove(Item.NoDistribution)
          super.setSelectedItem(item)
        }
      }
    }
    return foundItem ?: item
  }

  private fun getSelectedDistributionUiPath(): String {
    val path =
      (selectedDistribution as? LocalDistributionInfo)?.path
      ?: defaultDistributionLocation
      ?: collectionModel.items.asSequence()
        .mapNotNull { (it as? Item.Distribution)?.info }
        .mapNotNull { (it as? LocalDistributionInfo)?.path }
        .firstOrNull()
      ?: System.getProperty("user.home", "")
    return getPresentablePath(path)
  }

  private fun setSelectedDistributionUiPath(uiPath: String) {
    when (val distribution = selectedDistribution) {
      is LocalDistributionInfo -> {
        distribution.uiPath = uiPath
        popup?.list?.repaint()
        selectedItemChanged()
      }
      else -> {
        selectedDistribution = LocalDistributionInfo(uiPath)
      }
    }
  }

  fun bindSelectedDistribution(property: ObservableMutableProperty<DistributionInfo?>) {
    bind(property.transform(
      { it?.let(Item::Distribution) ?: Item.NoDistribution },
      { (it as? Item.Distribution)?.info }
    ))
  }

  private fun bindSelectedDistributionPath(property: ObservableMutableProperty<String>) {
    val mutex = AtomicBoolean()
    property.afterChange { text ->
      mutex.lockOrSkip {
        setSelectedDistributionUiPath(text)
      }
    }
    whenItemSelected {
      mutex.lockOrSkip {
        property.set(getSelectedDistributionUiPath())
      }
    }
  }

  private fun showBrowseDistributionDialog() {
    val fileBrowserAccessor = object : TextComponentAccessor<DistributionComboBox> {
      override fun getText(component: DistributionComboBox) = getSelectedDistributionUiPath()
      override fun setText(component: DistributionComboBox, text: String) = setSelectedDistributionUiPath(text)
    }
    val selectFolderAction = BrowseFolderRunnable<DistributionComboBox>(
      info.fileChooserTitle,
      info.fileChooserDescription,
      project,
      info.fileChooserDescriptor,
      this,
      fileBrowserAccessor
    )
    selectFolderAction.run()
  }

  private fun createEditor(): Editor {
    val property = AtomicProperty("")
    val editor = object : Editor() {
      override fun setItem(anObject: Any?) {}
      override fun getItem(): Any? = selectedItem
    }
    editor.textField.bind(property)
    bindSelectedDistributionPath(property)
    return editor
  }

  init {
    renderer = Optional.ofNullable(popup)
      .map { it.list }
      .map { ExpandableItemsHandlerFactory.install<Int>(it) }
      .map<ListCellRenderer<Item>> { ExpandedItemListCellRendererWrapper(Renderer(), it) }
      .orElseGet { Renderer() }
  }

  init {
    collectionModel.add(Item.NoDistribution)
    collectionModel.add(Item.SpecifyDistributionAction)
    selectedItem = Item.NoDistribution
  }

  init {
    val editor = createEditor()
    setEditor(editor)
    whenItemSelected {
      setEditable(it is Item.Distribution && it.info is LocalDistributionInfo)
    }
    editor.textField.addBrowseExtension(::showBrowseDistributionDialog, null)
    FileChooserFactory.getInstance()
      .installFileCompletion(editor.textField, info.fileChooserDescriptor, true, null)
  }

  init {
    val editorComponent = editor?.editorComponent
    if (editorComponent is JTextComponent) {
      val editorInputMap = editorComponent.inputMap
      for (keyStroke in editorInputMap.allKeys()) {
        if (DefaultEditorKit.pasteAction == editorInputMap[keyStroke]) {
          inputMap.put(keyStroke, DefaultEditorKit.pasteAction)
        }
      }
      actionMap.put(DefaultEditorKit.pasteAction, object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
          val comboBox = e.source as? DistributionComboBox ?: return
          val clipboardText = ClipboardUtil.getTextInClipboard() ?: return
          val distribution = comboBox.selectedDistribution
          if (distribution is LocalDistributionInfo) {
            distribution.uiPath = clipboardText
          }
          else {
            comboBox.selectedDistribution = LocalDistributionInfo(clipboardText)
          }
        }
      })
    }
  }

  private inner class Renderer : ColoredListCellRenderer<Item>() {
    override fun customizeCellRenderer(
      list: JList<out Item>,
      value: Item?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      ipad = JBInsets.emptyInsets()
      myBorder = null

      when (value) {
        is Item.ListLoading -> append(sdkListLoadingText)
        is Item.NoDistribution -> append(noDistributionText, SimpleTextAttributes.ERROR_ATTRIBUTES)
        is Item.SpecifyDistributionAction -> append(specifyLocationActionName)
        is Item.Distribution -> {
          val name = value.info.name
          val description = value.info.description
          append(name)
          if (description != null) {
            append(" ")
            append(description, SimpleTextAttributes.GRAYED_ATTRIBUTES)
          }
        }
      }
    }
  }

  private abstract class Editor : BasicComboBoxEditor() {
    val textField get() = editor as ExtendableTextField

    override fun createEditorComponent(): JTextField {
      val textField = ExtendableTextField()
      textField.border = null
      return textField
    }
  }

  sealed class Item {
    object ListLoading : Item()
    object NoDistribution : Item()
    object SpecifyDistributionAction : Item()
    data class Distribution(val info: DistributionInfo) : Item()
  }
}