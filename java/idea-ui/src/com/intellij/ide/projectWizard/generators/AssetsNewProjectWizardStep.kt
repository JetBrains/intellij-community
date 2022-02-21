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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.util.StringJoiner

@ApiStatus.Experimental
abstract class AssetsNewProjectWizardStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

  lateinit var outputDirectory: String
  private val assets = ArrayList<GeneratorAsset>()
  private val templateProperties = HashMap<String, Any>()
  private val filesToOpen = HashSet<String>()

  fun addAssets(vararg assets: GeneratorAsset) =
    addAssets(assets.toList())

  fun addAssets(assets: Iterable<GeneratorAsset>) {
    this.assets.addAll(assets)
  }

  fun addTemplateProperties(vararg properties: Pair<String, Any>) =
    addTemplateProperties(properties.toMap())

  fun addTemplateProperties(properties: Map<String, Any>) {
    templateProperties.putAll(properties)
  }

  fun addFilesToOpen(vararg relativeCanonicalPaths: String) =
    addFilesToOpen(relativeCanonicalPaths.toList())

  fun addFilesToOpen(relativeCanonicalPaths: Iterable<String>) {
    filesToOpen.addAll(relativeCanonicalPaths.map { "$outputDirectory/$it" })
  }

  abstract fun setupAssets(project: Project)

  override fun setupProject(project: Project) {
    setupAssets(project)

    WriteAction.runAndWait<Throwable> {
      try {
        val generatedFiles = AssetsProcessor().generateSources(outputDirectory, assets, templateProperties)
        runWhenCreated(project) { //IDEA-244863
          reformatCode(project, generatedFiles)
          openFilesInEditor(project, generatedFiles.filter { it.path in filesToOpen })
        }
      }
      catch (e: IOException) {
        logger<NewProjectWizardStep>().error("Unable generating sources", e)
      }
    }
  }

  private fun runWhenCreated(project: Project, action: () -> Unit) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      action()
    }
    else {
      StartupManager.getInstance(project).runAfterOpened {
        ApplicationManager.getApplication().invokeLater(action, project.disposed)
      }
    }
  }

  private fun reformatCode(project: Project, files: List<VirtualFile>) {
    val psiManager = PsiManager.getInstance(project)
    val generatedPsiFiles = files.mapNotNull { psiManager.findFile(it) }
    ReformatCodeProcessor(project, generatedPsiFiles.toTypedArray(), null, false).run()
  }

  private fun openFilesInEditor(project: Project, files: List<VirtualFile>) {
    val fileEditorManager = FileEditorManager.getInstance(project)
    val projectView = ProjectView.getInstance(project)
    for (file in files) {
      fileEditorManager.openFile(file, true)
      projectView.select(null, file, false)
    }
  }

  companion object {
    fun AssetsNewProjectWizardStep.withJavaSampleCodeAsset(sourceRootPath: String, aPackage: String) {
      val templateManager = FileTemplateManager.getDefaultInstance()
      val template = templateManager.getInternalTemplate("SampleCode")
      val packageDirectory = aPackage.replace('.', '/')
      val pathJoiner = StringJoiner("/")
      if (sourceRootPath.isNotEmpty()) {
        pathJoiner.add(sourceRootPath)
      }
      if (packageDirectory.isNotEmpty()) {
        pathJoiner.add(packageDirectory)
      }
      pathJoiner.add("Main.java")
      val path = pathJoiner.toString()

      addAssets(GeneratorTemplateFile(path, template))
      addFilesToOpen(path)
      addTemplateProperties("PACKAGE_NAME" to aPackage)
    }
  }
}