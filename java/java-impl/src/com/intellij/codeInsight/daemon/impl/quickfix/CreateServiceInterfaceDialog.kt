// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.CommonBundle
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.ide.actions.TemplateKindCombo
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDirectory
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

internal class CreateServiceInterfaceDialog(
  project: Project?,
  private val interfaceName: @NlsSafe String,
  private val psiRootDirs: Map<Module, Array<PsiDirectory>>,
) : DialogWrapper(project) {

  private val moduleCombo: ComboBoxWithWidePopup<Module> =
    ComboBoxWithWidePopup(psiRootDirs.keys.sortedBy { it.name }.toTypedArray()).apply {
      renderer = textListCellRenderer("") { it.name }
      addActionListener { updateRootDirsCombo() }
    }

  private val rootDirCombo: ComboBoxWithWidePopup<PsiDirectory> =
    ComboBoxWithWidePopup<PsiDirectory>().apply {
      renderer = CreateServiceClassFixBase.PsiDirectoryListCellRenderer()
    }

  private val kindCombo: TemplateKindCombo = TemplateKindCombo().apply {
    for (kind in listOf(CreateClassKind.CLASS, CreateClassKind.INTERFACE, CreateClassKind.ANNOTATION)) {
      addItem(StringUtil.capitalize(kind.description), kind.kindIcon, kind.name)
    }
  }

  val rootDir: PsiDirectory?
    get() = rootDirCombo.selectedItem as PsiDirectory?

  val classKind: CreateClassKind
    get() = CreateClassKind.valueOf(kindCombo.selectedName)

  init {
    title = QuickFixBundle.message("create.service")
    updateRootDirsCombo()
    init()
  }

  private fun updateRootDirsCombo() {
    val module = moduleCombo.selectedItem as Module?
    val moduleRootDirs = psiRootDirs[module] ?: PsiDirectory.EMPTY_ARRAY
    rootDirCombo.model = DefaultComboBoxModel(moduleRootDirs)
  }

  override fun createCenterPanel(): JComponent = panel {
    row(CommonBundle.message("label.name") + ":") {
      textField()
        .text(interfaceName)
        .align(AlignX.FILL)
        .applyToComponent { isEditable = false }
    }
    if (moduleCombo.model.size > 1) {
      row(CommonBundle.message("label.module") + ":") {
        cell(moduleCombo).align(AlignX.FILL)
      }
    }
    if (rootDirCombo.model.size > 1) {
      row(CommonBundle.message("label.source.root") + ":") {
        cell(rootDirCombo).align(AlignX.FILL)
      }
    }
    row(CommonBundle.message("label.kind") + ":") {
      cell(kindCombo).align(AlignX.FILL)
    }
  }
}
