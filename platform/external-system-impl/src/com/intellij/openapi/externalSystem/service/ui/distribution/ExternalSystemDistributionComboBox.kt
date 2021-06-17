// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.distribution

import com.intellij.openapi.application.ex.ClipboardUtil
import com.intellij.openapi.externalSystem.service.ui.whenItemSelected
import com.intellij.openapi.externalSystem.service.ui.whenTextModified
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.BrowseFolderRunnable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.lockOrSkip
import java.awt.event.ActionEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.AbstractAction
import javax.swing.JList
import javax.swing.JTextField
import javax.swing.plaf.basic.BasicComboBoxEditor
import javax.swing.text.DefaultEditorKit
import javax.swing.text.JTextComponent


class ExternalSystemDistributionComboBox(
  project: Project,
  distributionsInfo: ExternalSystemDistributionsInfo
) : ComboBox<ExternalSystemDistributionInfo>(CollectionComboBoxModel()) {

  private val collectionModel: CollectionComboBoxModel<ExternalSystemDistributionInfo>
    get() = model as CollectionComboBoxModel

  var selectedDistribution: ExternalSystemDistributionInfo
    get() = selectedItem as ExternalSystemDistributionInfo
    set(distribution) {
      selectedItem = distribution
    }

  override fun setSelectedItem(anObject: Any?) {
    val distribution = when (anObject) {
      is SpecifyDistributionActionInfo -> LocalDistributionInfo(System.getProperty("user.home", ""))
      is ExternalSystemDistributionInfo -> anObject
      else -> return
    }
    addIfNotExists(distribution)
    super.setSelectedItem(distribution)
  }

  private fun addIfNotExists(distribution: ExternalSystemDistributionInfo) {
    if (distribution !in collectionModel.items) {
      collectionModel.add(distribution)
    }
  }

  init {
    renderer = Renderer()
    val preferredWidth = distributionsInfo.comboBoxPreferredWidth
    if (preferredWidth != null) {
      setMinimumAndPreferredWidth(preferredWidth)
    }
  }

  init {
    val (localDistributions, distributions) = distributionsInfo.distributions
      .partition { it is LocalDistributionInfo }
    distributions.forEach { addIfNotExists(it) }
    addIfNotExists(SpecifyDistributionActionInfo(distributionsInfo.comboBoxActionName))
    localDistributions.forEach { addIfNotExists(it) }
    selectedDistribution = collectionModel.items.first()
  }

  init {
    ComboBoxEditor.installComboBoxEditor(project, distributionsInfo, this)
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
          val comboBox = e.source as? ExternalSystemDistributionComboBox ?: return
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

  private class Renderer : ColoredListCellRenderer<ExternalSystemDistributionInfo>() {
    override fun customizeCellRenderer(
      list: JList<out ExternalSystemDistributionInfo>,
      value: ExternalSystemDistributionInfo?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
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

  private class ComboBoxEditor(
    project: Project,
    distributionsInfo: ExternalSystemDistributionsInfo,
    component: ExternalSystemDistributionComboBox
  ) : ExtendableTextField() {
    init {
      val fileBrowserAccessor = object : TextComponentAccessor<ExternalSystemDistributionComboBox> {
        override fun getText(component: ExternalSystemDistributionComboBox) = component.selectedDistribution.asText()
        override fun setText(component: ExternalSystemDistributionComboBox, text: String) {
          component.selectedDistribution = LocalDistributionInfo(text)
        }
      }
      val selectFolderAction = BrowseFolderRunnable<ExternalSystemDistributionComboBox>(
        distributionsInfo.fileChooserTitle,
        distributionsInfo.fileChooserDescription,
        project,
        distributionsInfo.fileChooserDescriptor,
        component,
        fileBrowserAccessor
      )
      addBrowseExtension(selectFolderAction, null)
    }

    init {
      val fileChooserFactory = FileChooserFactory.getInstance()
      fileChooserFactory.installFileCompletion(this, distributionsInfo.fileChooserDescriptor, true, null)
    }

    init {
      border = null
    }

    companion object {
      fun installComboBoxEditor(
        project: Project,
        distributionsInfo: ExternalSystemDistributionsInfo,
        component: ExternalSystemDistributionComboBox
      ) {
        val mutex = AtomicBoolean()
        component.setEditor(object : BasicComboBoxEditor() {
          override fun createEditorComponent(): JTextField {
            return ComboBoxEditor(project, distributionsInfo, component).apply {
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
              if (anObject is ExternalSystemDistributionInfo) {
                editor.text = anObject.asText()
              }
            }
          }

          override fun getItem(): Any {
            return component.selectedDistribution
          }
        })
      }

      private fun ExternalSystemDistributionInfo.asText(): String {
        return if (description == null) name else "$name $description"
      }
    }
  }

  private class SpecifyDistributionActionInfo(override val name: String) : ExternalSystemDistributionInfo() {
    override val description: String? = null
  }
}