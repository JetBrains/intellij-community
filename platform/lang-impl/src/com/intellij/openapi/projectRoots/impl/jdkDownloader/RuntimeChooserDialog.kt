// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.icons.AllIcons
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.toJBEmptyBorder
import com.intellij.util.asSafely
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.datatransfer.DataFlavor
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.isDirectory

internal sealed class RuntimeChooserDialogResult {
  object Cancel : RuntimeChooserDialogResult()
  object UseDefault: RuntimeChooserDialogResult()
  data class DownloadAndUse(val item: JdkItem, val path: Path) : RuntimeChooserDialogResult()
  data class UseCustomJdk(val name: String, val path: Path) : RuntimeChooserDialogResult()
}

internal class RuntimeChooserDialog(
  private val project: Project?,
  private val model: RuntimeChooserModel,
) : DialogWrapper(project), UiDataProvider {
  private val USE_DEFAULT_RUNTIME_CODE = NEXT_USER_EXIT_CODE + 42

  private lateinit var jdkInstallDirSelector: TextFieldWithBrowseButton
  private lateinit var jdkCombobox: ComboBox<RuntimeChooserItem>

  init {
    title = LangBundle.message("dialog.title.choose.ide.runtime")
    isResizable = false
    init()
    initClipboardListener()
  }

  private fun initClipboardListener() {
    val knownPaths = mutableSetOf<String>()

    val clipboardUpdateAction = {
      val newPath = runCatching {
        CopyPasteManager.getInstance().contents?.getTransferData(DataFlavor.stringFlavor) as? String
      }.getOrNull()

      if (!newPath.isNullOrBlank() && knownPaths.add(newPath)) {
        RuntimeChooserCustom.importDetectedItem(newPath.trim(), model, true)
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

  override fun uiDataSnapshot(sink: DataSink) {
    sink[JDK_DOWNLOADER_EXT] = RuntimeChooserCustom.jdkDownloaderExtension
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
      val jdkItem = jdkCombobox.selectedItem.asSafely<RuntimeChooserDownloadableItem>()?.item ?: return@run
      val path = model.getInstallPathFromText(jdkItem, jdkInstallDirSelector.text)
      return RuntimeChooserDialogResult.DownloadAndUse(jdkItem, path)
    }

    if (isOK) run {
      val jdkItem = jdkCombobox.selectedItem.asSafely<RuntimeChooserCustomItem>() ?: return@run
      val home = Paths.get(jdkItem.homeDir)
      if (home.isDirectory()) {
        return RuntimeChooserDialogResult.UseCustomJdk(listOfNotNull(jdkItem.displayName, jdkItem.version).joinToString(" "), home)
      }
    }

    return RuntimeChooserDialogResult.Cancel
  }

  override fun createTitlePane(): JComponent {
    return panel {
      row {
        icon(AllIcons.General.Warning)
          .align(AlignY.TOP)
          .gap(RightGap.SMALL)
        text(LangBundle.message("dialog.label.choose.ide.runtime.warn", ApplicationInfo.getInstance().shortCompanyName),
             maxLineLength = DEFAULT_COMMENT_WIDTH)
      }
    }.apply {
      val customLine = when {
        SystemInfo.isWindows -> JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0)
        else -> JBUI.Borders.customLineBottom(JBColor.border())
      }
      border = JBUI.Borders.merge(JBUI.Borders.empty(10), customLine, true)
      background = if (ExperimentalUI.isNewUI()) JBUI.CurrentTheme.Banner.WARNING_BACKGROUND else JBUI.CurrentTheme.Notification.BACKGROUND
      foreground = JBUI.CurrentTheme.Notification.FOREGROUND
      putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)
    }
  }

  override fun createCenterPanel(): JComponent {
    jdkCombobox = object : ComboBox<RuntimeChooserItem>(model.mainComboBoxModel) {
      init {
        isSwingPopup = false
        setRenderer(object: RuntimeChooserPresenter() {
          override fun separatorFor(value: RuntimeChooserItem?): ListSeparator? {
            val customJdks = this@RuntimeChooserDialog.model.customJdks
            val advancedItems = this@RuntimeChooserDialog.model.advancedDownloadItems
            val message = when {
                            value is RuntimeChooserAddCustomItem && customJdks.isEmpty() -> LangBundle.message("dialog.separator.choose.ide.runtime.advanced")
                            advancedItems.any() && value == advancedItems.first() -> LangBundle.message("dialog.separator.choose.ide.runtime.advancedJbrs")
                            customJdks.any() && value == customJdks.first() -> LangBundle.message("dialog.separator.choose.ide.runtime.customSelected")
                            else -> null
                          } ?: return null
            return ListSeparator(message)
          }
        })
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
        cell(control).align(AlignX.FILL)

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
        cell(jdkCombobox).align(AlignX.FILL)
      }

      //download row
      row(LangBundle.message("dialog.label.choose.ide.runtime.location")) {
        jdkInstallDirSelector = textFieldWithBrowseButton(
          FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(LangBundle.message("dialog.title.choose.ide.runtime.select.path.to.install.jdk")),
          project
        ).align(AlignX.FILL)
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
    }.apply {
      border = IntelliJSpacingConfiguration().dialogUnscaledGaps.toJBEmptyBorder()
      putClientProperty(IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY, false)
    }
  }
}
