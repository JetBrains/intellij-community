// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.extractModule

import com.intellij.CommonBundle
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.graph.GraphAlgorithms
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_MODULE_ENTITY_TYPE_ID_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.walk

private val LOG = logger<ExtractModuleService>()

internal class DependentModule(
  val module: Module,
  val stillDependsOnOldModule: Boolean,
)

internal suspend fun compilerOutputPath(module: Module): Path? = readAction {
  CompilerModuleExtension.getInstance(module)?.compilerOutputPath?.toNioPath()
}

internal suspend fun Path.forEachClassfile(action: suspend (Path) -> Unit) {
  walk().filter { it.extension == "class" }.forEach { path ->
    action(path)
  }
}

@Service(Service.Level.PROJECT)
class ExtractModuleService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  @RequiresEdt
  fun analyzeDependenciesAndCreateModuleInBackground(
    directory: PsiDirectory,
    module: Module,
    moduleName: @NlsSafe String,
    targetSourceRootPath: String?,
  ) {
    CompilerManager.getInstance(project).make { aborted, errors, _, _ ->
      if (aborted || errors > 0) {
        return@make
      }
      coroutineScope.launch {
        analyzeDependenciesAndCreateModule(directory, module, moduleName, targetSourceRootPath)
      }
    }
  }

  @OptIn(ExperimentalPathApi::class)
  private suspend fun analyzeDependenciesAndCreateModule(
    directory: PsiDirectory,
    module: Module,
    moduleName: @NlsSafe String,
    targetSourceRootPath: String?,
  ) {
    withBackgroundProgress(project, JavaUiBundle.message("progress.title.extract.module.analyzing.dependencies", directory.name)) {
      val usedModules = LinkedHashSet<Module>()
      val usedLibraries = LinkedHashSet<Library>()
      val compilerOutputPath = compilerOutputPath(module) ?: return@withBackgroundProgress

      val packageName = readAction {
        JavaDirectoryService.getInstance().getPackage(directory)?.qualifiedName
      } ?: return@withBackgroundProgress
      val compiledPackagePath = packageName.replace('.', '/').let { compilerOutputPath.resolve(it) }

      val fileProcessor = ExtractModuleFileProcessor()
      compiledPackagePath.forEachClassfile { path ->
        fileProcessor.processFile(path)
      }

      readAction {
        val fileIndex = ProjectFileIndex.getInstance(module.project)
        fileProcessor.referencedClasses.forEach { className ->
          val file = JavaPsiFacade.getInstance(module.project).findClass(className, module.getModuleWithDependenciesAndLibrariesScope(
            false))?.containingFile
          val virtualFile = file?.virtualFile ?: return@forEach
          val depModule = fileIndex.getModuleForFile(virtualFile)
          if (depModule != null) {
            usedModules.add(depModule)
            return@forEach
          }
          val library = fileIndex.getOrderEntriesForFile(virtualFile).asSequence()
            .filterIsInstance<LibraryOrderEntry>().filter { !it.isModuleLevel }.mapNotNull { it.library }.firstOrNull()
          if (library != null) {
            usedLibraries.add(library)
          }
        }
      }

      try {
        val allDependentModules = readAction {
          collectDependentModules(module)
        }
        val packageClasses = fileProcessor.gatheredClassLinks.keys.filterTo(HashSet()) { it.startsWith("$packageName.") }
        val moduleClasses = ExtractModuleFileProcessor().let { fileProcessor ->
          compilerOutputPath.forEachClassfile { path ->
            fileProcessor.processFile(path)
          }
          fileProcessor.gatheredClassLinks.keys.filterTo(HashSet()) { it !in packageClasses }
        }

        val packageDependentModules = filterDependentModules(allDependentModules, packageClasses, moduleClasses)
        writeAction {
          extractModule(directory, module, moduleName, usedModules, usedLibraries, targetSourceRootPath, packageDependentModules)
        }
        ModuleDependenciesCleaner(module, usedModules, compilerOutputPath, compiledPackagePath).startInBackground()
      }
      catch (e: Throwable) {
        if (e !is ControlFlowException) {
          LOG.info(e)
          Messages.showErrorDialog(project, JavaUiBundle.message("dialog.message.failed.to.extract.module", e),
                                   CommonBundle.getErrorTitle())
        }
      }

    }
  }

  @OptIn(ExperimentalPathApi::class)
  private suspend fun filterDependentModules(dependentModules: Set<Module>, packageClasses: Set<String>, moduleClasses: HashSet<String>) =
    dependentModules.mapNotNull {
      val compilerOutputPath = compilerOutputPath(it) ?: return@mapNotNull null
      val fileProcessor = ExtractModuleFileProcessor()

      compilerOutputPath.forEachClassfile { path ->
        withContext(Dispatchers.IO) {
          fileProcessor.processFile(path)
        }
      }
      val packageDependency = fileProcessor.referencedClasses.any { it in packageClasses }
      if (!packageDependency) return@mapNotNull null
      val stillDependsOnModule = fileProcessor.referencedClasses.any { it in moduleClasses }
      DependentModule(it, stillDependsOnModule)
    }

  @RequiresReadLock
  private fun collectDependentModules(module: Module): Set<Module> {
    val moduleGraph = ModuleManager.getInstance(project).moduleGraph()
    val dependentModules = LinkedHashSet<Module>()
    GraphAlgorithms.getInstance().collectOutsRecursively(moduleGraph, module, dependentModules)
    return dependentModules
  }

  @RequiresWriteLock
  private fun extractModule(
    directory: PsiDirectory,
    module: Module,
    moduleName: @NlsSafe String,
    usedModules: Set<Module>,
    usedLibraries: Set<Library>,
    targetSourceRootPath: String?,
    packageDependentModules: List<DependentModule>,
  ) {
    val packagePrefix = JavaDirectoryService.getInstance().getPackage(directory)?.qualifiedName ?: ""
    val targetSourceRoot = targetSourceRootPath?.let { VfsUtil.createDirectories(it) }
    val (contentRoot, imlFileDirectory) = if (targetSourceRoot != null) {
      val parent = targetSourceRoot.parent
      if (parent in ModuleRootManager.getInstance(module).contentRoots) targetSourceRoot to module.moduleNioFile.parent
      else parent to parent.toNioPath()
    }
    else {
      directory.virtualFile to module.moduleNioFile.parent
    }

    val newModule = ModuleManager.getInstance(module.project).newModule(imlFileDirectory.resolve("$moduleName.iml"),
                                                                        JAVA_MODULE_ENTITY_TYPE_ID_NAME)

    ModuleRootModificationUtil.updateModel(newModule) { model ->
      if (ModuleRootManager.getInstance(module).isSdkInherited) {
        model.inheritSdk()
      }
      else {
        model.sdk = ModuleRootManager.getInstance(module).sdk
      }
      val contentEntry = model.addContentEntry(contentRoot)
      if (targetSourceRoot != null) {
        contentEntry.addSourceFolder(targetSourceRoot, false)
      }
      else {
        contentEntry.addSourceFolder(directory.virtualFile, false, packagePrefix)
      }
      val moduleDependencies = JavaProjectDependenciesAnalyzer.removeDuplicatingDependencies(usedModules)
      moduleDependencies.forEach { model.addModuleOrderEntry(it) }
      val exportedLibraries = HashSet<Library>()
      for (moduleDependency in moduleDependencies) {
        ModuleRootManager.getInstance(moduleDependency).orderEntries().exportedOnly().recursively().forEachLibrary {
          exportedLibraries.add(it)
        }
      }
      (usedLibraries - exportedLibraries).forEach { model.addLibraryEntry(it) }
    }

    packageDependentModules.forEach { dependentModule ->
      ModuleRootModificationUtil.updateModel(dependentModule.module) { model ->
        model.addModuleOrderEntry(newModule)
        if (!dependentModule.stillDependsOnOldModule) {
          model.findModuleOrderEntry(module)?.let { orderEntry ->
            model.removeOrderEntry(orderEntry)
          } ?: LOG.error("Could not find module order entry for $module in ${dependentModule.module}")
        }
      }
    }

    if (targetSourceRoot != null) {
      val targetDirectory = VfsUtil.createDirectoryIfMissing(targetSourceRoot, packagePrefix.replace('.', '/'))
      MoveClassesOrPackagesUtil.moveDirectoryRecursively(directory,
                                                         PsiManager.getInstance(module.project).findDirectory(targetDirectory.parent))
    }
    SaveAndSyncHandler.getInstance().scheduleProjectSave(module.project)
  }

  @TestOnly
  suspend fun extractModuleFromDirectory(directory: PsiDirectory, module: Module, moduleName: @NlsSafe String, targetSourceRoot: String?) {
    analyzeDependenciesAndCreateModule(directory, module, moduleName, targetSourceRoot)
  }
}
