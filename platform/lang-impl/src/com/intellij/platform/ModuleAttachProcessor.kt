// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.CommonBundle
import com.intellij.conversion.ModuleSettings.MODULE_ROOT_MANAGER_COMPONENT
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.PrimaryModuleManager
import com.intellij.openapi.module.impl.ModuleManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ModuleAttachProcessor.Companion.getPrimaryModule
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectAttachProcessor.Companion.canAttachToProject
import com.intellij.projectImport.ProjectOpenedCallback
import com.intellij.util.io.directoryStreamIfExists
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText

private val LOG = logger<ModuleAttachProcessor>()

@InternalIgnoreDependencyViolation
class ModuleAttachProcessor : ProjectAttachProcessor() {
  @ApiStatus.Internal
  companion object {
    @JvmStatic
    fun getPrimaryModule(project: Project): Module? {
      return if (canAttachToProject()) PrimaryModuleManager.findPrimaryModule(project) else null
    }
  }

  override fun isEnabled(project: Project?, projectDir: Path?, newProject: Project?): Boolean {
    val dotIdeaDir = projectDir?.resolve(Project.DIRECTORY_STORE_FOLDER) ?: return false
    return Files.exists(dotIdeaDir) &&
           projectDir.directoryStreamIfExists({ path: Path ->
                                                path.fileName.toString().endsWith(ModuleManagerEx.IML_EXTENSION)
                                              }) { stream ->
             stream.any { imlFile ->
               imlFile.readText().contains(MODULE_ROOT_MANAGER_COMPONENT)
             }
           } ?: false
  }

  override suspend fun attachToProjectAsync(project: Project,
                                            projectDir: Path,
                                            callback: ProjectOpenedCallback?,
                                            beforeOpen: (suspend (Project) -> Boolean)?): Boolean {
    val dotIdeaDir = projectDir.resolve(Project.DIRECTORY_STORE_FOLDER)
    if (Files.notExists(dotIdeaDir)) {
      return false
    }

    LOG.info("Attaching directory: $projectDir")

    val newModule: Module? = try {
      findMainModule(project, dotIdeaDir) ?: findMainModule(project, projectDir)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error(e)
      withContext(Dispatchers.EDT) {
        Messages.showErrorDialog(project,
                                 LangBundle.message("module.attach.dialog.message.cannot.attach.project", e.message),
                                 CommonBundle.getErrorTitle())
      }
      return false
    }

    LifecycleUsageTriggerCollector.onProjectModuleAttached(project)

    if (newModule != null) {
      withContext(Dispatchers.EDT) {
        callback?.projectOpened(project, newModule)
      }
      return true
    }

    return withContext(Dispatchers.EDT) {
      Messages.showYesNoDialog(project,
                               LangBundle.message("module.attach.dialog.message.project.uses.non.standard.layout", projectDir),
                               LangBundle.message("module.attach.dialog.title.open.project"),
                               Messages.getQuestionIcon()) != Messages.YES
    }
  }

  override fun beforeDetach(module: Module) {
    module.project.messageBus.syncPublisher(ModuleAttachListener.TOPIC).beforeDetach(module)
  }
}

private suspend fun findMainModule(project: Project, projectDir: Path): Module? {
  return projectDir.directoryStreamIfExists({ path -> path.fileName.toString().endsWith(ModuleManagerEx.IML_EXTENSION) }) { directoryStream ->
    for (file in directoryStream) {
      return attachModule(project, file)
    }
    return null
  }
}

internal suspend fun attachModule(project: Project, imlFile: Path): Module {
  val moduleManager = ModuleManager.getInstance(project)
  val model = moduleManager.getModifiableModel()
  val module = model.loadModule(imlFile.invariantSeparatorsPathString)
  edtWriteAction {
    model.commit()
  }

  val newModule = readAction { moduleManager.findModuleByName(module.name)!! }
  val primaryModule = withContext(Dispatchers.EDT) { addPrimaryModuleDependency(project, newModule) }
  val tasks = mutableListOf<suspend () -> Unit>()
  module.project.messageBus.syncPublisher(ModuleAttachListener.TOPIC).afterAttach(newModule, primaryModule, imlFile, tasks)
  for (task in tasks) {
    runCatching { task() }.getOrLogException(LOG)
  }
  return newModule
}

private fun addPrimaryModuleDependency(project: Project, newModule: Module): Module? {
  val module = getPrimaryModule(project)
  if (module != null && module !== newModule) {
    ModuleRootModificationUtil.addDependency(module, newModule)
    return module
  }
  return null
}

/**
 * @param project the project
 * @return `null` if either multi-projects are not enabled or the project has only one module
 */
@NlsSafe
fun getMultiProjectDisplayName(project: Project): String? {
  if (!canAttachToProject()) {
    return null
  }

  val modules = ModuleManager.getInstance(project).modules
  if (modules.size <= 1) {
    return null
  }

  val primaryModule = getPrimaryModule(project) ?: modules.first()
  val result = StringBuilder(primaryModule.name)
    .append(", ")
    .append(modules.asSequence().filter { it !== primaryModule }.first().name)
  if (modules.size > 2) {
    result.append("...")
  }
  return result.toString()
}

internal fun getSortedModules(project: Project): List<Module> {
  val primaryModule = getPrimaryModule(project)
  val result = ArrayList<Module>()
  ModuleManager.getInstance(project).modules.filterTo(result) { it !== primaryModule}
  result.sortBy(Module::getName)
  primaryModule?.let {
    result.add(0, it)
  }
  return result
}
