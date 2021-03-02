// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.icons.AllIcons
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import com.intellij.util.castSafelyTo
import com.intellij.util.io.isDirectory
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.SwingConstants

sealed class RuntimeChooserDialogResult {
  object Cancel : RuntimeChooserDialogResult()
  object UseDefault: RuntimeChooserDialogResult()
  data class DownloadAndUse(val item: JdkItem, val path: Path) : RuntimeChooserDialogResult()
  data class UseCustomJdk(val name: String, val path: Path) : RuntimeChooserDialogResult()
}

class RuntimeChooserDialog(
  private val project: Project?,
  private val model: RuntimeChooserModel,
) : DialogWrapper(project), DataProvider {
  private val USE_DEFAULT_RUNTIME_CODE = NEXT_USER_EXIT_CODE + 42

  private lateinit var jdkInstallDirSelector: TextFieldWithBrowseButton
  private lateinit var jdkCombobox: ComboBox<RuntimeChooserItem>

  init {
    title = LangBundle.message("dialog.title.choose.ide.runtime")
    setResizable(false)
    init()
  }

  override fun getData(dataId: String): Any? {
    return RuntimeChooserCustom.jdkDownloaderExtensionProvider.getData(dataId)
  }

  override fun createActions(): Array<Action> {
    return super.createActions() + object : DialogWrapperExitAction(
      LangBundle.message("dialog.button.choose.ide.runtime.useDefault"),
      USE_DEFAULT_RUNTIME_CODE) {

    }
  }

  fun showDialogAndGetResult() : RuntimeChooserDialogResult {
    show()

    if (exitCode == USE_DEFAULT_RUNTIME_CODE) {
      return RuntimeChooserDialogResult.UseDefault
    }

    if (isOK) run {
      val jdkItem = jdkCombobox.selectedItem.castSafelyTo<RuntimeChooserDownloadableItem>()?.item ?: return@run
      val path = model.getInstallPathFromText(jdkItem, jdkInstallDirSelector.text)
      return RuntimeChooserDialogResult.DownloadAndUse(jdkItem, path)
    }

    if (isOK) run {
      val jdkItem = jdkCombobox.selectedItem.castSafelyTo<RuntimeChooserCustomItem>() ?: return@run
      val home = Paths.get(jdkItem.homeDir)
      if (home.isDirectory()) {
        return RuntimeChooserDialogResult.UseCustomJdk(jdkItem.version, home)
      }
    }

    return RuntimeChooserDialogResult.Cancel
  }

  override fun createCenterPanel(): JComponent {
    jdkCombobox = object : ComboBox<RuntimeChooserItem>(model.mainComboBoxModel) {
      init {
        isSwingPopup = false
        setMinimumAndPreferredWidth(400)
        setRenderer(RuntimeChooserPresenter())
      }

      override fun setSelectedItem(anObject: Any?) {
        if (anObject !is RuntimeChooserItem) return

        if (anObject is RuntimeChooserAddCustomItem) {
          RuntimeChooserCustom
            .createSdkChooserPopup(jdkCombobox, this@RuntimeChooserDialog.model)
            ?.showUnderneathOf(jdkCombobox)
          return
        }

        if (anObject is RuntimeChooserShowAdvancedItem) {
          this@RuntimeChooserDialog.model.showAdvancedOptions()
          return
        }

        if (anObject is RuntimeChooserDownloadableItem || anObject is RuntimeChooserCustomItem || anObject is RuntimeChooserCurrentItem) {
          super.setSelectedItem(anObject)
        }
      }
    }

    return panel {
      row(LangBundle.message("dialog.label.choose.ide.runtime.combo")) {
        jdkCombobox.invoke(growX)
      }

      //download row
      row(LangBundle.message("dialog.label.choose.ide.runtime.location")) {
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
      row(LangBundle.message("dialog.label.choose.ide.runtime.location")) {
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

      row {
        comment(LangBundle.message("dialog.label.choose.ide.runtime.warn"), 90).component.also {
          it.icon = Messages.getWarningIcon()
        }
      }
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
