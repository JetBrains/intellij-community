// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.INTELLIJ
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.MODIFIABLE_MODULE_MODEL_KEY
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloader
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.dsl.builder.Panel
import kotlinx.coroutines.launch
import java.nio.file.Paths

class IntelliJJavaNewProjectWizard : BuildSystemJavaNewProjectWizard {

  override val name = INTELLIJ

  override val ordinal = 0

  override fun createStep(parent: JavaNewProjectWizard.Step): NewProjectWizardStep =
    Step(parent)
      .nextStep(::AssetsStep)

  class Step(parent: JavaNewProjectWizard.Step) :
    IntelliJNewProjectWizardStep<JavaNewProjectWizard.Step>(parent),
    BuildSystemJavaNewProjectWizardData by parent,
    IntelliJJavaNewProjectWizardData {

    override fun setupSettingsUI(builder: Panel) {
      setupJavaSdkUI(builder)
      setupSampleCodeUI(builder)
      setupSampleCodeWithOnBoardingTipsUI(builder)
    }

    override fun setupAdvancedSettingsUI(builder: Panel) {
      setupModuleNameUI(builder)
      setupModuleContentRootUI(builder)
      setupModuleFileLocationUI(builder)
    }

    override fun setupProject(project: Project) {
      val builder = JavaModuleBuilder()
      val moduleFile = Paths.get(moduleFileLocation, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION)

      builder.name = moduleName
      builder.moduleFilePath = FileUtil.toSystemDependentName(moduleFile.toString())
      builder.contentEntryPath = FileUtil.toSystemDependentName(contentRoot)

      val localTask = sdkDownloadTask
      if (localTask is JdkDownloadTask) {
        // Download the SDK on project creation
        (SdkDownload.EP_NAME.findFirstSafe { it is JdkDownloader } as? JdkDownloader)?.let { jdkDownloader ->

          project.coroutineScope.launch {
            withBackgroundProgress(project, JavaUiBundle.message("progress.title.downloading", (sdkDownloadTask as SdkDownloadTask).suggestedSdkName)) {
              val (selectedFile) = JdkInstaller.getInstance().validateInstallDir(localTask.plannedHomeDir)
              if (selectedFile != null) {
                jdkDownloader.prepareDownloadTask(project, localTask.jdkItem, selectedFile) { downloadTask ->
                  val model = ProjectStructureConfigurable.getInstance(project).projectJdksModel
                  project.coroutineScope.launch {
                    writeAction {
                      model.setupInstallableSdk(JavaSdkImpl.getInstance(), downloadTask) { sdk ->
                        ProjectJdkTable.getInstance().addJdk(sdk)
                        if (context.isCreatingNewProject) {
                          ProjectRootManager.getInstance(project).projectSdk = sdk
                        } else {
                          // TODO: Update module SDK
                        }
                      }
                    }
                  }
                }
              } else {
                // TODO: Show error notification
              }
            }
          }
        }
      }

      if (context.isCreatingNewProject) {
        // New project with a single module: set project JDK
        context.projectJdk = sdk
      }
      else {
        // New module in an existing project: set module JDK
        val sameSDK = ProjectRootManager.getInstance(project).projectSdk?.name == sdk?.name
        builder.moduleJdk = if (sameSDK) null else sdk
      }

      val model = context.getUserData(MODIFIABLE_MODULE_MODEL_KEY)
      builder.commit(project, model)
    }

    init {
      data.putUserData(IntelliJJavaNewProjectWizardData.KEY, this)
    }
  }

  private class AssetsStep(
    private val parent: Step
  ) : AssetsJavaNewProjectWizardStep(parent) {

    override fun setupAssets(project: Project) {
      outputDirectory = parent.contentRoot

      if (context.isCreatingNewProject) {
        addAssets(StandardAssetsProvider().getIntelliJIgnoreAssets())
      }

      if (parent.addSampleCode) {
        withJavaSampleCodeAsset("src", "", parent.generateOnboardingTips)
      }
    }

    override fun setupProject(project: Project) {
      if (parent.generateOnboardingTips) {
        prepareOnboardingTips(project)
      }
      super.setupProject(project)
    }
  }
}