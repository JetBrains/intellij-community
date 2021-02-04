// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.layout.*
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent


class RuntimeChooserAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val model = RuntimeChooserModel()
    RuntimeChooserDialog(null, model).show()
  }
}


private class RuntimeChooserDialog(
  project: Project?,
  val runtimeChooserModel: RuntimeChooserModel,
) : DialogWrapper(project) {

  private val USE_DEFAULT_ACTION = NEXT_USER_EXIT_CODE + 23

  private val useDefaultRuntimeAction = object : DialogWrapperExitAction(
    LangBundle.message("dialog.action.choose.ide.use.bundled"),
    USE_DEFAULT_ACTION) {
  }

  init {
    title = LangBundle.message("dialog.title.choose.ide.runtime")
    runtimeChooserModel.fetchAvailableJbrs()

    init()
  }

  override fun createActions(): Array<Action> {
    return super.createActions() + useDefaultRuntimeAction
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(LangBundle.message("dialog.label.choose.ide.runtime.combo")) {
        object : ComboBox<RuntimeChooseItem>(runtimeChooserModel.mainComboBoxModel) {
          init {
            isSwingPopup = false
            setMinimumAndPreferredWidth(400)
            setRenderer(RuntimeChooserPresenter())
          }
        }.invoke()
      }
    }
  }
}
