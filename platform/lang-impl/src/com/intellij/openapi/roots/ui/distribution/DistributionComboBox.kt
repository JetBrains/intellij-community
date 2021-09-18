// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.distribution

import com.intellij.openapi.application.ex.ClipboardUtil
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
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


class DistributionComboBox(project: Project?, info: FileChooserInfo) : ComboBox<DistributionInfo>(CollectionComboBoxModel()) {

  var comboBoxActionName = ProjectBundle.message("sdk.specify.location")
  var noDistributionName = ProjectBundle.message("sdk.missing.item")

  private val collectionModel: CollectionComboBoxModel<DistributionInfo>
    get() = model as CollectionComboBoxModel

  var selectedDistribution: DistributionInfo
    get() = selectedItem as DistributionInfo
    set(distribution) {
      selectedItem = distribution
    }

  override fun setSelectedItem(anObject: Any?) {
    val distribution = when (anObject) {
      is SpecifyDistributionActionInfo -> LocalDistributionInfo(System.getProperty("user.home", ""))
      is DistributionInfo -> anObject
      null -> NoDistributionInfo
      else -> return
    }
    addItemIfNotExists(distribution)
    super.setSelectedItem(distribution)
  }

  fun addItemIfNotExists(distribution: DistributionInfo) {
    if (!collectionModel.contains(distribution)) {
      if (distribution is LocalDistributionInfo) {
        collectionModel.add(distribution)
      }
      else {
        val index = collectionModel.getElementIndex(SpecifyDistributionActionInfo)
        collectionModel.add(index, distribution)
      }
      if (collectionModel.contains(NoDistributionInfo)) {
        collectionModel.remove(NoDistributionInfo)
        super.setSelectedItem(distribution)
      }
    }
  }

  init {
    renderer = Optional.ofNullable(popup)
      .map { it.list }
      .map { ExpandableItemsHandlerFactory.install<Int>(it) }
      .map<ListCellRenderer<DistributionInfo?>> { ExpandedItemListCellRendererWrapper(Renderer(), it) }
      .orElseGet { Renderer() }
  }

  init {
    collectionModel.add(NoDistributionInfo)
    collectionModel.add(SpecifyDistributionActionInfo)
    selectedDistribution = collectionModel.items.first { it != SpecifyDistributionActionInfo }
  }

  init {
    ComboBoxEditor.installComboBoxEditor(project, info, this)
    whenItemSelected {
      setEditable(it is LocalDistributionInfo)
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

  private inner class Renderer : ColoredListCellRenderer<DistributionInfo>() {
    override fun customizeCellRenderer(
      list: JList<out DistributionInfo>,
      value: DistributionInfo?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      ipad = JBUI.emptyInsets()
      myBorder = null

      when (value) {
        NoDistributionInfo -> append(noDistributionName)
        SpecifyDistributionActionInfo -> append(comboBoxActionName)
        else -> {
          val name = value?.name
          val description = value?.description
          if (name != null) {
            append(name)
          }
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
        override fun getText(component: DistributionComboBox) = component.selectedDistribution.asText()
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
              if (anObject is DistributionInfo) {
                editor.text = anObject.asText()
              }
            }
          }

          override fun getItem(): Any {
            return component.selectedDistribution
          }
        })
      }

      private fun DistributionInfo.asText(): String {
        return if (description == null) name else "$name $description"
      }
    }
  }

  object NoDistributionInfo : DistributionInfo {
    override val name: Nothing get() = throw UnsupportedOperationException()
    override val description: Nothing get() = throw UnsupportedOperationException()
  }

  private object SpecifyDistributionActionInfo : DistributionInfo {
    override val name: Nothing get() = throw UnsupportedOperationException()
    override val description: Nothing get() = throw UnsupportedOperationException()
  }
}