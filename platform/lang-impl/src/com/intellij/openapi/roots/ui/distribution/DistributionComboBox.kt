// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.distribution

import com.intellij.openapi.application.ex.ClipboardUtil
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.ui.distribution.DistributionComboBox.Item
import com.intellij.openapi.roots.ui.whenItemSelected
import com.intellij.openapi.roots.ui.whenTextModified
import com.intellij.openapi.ui.BrowseFolderRunnable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.ui.*
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.lockOrSkip
import com.intellij.util.ui.JBUI
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


class DistributionComboBox(project: Project?, info: FileChooserInfo) : ComboBox<Item>(CollectionComboBoxModel()) {

  var sdkListLoadingText: String = ProjectBundle.message("sdk.loading.item")
  var noDistributionText: String = ProjectBundle.message("sdk.missing.item")
  var specifyLocationActionName: String = ProjectBundle.message("sdk.specify.location")

  var defaultDistributionLocation: String = System.getProperty("user.home", "")

  private val collectionModel: CollectionComboBoxModel<Item>
    get() = model as CollectionComboBoxModel

  var selectedDistribution: DistributionInfo?
    get() = (selectedItem as? Item.Distribution)?.info
    set(distribution) {
      selectedItem = distribution
    }

  override fun setSelectedItem(anObject: Any?) {
    val item = when (anObject) {
      is Item.ListLoading -> Item.ListLoading
      is Item.NoDistribution, null -> Item.NoDistribution
      is Item.SpecifyDistributionAction -> addDistributionIfNotExists(LocalDistributionInfo(defaultDistributionLocation))
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
    ComboBoxEditor.installComboBoxEditor(project, info, this)
    whenItemSelected {
      setEditable(it is Item.Distribution && it.info is LocalDistributionInfo)
    }
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
      ipad = JBUI.emptyInsets()
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

  private class ComboBoxEditor(
    project: Project?,
    info: FileChooserInfo,
    component: DistributionComboBox
  ) : ExtendableTextField() {
    init {
      val fileBrowserAccessor = object : TextComponentAccessor<DistributionComboBox> {
        override fun getText(component: DistributionComboBox) = component.selectedDistribution?.name ?: ""
        override fun setText(component: DistributionComboBox, text: String) {
          component.selectedDistribution = LocalDistributionInfo(text)
        }
      }
      val selectFolderAction = BrowseFolderRunnable<DistributionComboBox>(
        info.fileChooserTitle,
        info.fileChooserDescription,
        project,
        info.fileChooserDescriptor,
        component,
        fileBrowserAccessor
      )
      addBrowseExtension(selectFolderAction, null)
    }

    init {
      val fileChooserFactory = FileChooserFactory.getInstance()
      fileChooserFactory.installFileCompletion(this, info.fileChooserDescriptor, true, null)
    }

    init {
      border = null
    }

    companion object {
      fun installComboBoxEditor(
        project: Project?,
        info: FileChooserInfo,
        component: DistributionComboBox
      ) {
        val mutex = AtomicBoolean()
        component.setEditor(object : BasicComboBoxEditor() {
          override fun createEditorComponent(): JTextField {
            return ComboBoxEditor(project, info, component).apply {
              whenTextModified {
                mutex.lockOrSkip {
                  val distribution = component.selectedDistribution
                  if (distribution is LocalDistributionInfo) {
                    distribution.uiPath = text
                    component.popup?.list?.repaint()
                  }
                }
              }
            }
          }

          override fun setItem(anObject: Any?) {
            mutex.lockOrSkip {
              if (anObject is Item.Distribution) {
                editor.text = anObject.info.name
              }
            }
          }

          override fun getItem(): Any? {
            return component.selectedItem
          }
        })
      }
    }
  }

  sealed class Item {
    object ListLoading : Item()
    object NoDistribution : Item()
    object SpecifyDistributionAction : Item()
    data class Distribution(val info: DistributionInfo) : Item()
  }
}