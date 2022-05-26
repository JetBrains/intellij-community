// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.core.JavaPsiBundle
import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.toUiPathProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import com.intellij.project.isDirectoryBased
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBUI
import javax.swing.JPanel

internal class ProjectConfigurableUi(private val myProjectConfigurable: ProjectConfigurable,
                                     private val myProject: Project) {

  private val propertyGraph = PropertyGraph()

  private val nameProperty = propertyGraph.graphProperty { myProject.name }
  private val compilerOutputProperty = propertyGraph.graphProperty { "" }

  var projectName by nameProperty
  var projectCompilerOutput by compilerOutputProperty

  private lateinit var myProjectJdkConfigurable: ProjectJdkConfigurable
  private lateinit var myLanguageLevelCombo: LanguageLevelCombo
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

    buildPanel()

    myProjectJdkConfigurable.addChangeListener {
      myLanguageLevelCombo.sdkUpdated(myProjectJdkConfigurable.selectedProjectJdk, myProject.isDefault)
    }
    myLanguageLevelCombo.addActionListener {
      LanguageLevelProjectExtensionImpl.getInstanceImpl(myProject).currentLevel = myLanguageLevelCombo.selectedLevel
    }
  }

  private fun buildPanel() {
    myPanel = headerlessPanel()
  }

  fun Panel.titleAndNameField() {
    if (myProject.isDirectoryBased) {
      row {
        label(JavaUiBundle.message("project.structure.title"))
          .bold()
          .comment(JavaUiBundle.message("project.structure.comment"),
            120)
      }
        .bottomGap(BottomGap.SMALL)

      row(JavaUiBundle.message("project.structure.name")) {
        textField()
          .bindText(nameProperty)
          .columns(28)
      }
        .bottomGap(BottomGap.SMALL)
    }
  }

  fun headerlessPanel(): JPanel = panel {
    titleAndNameField()

    row(JavaUiBundle.message("project.structure.sdk")) {
      cell(myProjectJdkConfigurable.createComponent())
    }
      .bottomGap(BottomGap.SMALL)

    row(JavaUiBundle.message("module.module.language.level")) {
      cell(myLanguageLevelCombo)
      cell()
    }
      .layout(RowLayout.PARENT_GRID)
      .bottomGap(BottomGap.SMALL)

    row(JavaUiBundle.message("project.structure.compiler.output")) {
      textFieldWithBrowseButton()
        .bindText(compilerOutputProperty.toUiPathProperty())
        .onIsModified {
          if (!myProjectConfigurable.isFrozen)
            LanguageLevelProjectExtensionImpl.getInstanceImpl(myProject).currentLevel = myLanguageLevelCombo.selectedLevel
          return@onIsModified true
        }
        .horizontalAlign(HorizontalAlign.FILL)
        .comment(JavaUiBundle.message("project.structure.compiler.output.comment"), 100)
      cell()
    }
      .layout(RowLayout.PARENT_GRID)
  }.apply {
    withBorder(JBUI.Borders.empty(20, 20))
  }

  fun getPanel(): JPanel = myPanel

  fun getLanguageLevel(): LanguageLevel? = myLanguageLevelCombo.selectedLevel
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
      projectCompilerOutput = FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(compilerOutput))
    }
    myLanguageLevelCombo.reset(myProject)
    projectName = myProject.name
  }
}