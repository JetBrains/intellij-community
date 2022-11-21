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
import com.intellij.ide.wizard.setupProjectSafe
import com.intellij.ide.wizard.whenProjectCreated
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.UIBundle
import org.jetbrains.annotations.ApiStatus
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

  private fun addTemplateProperties(properties: Map<String, Any>) {
    templateProperties.putAll(properties)
  }

  fun addFilesToOpen(vararg relativeCanonicalPaths: String) =
    addFilesToOpen(relativeCanonicalPaths.toList())

  private fun addFilesToOpen(relativeCanonicalPaths: Iterable<String>) {
    filesToOpen.addAll(relativeCanonicalPaths.map { "$outputDirectory/$it" })
  }

  abstract fun setupAssets(project: Project)

  override fun setupProject(project: Project) {
    setupProjectSafe(project, UIBundle.message("error.project.wizard.new.project.sample.code", context.isCreatingNewProjectInt)) {
      setupAssets(project)

      val generatedFiles = invokeAndWaitIfNeeded {
        runWriteAction {
          AssetsProcessor().generateSources(outputDirectory, assets, templateProperties)
        }
      }
      whenProjectCreated(project) { //IDEA-244863
        reformatCode(project, generatedFiles)
        openFilesInEditor(project, generatedFiles.filter { it.path in filesToOpen })
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