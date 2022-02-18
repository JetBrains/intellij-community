// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorTemplateFile
import com.intellij.ide.starters.local.generator.AssetsProcessor
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil.invokeLater
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Experimental
abstract class JavaAssetsNewProjectWizardStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

  abstract fun getOutputDirectory(): String

  abstract fun getAssets(): List<GeneratorAsset>

  open fun getTemplateProperties(): Map<String, Any> = emptyMap()

  open fun getFilePathsToOpen(): List<String> = emptyList()

  fun getJavaSampleCodeAsset(sourceRootPath: String, aPackage: String): GeneratorAsset {
    val templateManager = FileTemplateManager.getDefaultInstance()
    val template = templateManager.getInternalTemplate("SampleCode")
    val packageDirectory = aPackage.replace('.', '/')
    return GeneratorTemplateFile("$sourceRootPath/$packageDirectory/Main.java", template)
  }

  override fun setupProject(project: Project) {
    val outputDirectory = getOutputDirectory()
    val generatedFiles = WriteAction.computeAndWait<List<VirtualFile>, Throwable> {
      try {
        AssetsProcessor().generateSources(outputDirectory, getAssets(), getTemplateProperties())
      }
      catch (e: IOException) {
        logger<NewProjectWizardStep>().error("Unable generating sources", e)
        emptyList()
      }
    }

    StartupManager.getInstance(project).runAfterOpened {
      invokeLater(project) {
        val psiManager = PsiManager.getInstance(project)
        val generatedPsiFiles = generatedFiles.mapNotNull { psiManager.findFile(it) }
        ReformatCodeProcessor(project, generatedPsiFiles.toTypedArray(), null, false).run()

        val localFileSystem = LocalFileSystem.getInstance()
        val fileEditorManager = FileEditorManager.getInstance(project)
        val projectView = ProjectView.getInstance(project)
        val files = getFilePathsToOpen()
          .mapNotNull { localFileSystem.findFileByPath("$outputDirectory/$it") }
        for (file in files) {
          fileEditorManager.openFile(file, true)
          projectView.select(null, file, false)
        }
      }
    }
  }
}