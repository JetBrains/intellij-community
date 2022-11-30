// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.INTELLIJ
import com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizardData.Companion.addSampleCode
import com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizardData.Companion.contentRoot
import com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizardData.Companion.javaData
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.wizard.chain
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Paths

class IntelliJJavaNewProjectWizard : BuildSystemJavaNewProjectWizard {

  override val name = INTELLIJ

  override val ordinal = 0

  override fun createStep(parent: JavaNewProjectWizard.Step) = Step(parent).chain(::AssetsStep)

  class Step(parent: JavaNewProjectWizard.Step) :
    IntelliJNewProjectWizardStep<JavaNewProjectWizard.Step>(parent),
    BuildSystemJavaNewProjectWizardData by parent,
    IntelliJJavaNewProjectWizardData {

    override fun setupProject(project: Project) {
      super.setupProject(project)

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
    }

    init {
      data.putUserData(IntelliJJavaNewProjectWizardData.KEY, this)
    }
  }

  private class AssetsStep(parent: Step) : AssetsNewProjectWizardStep(parent) {
    override fun setupAssets(project: Project) {
      outputDirectory = contentRoot
      addAssets(StandardAssetsProvider().getIntelliJIgnoreAssets())
      if (addSampleCode) {
        withJavaSampleCodeAsset("src", "", javaData.generateOnboardingTips)
      }
    }

    override fun setupProject(project: Project) {
      super.setupProject(project)
      if (javaData.generateOnboardingTips) {
        prepareTipsInEditor(project)
      }
    }
  }
}