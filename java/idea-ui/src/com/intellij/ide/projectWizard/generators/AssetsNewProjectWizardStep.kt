// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorEmptyDirectory
import com.intellij.ide.starters.local.GeneratorFile
import com.intellij.ide.starters.local.GeneratorResourceFile
import com.intellij.ide.starters.local.GeneratorTemplateFile
import com.intellij.ide.starters.local.generator.AssetsProcessor
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardActivityKey
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.setupProjectSafe
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.refreshAndFindVirtualFileOrDirectory
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.observation.trackActivityBlocking
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.UIBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

  fun addFileAsset(path: String, content: String, vararg permissions: PosixFilePermission) {
    addFileAsset(path, content, permissions.toSet())
  }

  fun addFileAsset(path: String, content: String, permissions: Set<PosixFilePermission>) {
    addAssets(GeneratorFile(path, permissions, content))
  }

  fun addFileAsset(path: String, content: ByteArray, vararg permissions: PosixFilePermission) {
    addFileAsset(path, content, permissions.toSet())
  }

  fun addFileAsset(path: String, content: ByteArray, permissions: Set<PosixFilePermission>) {
    addAssets(GeneratorFile(path, permissions, content))
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

      val filesToReformat = invokeAndWaitIfNeeded {
        runWithModalProgressBlocking(project, UIBundle.message("label.project.wizard.new.assets.step.generate.sources.progress", project.name)) {
          generateSources(outputDirectory)
        }
      }

      StartupManager.getInstance(project).runAfterOpened { // IDEA-244863
        project.trackActivityBlocking(NewProjectWizardActivityKey) {
          val coroutineScope = CoroutineScopeService.getCoroutineScope(project)
          coroutineScope.launchTracked {
            reformatCode(project, filesToReformat)
          }
          coroutineScope.launchTracked {
            openFilesInEditor(project, filesToOpen)
          }
        }
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

  private suspend fun generateSources(outputDirectory: Path): List<Path> {
    return withContext(Dispatchers.IO) {
      blockingContext {
        val assetsProcessor = AssetsProcessor.getInstance()
        assetsProcessor.generateSources(outputDirectory, assets, templateProperties)
      }
    }
  }

  private suspend fun reformatCode(project: Project, files: List<Path>) {
    val virtualFiles = withContext(Dispatchers.IO) {
      blockingContext {
        files.mapNotNull { it.refreshAndFindVirtualFileOrDirectory() }
          .filter { it.isFile }
      }
    }
    val psiFiles = readAction {
      virtualFiles.mapNotNull { it.findPsiFile(project) }
    }
    blockingContext {
      ReformatCodeProcessor(project, psiFiles.toTypedArray(), null, false).run()
    }
  }

  private suspend fun openFilesInEditor(project: Project, files: List<Path>) {
    val virtualFiles = withContext(Dispatchers.IO) {
      blockingContext {
        files.mapNotNull { it.refreshAndFindVirtualFileOrDirectory() }
          .filter { it.isFile }
      }
    }
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val fileEditorManager = FileEditorManager.getInstance(project)
        for (file in virtualFiles) {
          fileEditorManager.openFile(file, true)
        }
      }
    }
    withContext(Dispatchers.EDT) {
      blockingContext {
        val projectView = ProjectView.getInstance(project)
        for (file in virtualFiles) {
          projectView.select(null, file, false)
        }
      }
    }
  }

  @Service(Service.Level.PROJECT)
  private class CoroutineScopeService(private val coroutineScope: CoroutineScope) {
    companion object {
      fun getCoroutineScope(project: Project): CoroutineScope {
        return project.service<CoroutineScopeService>().coroutineScope
      }
    }
  }
}