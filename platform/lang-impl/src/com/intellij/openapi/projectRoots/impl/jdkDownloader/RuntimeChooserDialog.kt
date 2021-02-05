// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

class RuntimeChooserDialog(
  private val project: Project?,
  private val model: RuntimeChooserModel,
) : DialogWrapper(project) {
  private val USE_DEFAULT_RUNTIME_CODE = NEXT_USER_EXIT_CODE + 42

  private lateinit var jdkInstallDirSelector: TextFieldWithBrowseButton
  private lateinit var jdkCombobox: ComboBox<RuntimeChooserItem>

  init {
    title = LangBundle.message("dialog.title.choose.ide.runtime")
    setResizable(false)
    init()
  }

  override fun createActions(): Array<Action> {
    return super.createActions() + object : DialogWrapperExitAction(
      LangBundle.message("dialog.button.choose.ide.runtime.useDefault"),
      USE_DEFAULT_RUNTIME_CODE) {

    }
  }

  override fun createSouthAdditionalPanel(): JPanel {
    val panel: JPanel = NonOpaquePanel(BorderLayout())
    panel.border = JBUI.Borders.emptyLeft(10)

    val link = ActionLink(LangBundle.message("dialog.label.choose.ide.runtime.more"))
    link.addActionListener {
      model.showAdvancedOptions()
      link.isEnabled = false
    }

    panel.add(link)
    return panel
  }


  override fun createCenterPanel(): JComponent {
    jdkCombobox = object : ComboBox<RuntimeChooserItem>(model.mainComboBoxModel) {
      init {
        isSwingPopup = false
        setMinimumAndPreferredWidth(400)
        setRenderer(RuntimeChooserPresenter(this@RuntimeChooserDialog.model))
      }
    }

    return panel {
      row(LangBundle.message("dialog.label.choose.ide.runtime.combo")) {
        jdkCombobox.invoke(growX)
      }

      //download row
      row(ProjectBundle.message("dialog.row.jdk.location")) {
        val locationLabel = JBTextField()
        locationLabel.isEditable = false
        locationLabel.invoke(growX)

        val updateLocation = {
          (jdkCombobox.selectedItem as? RuntimeChooserItemWithFixedLocation)?.let { item ->
            locationLabel.text = SdkListPresenter.presentDetectedSdkPath(item.homeDir)
          }
        }
        updateLocation()
        jdkCombobox.addItemListener { updateLocation() }
      }.onlyVisibleWhenSelected { it is RuntimeChooserItemWithFixedLocation }

      //download row
      row(ProjectBundle.message("dialog.row.jdk.location")) {
        jdkInstallDirSelector = textFieldWithBrowseButton(
          project = project,
          browseDialogTitle = LangBundle.message("dialog.title.choose.ide.runtime.select.path.to.install.jdk"),
          fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        ).constraints(growX).component

        val updateLocation = {
          (jdkCombobox.selectedItem as? RuntimeChooserDownloadableItem)?.let { item ->
            jdkInstallDirSelector.text = model.getDefaultInstallPathFor(item.item)
          }
        }
        updateLocation()
        jdkCombobox.addItemListener { updateLocation() }
      }.onlyVisibleWhenSelected { it is RuntimeChooserDownloadableItem }

      row {
        comment(LangBundle.message("dialog.message.choose.ide.runtime.select.path.to.install.jdk"))
      }.onlyVisibleWhenSelected { it is RuntimeChooserDownloadableItem }
    }
  }

  private fun Row.onlyVisibleWhenSelected(isVisible: (RuntimeChooserItem?) -> Boolean) {
    val updateVisible = {
      val visible = isVisible(jdkCombobox.selectedItem as? RuntimeChooserItem)
      this@onlyVisibleWhenSelected.visible = visible
      this@onlyVisibleWhenSelected.enabled = visible
    }
    updateVisible()
    jdkCombobox.addItemListener { updateVisible() }
  }
}
