// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.layout.*
import java.awt.Component
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JList


class RuntimeChooserAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val model = RuntimeChooserModel()
    RuntimeChooserDialog(null, model).show()
  }
}

private class RuntimeChooserModel {
  private val myMainComboModel = DefaultComboBoxModel<RuntimeChooseItem>()

  val mainComboBoxModel : ComboBoxModel<RuntimeChooseItem>
    get() = myMainComboModel

  private fun updateDownloadJbrList(items: List<JdkItem>) {
    //TODO: make sure we have the dialog modality!
    invokeLater(modalityState = ModalityState.any() /*hack!*/) {
      myMainComboModel.removeAllElements()
      myMainComboModel.addAll(items.map { RuntimeChooserDownloadableItem(it) })
    }
  }

  fun fetchAvailableJbrs() {
    ApplicationManager.getApplication().assertIsDispatchThread()

    object: Task.Backgroundable(null, LangBundle.message("progress.title.downloading.jetbrains.runtime.list")) {
      override fun run(indicator: ProgressIndicator) {
        onUpdateDownloadJbrListScheduled()
        val builds = service<JbrListDownloader>().downloadForUI(indicator)
        updateDownloadJbrList(builds)
      }
    }.queue()
  }

  fun onUpdateDownloadJbrListScheduled() {
    //show progress
  }
}


private sealed class RuntimeChooseItem
private object RuntimeChooseSeparator : RuntimeChooseItem()
private class RuntimeChooserDownloadableItem(val item : JdkItem) : RuntimeChooseItem() {
  override fun toString() = item.fullPresentationText
}

@Service(Service.Level.APP)
private class JbrListDownloader : JdkListDownloaderBase() {
  override val feedUrl: String by lazy {
    val majorVersion = ApplicationInfo.getInstance().build.components.firstOrNull()
    "https://download.jetbrains.com/jdk/feed/v1/jbr-choose-runtime-${majorVersion}.json.xz"
  }
}

private class RuntimeChooserMainPresenter : ColoredListCellRenderer<RuntimeChooseItem>() {
  override fun getListCellRendererComponent(list: JList<out RuntimeChooseItem>?,
                                            value: RuntimeChooseItem?,
                                            index: Int,
                                            selected: Boolean,
                                            hasFocus: Boolean): Component {
    if (value is RuntimeChooseSeparator) return SeparatorComponent()
    return super.getListCellRendererComponent(list, value, index, selected, hasFocus)
  }

  override fun customizeCellRenderer(list: JList<out RuntimeChooseItem>,
                                     value: RuntimeChooseItem?,
                                     index: Int,
                                     selected: Boolean,
                                     hasFocus: Boolean) {
    if (value is RuntimeChooserDownloadableItem) {
      val item = value.item
      item.product.vendor.let {
        append(it)
        append(" ")
      }

      item.product.product?.let {
        append(it)
        append(" ")
      }

      append(item.jdkVersion, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true)
      append(" ")

      item.product.flavour?.let {
        append(it, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }
}

private class RuntimeChooserDialog(
  project: Project?,
  val runtimeChooserModel: RuntimeChooserModel,
) : DialogWrapper(project) {
  init {
    title = LangBundle.message("dialog.title.choose.ide.runtime")
    runtimeChooserModel.fetchAvailableJbrs()

    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(LangBundle.message("dialog.message.choose.ide.runtime.combo")) {
        object : ComboBox<RuntimeChooseItem>(runtimeChooserModel.mainComboBoxModel) {
          init {
            isSwingPopup = false
            setMinimumAndPreferredWidth(200)
            setRenderer(RuntimeChooserMainPresenter())
          }
        }.invoke()
      }
    }
  }
}
