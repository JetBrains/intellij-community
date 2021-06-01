// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.icons.AllIcons
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import com.intellij.util.castSafelyTo
import com.intellij.util.io.isDirectory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.datatransfer.DataFlavor
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JPanel
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
    initClipboardListener()
  }

  private fun initClipboardListener() {
    val knownPaths = mutableSetOf<String>()

    val clipboardUpdateAction = {
      val newPath = runCatching {
        CopyPasteManager.getInstance().contents?.getTransferData(DataFlavor.stringFlavor) as? String
      }.getOrNull()

      if (newPath != null && newPath.isNotBlank() && knownPaths.add(newPath)) {
        RuntimeChooserCustom.importDetectedItem(newPath.trim(), model)
      }
    }

    val windowListener = object: WindowAdapter() {
      override fun windowActivated(e: WindowEvent?) {
        invokeLater(ModalityState.any()) {
          clipboardUpdateAction()
        }
      }
    }

    window?.let { window ->
      window.addWindowListener(windowListener)
      Disposer.register(disposable) { window.removeWindowListener(windowListener) }
    }

    clipboardUpdateAction()
  }

  override fun getData(dataId: String): Any? {
    return RuntimeChooserCustom.jdkDownloaderExtensionProvider.getData(dataId)
  }

  override fun createSouthAdditionalPanel(): JPanel {
    return BorderLayoutPanel().apply {
      addToCenter(
        createJButtonForAction(
          DialogWrapperExitAction(
            LangBundle.message("dialog.button.choose.ide.runtime.useDefault"),
            USE_DEFAULT_RUNTIME_CODE)
        )
      )
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
        return RuntimeChooserDialogResult.UseCustomJdk(listOfNotNull(jdkItem.displayName, jdkItem.version).joinToString(" "), home)
      }
    }

    return RuntimeChooserDialogResult.Cancel
  }

  override fun createTitlePane(): JComponent {
    return BorderLayoutPanel().apply {
        val customLine = when {
          SystemInfo.isWindows -> JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0)
          else -> JBUI.Borders.customLineBottom(JBColor.border())
        }
        border = JBUI.Borders.merge(JBUI.Borders.empty(10), customLine, true)
        background = JBUI.CurrentTheme.Notification.BACKGROUND
        foreground = JBUI.CurrentTheme.Notification.FOREGROUND

        addToCenter(JBLabel().apply {
          icon = AllIcons.General.Warning
          verticalTextPosition = SwingConstants.TOP
          text = HtmlChunk
            .html()
            .addText(LangBundle.message("dialog.label.choose.ide.runtime.warn", ApplicationInfo.getInstance().shortCompanyName))
            .toString()
        })

        withPreferredWidth(400)
    }
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

        if (anObject is RuntimeChooserDownloadableItem || anObject is RuntimeChooserCustomItem || anObject is RuntimeChooserCurrentItem) {
          super.setSelectedItem(anObject)
        }
      }
    }

    return panel {
      row(LangBundle.message("dialog.label.choose.ide.runtime.current")) {
        val control = SimpleColoredComponent()
        control().constraints(growX)

        model.currentRuntime.getAndSubscribe(disposable) {
          control.clear()
          if (it != null) {
            RuntimeChooserPresenter.run {
              control.presetCurrentRuntime(it)
            }
          }
        }
      }

      row(LangBundle.message("dialog.label.choose.ide.runtime.combo")) {
        jdkCombobox.invoke(growX)
      }

      //download row
      row(LangBundle.message("dialog.label.choose.ide.runtime.location")) {
        jdkInstallDirSelector = textFieldWithBrowseButton(
          project = project,
          browseDialogTitle = LangBundle.message("dialog.title.choose.ide.runtime.select.path.to.install.jdk"),
          fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        ).constraints(growX)
          .comment(LangBundle.message("dialog.message.choose.ide.runtime.select.path.to.install.jdk"))
          .component

        val updateLocation = {
          when(val item = jdkCombobox.selectedItem){
            is RuntimeChooserDownloadableItem -> {
              jdkInstallDirSelector.text = model.getDefaultInstallPathFor(item.item)
              jdkInstallDirSelector.setButtonEnabled(true)
              jdkInstallDirSelector.isEditable = true
              jdkInstallDirSelector.setButtonVisible(true)
            }
            is RuntimeChooserItemWithFixedLocation -> {
              jdkInstallDirSelector.text = FileUtil.getLocationRelativeToUserHome(item.homeDir, false)
              jdkInstallDirSelector.setButtonEnabled(false)
              jdkInstallDirSelector.isEditable = false
              jdkInstallDirSelector.setButtonVisible(false)
            }
            else -> {
              jdkInstallDirSelector.text = ""
              jdkInstallDirSelector.setButtonEnabled(false)
              jdkInstallDirSelector.isEditable = false
              jdkInstallDirSelector.setButtonVisible(false)
            }
          }
        }
        updateLocation()
        jdkCombobox.addItemListener { updateLocation() }
      }
    }
  }
}
