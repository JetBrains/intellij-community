// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.extractModule

import com.intellij.CommonBundle
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.mapWithProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil
import com.intellij.task.ProjectTaskManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_MODULE_ENTITY_TYPE_ID_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.*

private val LOG = logger<ExtractModuleService>()

internal enum class ModuleDependencyScope {
  PRODUCTION,
  TEST,
  NONE
}

internal class DependentModule(
  val module: Module,
  val extractedPackageDependencyScope: ModuleDependencyScope,
  val oldModuleDependencyScope: ModuleDependencyScope,
)

private fun compilerOutputs(module: Module, includeTests: Boolean): List<Path> {
  return CompilerModuleExtension.getInstance(module)
    ?.getOutputRoots(/* includeTests = */ includeTests)
    ?.map { it.toNioPath() } ?: emptyList()
}

internal suspend fun compilerOutputPath(module: Module): List<Path> = readAction {
  compilerOutputs(module, includeTests = false)
}

internal suspend fun compilerOutputPathForTests(module: Module): List<Path> = readAction {
  /**
   * Temporary use this workaround due to delegating build to Bazel overrides only
   * [CompilerModuleExtension.getOutputRoots] and [CompilerModuleExtension.getOutputRootUrls]
   *
   * After switching monorepo compilation fully to Bazel without JPS model, this
   * code can be reverted to origin state of calling [CompilerModuleExtension.getCompilerOutputPathForTests]
   */
  @Suppress("ConvertArgumentToSet")
  compilerOutputs(module, includeTests = true) - compilerOutputs(module, includeTests = false)
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
    ProjectTaskManager.getInstance(project).buildAllModules().onSuccess {
      if (it.isAborted || it.hasErrors()) {
        return@onSuccess
      }

      coroutineScope.launch {
        withBackgroundProgress(project, JavaUiBundle.message("progress.title.extract.module.from.package", directory.name)) {
          analyzeDependenciesAndCreateModule(directory, module, moduleName, targetSourceRootPath)
        }
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
    reportSequentialProgress(6) { progressReporter ->
      val usedModules = LinkedHashSet<Module>()
      val usedLibraries = LinkedHashSet<Library>()
      val compilerOutputPaths = compilerOutputPath(module)
      if (compilerOutputPaths.isEmpty()) return@reportSequentialProgress

      val packageName = readAction {
        JavaDirectoryService.getInstance().getPackage(directory)?.qualifiedName
      } ?: return@reportSequentialProgress
      val packageRelativePathPrefix = packageName.replace('.', '/') + "/"

      progressReporter.itemStep(JavaUiBundle.message("progress.step.extract.module.collecting.used.classes", directory.name))

      val packageFileProcessor = ExtractModuleFileProcessor()
      val moduleFileProcessor = ExtractModuleFileProcessor()

      for (outputPath in compilerOutputPaths) {
        withClassRootEntries(outputPath) { entries ->
          for (entry in entries) {
            if (entry.entryName.startsWith(packageRelativePathPrefix)) {
              packageFileProcessor.processFile(entry.path)
            }
            else {
              moduleFileProcessor.processFile(entry.path)
            }
          }
        }
      }

      progressReporter.itemStep(JavaUiBundle.message("progress.step.extract.module.building.dependencies"))
      readAction {
        val fileIndex = ProjectFileIndex.getInstance(module.project)
        packageFileProcessor.referencedClasses.forEach { className ->
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
        progressReporter.itemStep(JavaUiBundle.message("progress.step.extract.module.collecting.dependent.modules", module.name))
        val allDependentModules = readAction {
          collectDependentModules(module)
        }
        val packageClasses = packageFileProcessor.gatheredClassLinks.keys.filterTo(HashSet()) { it.startsWith("$packageName.") }
        val moduleClasses = moduleFileProcessor.gatheredClassLinks.keys.filterTo(HashSet()) { it !in packageClasses }

        progressReporter.itemStep(JavaUiBundle.message("progress.step.extract.module.analyzing.dependent.modules"))
        val packageDependentModules = filterDependentModules(allDependentModules, packageClasses, moduleClasses)

        progressReporter.itemStep(JavaUiBundle.message("progress.step.extract.module.preparing.to.extract"))
        val dependencyCleaner = ModuleDependenciesCleaner(module, usedModules)
        val dependenciesToRemove = dependencyCleaner.findDependenciesToRemove(moduleFileProcessor)
        progressReporter.itemStep(JavaUiBundle.message("progress.step.extract.module.extracting"))
        writeAction {
          extractModule(directory, module, moduleName, usedModules, usedLibraries, targetSourceRootPath, packageDependentModules)
          dependencyCleaner.removeDependencies(dependenciesToRemove)
        }
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
  private suspend fun filterDependentModules(dependentModules: Set<Module>, packageClasses: Set<String>, moduleClasses: Set<String>) =
    dependentModules.mapWithProgress { dependentModule ->
      val compilerOutputPath = compilerOutputPath(dependentModule)
      val compilerOutputPathForTests = compilerOutputPathForTests(dependentModule)
      if (compilerOutputPath.isEmpty() && compilerOutputPathForTests.isEmpty()) return@mapWithProgress null

      val (prodDependsOnPackage, prodDependsOnModule) = rootDependsOnPackageAndModule(compilerOutputPath, packageClasses, moduleClasses)
      if (prodDependsOnPackage && prodDependsOnModule) { // no need to check tests
        return@mapWithProgress DependentModule(dependentModule, ModuleDependencyScope.PRODUCTION, ModuleDependencyScope.PRODUCTION)
      }

      val (testDependsOnPackage, testDependsOnModule) = rootDependsOnPackageAndModule(compilerOutputPathForTests, packageClasses, moduleClasses)
      if (!prodDependsOnPackage && !testDependsOnPackage) { // no need to do anything
        return@mapWithProgress null
      }

      val extractedPackageDependencyScope = when {
        prodDependsOnPackage -> ModuleDependencyScope.PRODUCTION
        else -> ModuleDependencyScope.TEST
      }
      val oldModuleDependencyScope = when {
        prodDependsOnModule -> ModuleDependencyScope.PRODUCTION
        testDependsOnModule -> ModuleDependencyScope.TEST
        else -> ModuleDependencyScope.NONE
      }

      DependentModule(dependentModule, extractedPackageDependencyScope, oldModuleDependencyScope)
    }.filterNotNull()

  private suspend fun rootDependsOnPackageAndModule(
    paths: List<Path>, packageClasses: Set<String>, moduleClasses: Set<String>,
  ): Pair<Boolean, Boolean> {
    val fileProcessor = ExtractModuleFileProcessor()

    withContext(Dispatchers.IO) {
      for (outputPath in paths) {
        withClassRootEntries(outputPath) { entries ->
          for (entry in entries) {
            fileProcessor.processFile(entry.path)
          }
        }
      }
    }

    val packageDependency = fileProcessor.referencedClasses.any { it in packageClasses }
    val stillDependsOnModule = fileProcessor.referencedClasses.any { it in moduleClasses }

    return packageDependency to stillDependsOnModule
  }

  @RequiresReadLock
  private fun collectDependentModules(module: Module): Set<Module> {
    val dependentModules = LinkedHashSet<Module>()
    ModuleUtil.collectModulesDependsOn(module, dependentModules)
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
        val newModuleEntry = model.addModuleOrderEntry(newModule)
        if (dependentModule.extractedPackageDependencyScope == ModuleDependencyScope.TEST) {
          newModuleEntry.scope = DependencyScope.TEST
        }
        val oldModuleEntry = model.findModuleOrderEntry(module)
        if (dependentModule.oldModuleDependencyScope == ModuleDependencyScope.NONE) {
          oldModuleEntry?.let { orderEntry ->
            model.removeOrderEntry(orderEntry)
          } ?: LOG.error("Could not find module order entry for $module in ${dependentModule.module}")
        }
        else if (dependentModule.oldModuleDependencyScope == ModuleDependencyScope.TEST) {
          oldModuleEntry?.let {
            it.scope = DependencyScope.TEST
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

private data class ClassFileEntry(val entryName: String, val path: Path)

@OptIn(ExperimentalPathApi::class)
private fun <R> withClassRootEntries(classRoot: Path, block: (entries: Sequence<ClassFileEntry>) -> R): R {
  return withClassRoot(classRoot) { nioRoot ->
    val sequence = nioRoot
      .walk()
      .filter { path ->
        path.extension == "class" && !classRoot.relativize(path).startsWith("META-INF/")
      }
      .map { ClassFileEntry(it.relativeTo(nioRoot).invariantSeparatorsPathString, it) }
    block(sequence)
  }
}

private fun <R> withClassRoot(classRoot: Path, block: (root: Path) -> R): R {
  return when {
    classRoot.isDirectory() -> block(classRoot)
    classRoot.isRegularFile() && classRoot.extension == "jar" -> {
      FileSystems.newFileSystem(classRoot).use {
        block(it.rootDirectories.single())
      }
    }
    else -> error("Unsupported classes output root: $classRoot")
  }
}
