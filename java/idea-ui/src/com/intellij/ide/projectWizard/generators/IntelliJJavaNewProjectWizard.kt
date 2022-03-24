// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logContentRootChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logModuleFileLocationChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logModuleNameChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizardData.Companion.addSampleCode
import com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizardData.Companion.contentRoot
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.wizard.GitNewProjectWizardData.Companion.gitData
import com.intellij.ide.wizard.chain
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Paths

class IntelliJJavaNewProjectWizard : BuildSystemJavaNewProjectWizard {
  override val name = "IntelliJ"

  override fun createStep(parent: JavaNewProjectWizard.Step) = Step(parent).chain(::AssetsStep)

  class Step(parent: JavaNewProjectWizard.Step) :
    IntelliJNewProjectWizardStep<JavaNewProjectWizard.Step>(parent),
    BuildSystemJavaNewProjectWizardData by parent,
    IntelliJJavaNewProjectWizardData {

    override fun setupProject(project: Project) {
      val builder = JavaModuleBuilder()
      val moduleFile = Paths.get(moduleFileLocation, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION)

      builder.name = moduleName
      builder.moduleFilePath = FileUtil.toSystemDependentName(moduleFile.toString())
      builder.contentEntryPath = FileUtil.toSystemDependentName(contentRoot)

      if (parent.context.isCreatingNewProject) {
        // New project with a single module: set project JDK
        parent.context.projectJdk = sdk
      }
      else {
        // New module in an existing project: set module JDK
        val sameSDK = ProjectRootManager.getInstance(project).projectSdk?.name == sdk?.name
        builder.moduleJdk = if (sameSDK) null else sdk
      }

      builder.commit(project)

      logSdkFinished(sdk)
    }

    init {
      data.putUserData(IntelliJJavaNewProjectWizardData.KEY, this)

      sdkProperty.afterChange { logSdkChanged(it) }
      addSampleCodeProperty.afterChange { logAddSampleCodeChanged() }
      moduleNameProperty.afterChange { logModuleNameChanged() }
      contentRootProperty.afterChange { logContentRootChanged() }
      moduleFileLocationProperty.afterChange { logModuleFileLocationChanged() }
    }
  }

  private class AssetsStep(parent: Step) : AssetsNewProjectWizardStep(parent) {
    override fun setupAssets(project: Project) {
      outputDirectory = contentRoot
      if (gitData?.git == true) {
        addAssets(StandardAssetsProvider().getIntelliJIgnoreAssets())
      }
      if (addSampleCode) {
        withJavaSampleCodeAsset("src", "")
      }
    }
  }
}