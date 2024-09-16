// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorEmptyDirectory
import com.intellij.ide.starters.local.GeneratorResourceFile
import com.intellij.ide.starters.local.GeneratorTemplateFile
import com.intellij.ide.starters.local.generator.AssetsProcessor
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.setupProjectSafe
import com.intellij.ide.wizard.whenProjectCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.intellij.openapi.vfs.refreshAndFindVirtualFileOrDirectory
import com.intellij.psi.PsiManager
import com.intellij.ui.UIBundle
import org.jetbrains.annotations.ApiStatus
import java.net.URL
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

@ApiStatus.Experimental
abstract class AssetsNewProjectWizardStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

  private var outputDirectory: String? = null

  private val assets = ArrayList<GeneratorAsset>()
  private val templateProperties = HashMap<String, Any>()
  private val filesToOpen = HashSet<String>()

  @ApiStatus.Internal
  internal fun getTemplateProperties(): Map<String, Any> {
    return templateProperties
  }

  fun addAssets(vararg assets: GeneratorAsset) =
    addAssets(assets.toList())

  fun addAssets(assets: Iterable<GeneratorAsset>) {
    this.assets.addAll(assets)
  }

  fun addTemplateAsset(sourcePath: String, templateName: String, vararg properties: Pair<String, Any>) {
    addTemplateAsset(sourcePath, templateName, properties.toMap())
  }

  fun addTemplateAsset(sourcePath: String, templateName: String, properties: Map<String, Any>) {
    val templateManager = FileTemplateManager.getDefaultInstance()
    val template = templateManager.getInternalTemplate(templateName)
    addAssets(GeneratorTemplateFile(sourcePath, template))
    templateProperties.putAll(properties)
  }

  fun addResourceAsset(path: String, resource: URL, vararg permissions: PosixFilePermission) {
    addResourceAsset(path, resource, permissions.toSet())
  }

  fun addResourceAsset(path: String, resource: URL, permissions: Set<PosixFilePermission>) {
    addAssets(GeneratorResourceFile(path, permissions, resource))
  }

  fun addEmptyDirectoryAsset(path: String, vararg permissions: PosixFilePermission) {
    addEmptyDirectoryAsset(path, permissions.toSet())
  }

  fun addEmptyDirectoryAsset(path: String, permissions: Set<PosixFilePermission>) {
    addAssets(GeneratorEmptyDirectory(path, permissions))
  }

  fun addFilesToOpen(vararg relativeCanonicalPaths: String) =
    addFilesToOpen(relativeCanonicalPaths.toList())

  private fun addFilesToOpen(relativeCanonicalPaths: Iterable<String>) {
    for (relativePath in relativeCanonicalPaths) {
      filesToOpen.add(relativePath)
    }
  }

  abstract fun setupAssets(project: Project)

  override fun setupProject(project: Project) {
    setupProjectSafe(project, UIBundle.message("error.project.wizard.new.project.sample.code", context.isCreatingNewProjectInt)) {
      setupAssets(project)

      val outputDirectory = resolveOutputDirectory()
      val filesToOpen = resolveFilesToOpen(outputDirectory)

      val assetsProcessor = AssetsProcessor.getInstance()
      val generatedFiles = assetsProcessor.generateSources(outputDirectory, assets, templateProperties)

      whenProjectCreated(project) { //IDEA-244863
        reformatCode(project, generatedFiles.mapNotNull { it.refreshAndFindVirtualFileOrDirectory() }.filter { it.isFile })
        openFilesInEditor(project, filesToOpen.mapNotNull { it.refreshAndFindVirtualFile() })
      }
    }
  }

  fun setOutputDirectory(outputDirectory: String) {
    this.outputDirectory = outputDirectory
  }

  private fun resolveOutputDirectory(): Path {
    if (outputDirectory != null) {
      return Path.of(outputDirectory!!)
    }
    if (baseData != null) {
      return Path.of(baseData!!.path, baseData!!.name)
    }
    throw UninitializedPropertyAccessException("Cannot generate project files: unspecified output directory")
  }

  private fun resolveFilesToOpen(outputDirectory: Path): List<Path> {
    return filesToOpen.map { outputDirectory.resolve(it).normalize() }
  }

  private fun reformatCode(project: Project, files: List<VirtualFile>) {
    val psiManager = PsiManager.getInstance(project)
    val psiFiles = files.mapNotNull { psiManager.findFile(it) }

    ReformatCodeProcessor(project, psiFiles.toTypedArray(), null, false).run()
  }

  private fun openFilesInEditor(project: Project, files: List<VirtualFile>) {
    val fileEditorManager = FileEditorManager.getInstance(project)
    val projectView = ProjectView.getInstance(project)
    for (file in files) {
      fileEditorManager.openFile(file, true)
      projectView.select(null, file, false)
    }
  }
}