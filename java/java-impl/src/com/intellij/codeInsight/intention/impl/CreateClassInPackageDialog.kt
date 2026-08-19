// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl

import com.intellij.CommonBundle
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind
import com.intellij.codeInsight.daemon.impl.quickfix.CreateServiceClassFixBase
import com.intellij.ide.actions.TemplateKindCombo
import com.intellij.java.JavaBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.util.PsiUtil
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

internal class CreateClassInPackageDialog(
  private val project: Project,
  rootDirs: Array<PsiDirectory>,
  private val packageName: String,
) : DialogWrapper(project) {

  private lateinit var nameField: JBTextField

  private val rootDirCombo = ComboBoxWithWidePopup<PsiDirectory>().apply {
    renderer = CreateServiceClassFixBase.PsiDirectoryListCellRenderer()
    model = DefaultComboBoxModel(rootDirs)
  }

  private val kindCombo = TemplateKindCombo().apply {
    for (kind in CreateClassKind.entries) {
      addItem(StringUtil.capitalize(kind.description), kind.kindIcon, kind.name)
    }
  }

  init {
    title = JavaBundle.message("dialog.title.create.class.in.package")
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    row(CommonBundle.message("label.name") + ":") {
      nameField = textField()
        .align(AlignX.FILL)
        .comment(JavaBundle.message("comment.the.class.will.be.created.in.the.package.0", packageName))
        .component
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

  override fun getPreferredFocusedComponent(): JComponent = nameField

  // Force continuousValidation, for custom validation in [doValidate]
  override fun continuousValidation(): Boolean = true

  override fun doValidate(): ValidationInfo? {
    val dir = rootDir
    val level = if (dir != null) PsiUtil.getLanguageLevel(dir) else LanguageLevel.HIGHEST
    if (PsiNameHelper.getInstance(project).isIdentifier(name, level)) {
      return null
    }
    return ValidationInfo(JavaBundle.message("error.text.this.is.not.a.valid.java.class.name"), nameField)
  }

  val name: String get() = nameField.text.trim()

  val rootDir: PsiDirectory? get() = rootDirCombo.selectedItem as? PsiDirectory

  val kind: CreateClassKind get() = CreateClassKind.valueOf(kindCombo.selectedName)
}
