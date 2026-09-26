// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.CommonBundle
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.java.JavaBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiDirectory
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class CreateServiceImplementationDialog(
  project: Project?,
  private val psiRootDirs: Array<PsiDirectory>,
  private val superClassName: String,
) : DialogWrapper(project) {

  private val rootDirCombo: ComboBoxWithWidePopup<PsiDirectory> =
    ComboBoxWithWidePopup(psiRootDirs).apply {
      renderer = CreateServiceClassFixBase.PsiDirectoryListCellRenderer()
    }

  var isSubclass: Boolean = true
    private set
  val rootDir: PsiDirectory?
    get() = rootDirCombo.selectedItem as PsiDirectory?

  init {
    title = QuickFixBundle.message("create.service.implementation")
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    buttonsGroup {
      row(JavaBundle.message("label.implementation")) {
        radioButton(JavaBundle.message("radio.button.subclass.of.0", superClassName), true)
          .focused()
      }
      row("") {
        radioButton(JavaBundle.message("radio.button.with.provider.method"), false)
      }
    }.bind(::isSubclass)

    if (psiRootDirs.size > 1) {
      row(CommonBundle.message("label.source.root") + ":") {
        cell(rootDirCombo).align(AlignX.FILL)
      }
    }
  }
}
