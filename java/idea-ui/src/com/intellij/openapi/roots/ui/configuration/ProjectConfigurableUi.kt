// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.core.JavaPsiBundle
import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import com.intellij.project.isDirectoryBased
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

internal class ProjectConfigurableUi(private val myProjectConfigurable: ProjectConfigurable,
                                     private val myProject: Project) {

  private val myProjectName: JTextField = JTextField(40)
  private lateinit var myProjectJdkConfigurable: ProjectJdkConfigurable
  private lateinit var myLanguageLevelCombo: LanguageLevelCombo
  private lateinit var myProjectCompilerOutput: TextFieldWithBrowseButton
  private lateinit var myPanel: JPanel

  fun initComponents(modulesConfigurator: ModulesConfigurator, model: ProjectSdksModel) {
    myProjectJdkConfigurable = ProjectJdkConfigurable(modulesConfigurator.projectStructureConfigurable, model)

    myLanguageLevelCombo = object : LanguageLevelCombo(JavaPsiBundle.message("default.language.level.description")) {
      override fun getDefaultLevel(): LanguageLevel? {
        val sdk = myProjectJdkConfigurable.selectedProjectJdk ?: return null
        val version = JavaSdk.getInstance().getVersion(sdk)
        return version?.maxLanguageLevel
      }
    }
    myProjectCompilerOutput = TextFieldWithBrowseButton().apply {
      this.textField.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          if (myProjectConfigurable.isFrozen) return
          LanguageLevelProjectExtensionImpl.getInstanceImpl(myProject).currentLevel = myLanguageLevelCombo.selectedLevel
        }
      })
    }

    buildPanel()

    myProjectJdkConfigurable.addChangeListener {
      myLanguageLevelCombo.sdkUpdated(myProjectJdkConfigurable.selectedProjectJdk, myProject.isDefault)
    }
    myLanguageLevelCombo.addActionListener {
      LanguageLevelProjectExtensionImpl.getInstanceImpl(myProject).currentLevel = myLanguageLevelCombo.selectedLevel
    }
  }

  private fun buildPanel() {
    myPanel = panel {

      if (myProject.isDirectoryBased) {
        row {
          label(JavaUiBundle.message("project.structure.title"), bold = true).comment(JavaUiBundle.message("project.structure.comment"), 120)
        }

        row(JavaUiBundle.message("project.structure.name")) {
          myProjectName()
        }
      }

      titledRow(JavaUiBundle.message("project.structure.sdks")) {
        row(JavaUiBundle.message("project.structure.java.sdk")) {
          myProjectJdkConfigurable.createComponent()()
        }
      }

      titledRow(JavaUiBundle.message("project.structure.java")) {
        row(JavaUiBundle.message("module.module.language.level")) {
          myLanguageLevelCombo()
        }
        row(JavaUiBundle.message("project.structure.compiler.output")) {
          myProjectCompilerOutput().comment(JavaUiBundle.message("project.structure.compiler.output.comment"), 84)
        }
      }
    }

    myPanel.preferredSize = JBUI.size(700, 500)
    myPanel.border = JBUI.Borders.empty(20)
  }

  fun getPanel(): JPanel = myPanel

  fun getProjectName(): String = myProjectName.text
  fun getProjectCompilerOutput(): String = myProjectCompilerOutput.text
  fun getLanguageLevel(): LanguageLevel = myLanguageLevelCombo.selectedLevel
  fun isDefaultLanguageLevel(): Boolean = myLanguageLevelCombo.isDefault
  fun isProjectJdkConfigurableModified(): Boolean = myProjectJdkConfigurable.isModified
  fun applyProjectJdkConfigurable() = myProjectJdkConfigurable.apply()

  fun reloadJdk() {
    myProjectJdkConfigurable.createComponent()
  }

  fun disposeUIResources() {
    myProjectJdkConfigurable.disposeUIResources()
  }

  fun reset(compilerOutput: String?) {
    myProjectJdkConfigurable.reset()
    if (compilerOutput != null) {
      myProjectCompilerOutput.text = FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(compilerOutput))
    }
    myLanguageLevelCombo.reset(myProject)
    myProjectName.text = myProject.name
  }
}