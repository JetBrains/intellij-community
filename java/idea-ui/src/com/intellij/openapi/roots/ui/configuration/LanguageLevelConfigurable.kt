// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModuleExtension
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl
import com.intellij.pom.java.LanguageLevel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

abstract class LanguageLevelConfigurable(project: Project?, onChange: Runnable) : UnnamedConfigurable {

  private var languageLevelCombo: LanguageLevelCombo?
  private var panel: JPanel?

  init {
    languageLevelCombo = object : LanguageLevelCombo(JavaUiBundle.message("project.language.level.combo.item")) {
      override val defaultLevel: LanguageLevel
        get() = LanguageLevelProjectExtensionImpl.getInstanceImpl(project).currentLevel
    }.apply {
      preferredSize = Dimension(300, preferredSize.height)
      addActionListener {
        val languageLevel = if (isDefault) null else selectedLevel
        languageLevelExtension.languageLevel = languageLevel
        onChange.run()
      }
    }

    panel = panel {
      row(JavaUiBundle.message("module.module.language.level")) {
        cell(languageLevelCombo!!)
          .resizableColumn()
          .align(Align.FILL)
      }
    }.withBorder(JBUI.Borders.empty(0, UIUtil.DEFAULT_HGAP))
  }

  override fun createComponent(): JComponent = panel!!

  override fun isModified(): Boolean = (languageLevelExtension as ModuleExtension).isChanged

  @Throws(ConfigurationException::class)
  override fun apply() {}

  override fun reset() {
    languageLevelCombo?.selectedItem = languageLevelExtension.languageLevel
  }

  override fun disposeUIResources() {
    panel = null
    languageLevelCombo = null
  }

  abstract val languageLevelExtension: LanguageLevelModuleExtension
}
