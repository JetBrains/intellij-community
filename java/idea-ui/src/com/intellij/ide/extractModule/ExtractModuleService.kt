// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.extractModule

import com.intellij.CommonBundle
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.debug
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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.mapWithProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.psi.JavaPsiFacade
import com.intellij.task.ProjectTaskManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.ide.legacyBridge.findLibraryBridge
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_MODULE_ENTITY_TYPE_ID_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi

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

interface TargetModuleCreator {
  @RequiresWriteLock
  fun createExtractedModule(originalModule: Module, directory: VirtualFile): ExtractedModuleData

  class ExtractedModuleData(val module: Module, val directoryToMoveClassesTo: VirtualFile?)
}

@Service(Service.Level.PROJECT)
class ExtractModuleService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  @RequiresEdt
  fun analyzeDependenciesAndCreateModuleInBackground(
    directory: VirtualFile,
    module: Module,
    targetModuleCreator: TargetModuleCreator,
  ) {
    ProjectTaskManager.getInstance(project).buildAllModules().onSuccess {
      if (it.isAborted || it.hasErrors()) {
        return@onSuccess
      }

      coroutineScope.launch {
        withBackgroundProgress(project, JavaUiBundle.message("progress.title.extract.module.from.package", directory.name)) {
          analyzeDependenciesAndCreateModule(directory, module, targetModuleCreator)
        }
      }
    }
  }

  @OptIn(ExperimentalPathApi::class)
  private suspend fun analyzeDependenciesAndCreateModule(
    directory: VirtualFile,
    module: Module,
    targetModuleCreator: TargetModuleCreator,
  ) {
    reportSequentialProgress(6) { progressReporter ->
      val usedModules = LinkedHashSet<Module>()
      val usedLibraries = LinkedHashSet<Library>()
      val compilerOutputPaths = compilerOutputPath(module)
      if (compilerOutputPaths.isEmpty()) return@reportSequentialProgress

      val packageName = readAction {
        PackageIndex.getInstance(project).getPackageName(directory)
      } ?: return@reportSequentialProgress
      val packageRelativePathPrefix = packageName.replace('.', '/') + "/"

      progressReporter.itemStep(JavaUiBundle.message("progress.step.extract.module.collecting.used.classes", directory.name))

      val moduleClasspathSet = LinkedHashSet<Path>()
      readAction {
        OrderEnumerator.orderEntries(module).productionOnly().recursively().exportedOnly().forEachModule { module ->
          moduleClasspathSet.addAll(compilerOutputs(module, includeTests = false))
        }
      }
      val moduleClasspath = moduleClasspathSet.toList()
      val packageFileProcessor = ExtractModuleFileProcessor(moduleClasspath)
      val moduleFileProcessor = ExtractModuleFileProcessor(moduleClasspath)

      for (outputPath in compilerOutputPaths) {
        packageFileProcessor.processClassFiles(outputPath) { it.startsWith(packageRelativePathPrefix) }
        moduleFileProcessor.processClassFiles(outputPath) { !it.startsWith(packageRelativePathPrefix) }
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
            if (depModule == module && className.startsWith("$packageName.")) {
              //skip references from classes which will be extracted
              return@forEach
            }
            if (usedModules.add(depModule)) {
              LOG.debug { "Module ${depModule.name} contains class $className referenced from some class under $packageName package" }
            }
            return@forEach
          }

          val library = fileIndex.findContainingLibraries(virtualFile).asSequence()
            .filterNot { it.tableId.level == JpsLibraryTableSerializer.MODULE_LEVEL }
            .firstNotNullOfOrNull { it.findLibraryBridge(WorkspaceModel.getInstance(project).currentSnapshot) }
          if (library != null) {
            if (usedLibraries.add(library)) {
              LOG.debug { "Library ${library.name} contains class $className referenced from some class under $packageName package" }
            }
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
        val packageDependentModules = filterDependentModules(allDependentModules, packageClasses, moduleClasses, module, packageName)

        progressReporter.itemStep(JavaUiBundle.message("progress.step.extract.module.preparing.to.extract"))
        val dependencyCleaner = ModuleDependenciesCleaner(module, usedModules)
        val dependenciesToRemove = dependencyCleaner.findDependenciesToRemove(moduleFileProcessor)
        progressReporter.itemStep(JavaUiBundle.message("progress.step.extract.module.extracting"))
        writeAction {
          extractModule(directory, module, targetModuleCreator, usedModules, usedLibraries, packageDependentModules)
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
  private suspend fun filterDependentModules(
    dependentModules: Set<Module>,
    packageClasses: Set<String>,
    moduleClasses: Set<String>,
    moduleContainingExtractedPackage: Module,
    extractedPackageName: String
  ) =
    dependentModules.mapWithProgress { dependentModule ->
      val compilerOutputPath = compilerOutputPath(dependentModule)
      val compilerOutputPathForTests = compilerOutputPathForTests(dependentModule)
      if (compilerOutputPath.isEmpty() && compilerOutputPathForTests.isEmpty()) return@mapWithProgress null

      val packagePathToExclude =
        if (dependentModule == moduleContainingExtractedPackage) extractedPackageName.replace('.', '/') + "/"
        else null
      val (prodDependsOnPackage, prodDependsOnModule) = rootDependsOnPackageAndModule(
        compilerOutputPath,
        packageClasses,
        moduleClasses,
        packagePathToExclude
      )
      if (prodDependsOnPackage && prodDependsOnModule) { // no need to check tests
        return@mapWithProgress DependentModule(dependentModule, ModuleDependencyScope.PRODUCTION, ModuleDependencyScope.PRODUCTION)
      }

      val (testDependsOnPackage, testDependsOnModule) = rootDependsOnPackageAndModule(
        compilerOutputPathForTests,
        packageClasses,
        moduleClasses,
        packagePathToExclude = null
      )
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
    paths: List<Path>, packageClasses: Set<String>, moduleClasses: Set<String>, packagePathToExclude: String?,
  ): Pair<Boolean, Boolean> {
    val fileProcessor = ExtractModuleFileProcessor()

    withContext(Dispatchers.IO) {
      for (outputPath in paths) {
        fileProcessor.processClassFiles(outputPath) { path ->
          packagePathToExclude == null || !path.startsWith(packagePathToExclude)
        }
      }
    }

    val packageDependency = fileProcessor.referencedClasses.firstOrNull { it in packageClasses }
    if (packageDependency != null) LOG.debug { "Some class under $paths refer to $packageDependency class from the extracted package" }
    val stillDependsOnModule = fileProcessor.referencedClasses.firstOrNull { it in moduleClasses }
    if (stillDependsOnModule != null) LOG.debug { "Some class under $paths refer to $stillDependsOnModule class outside the extracted package" }
    return (packageDependency != null) to (stillDependsOnModule != null)
  }

  @RequiresReadLock
  private fun collectDependentModules(module: Module): Set<Module> {
    val dependentModules = LinkedHashSet<Module>()
    ModuleUtil.collectModulesDependsOn(module, dependentModules)
    return dependentModules
  }

  @RequiresWriteLock
  private fun extractModule(
    directory: VirtualFile,
    module: Module,
    targetModuleCreator: TargetModuleCreator,
    usedModules: Set<Module>,
    usedLibraries: Set<Library>,
    packageDependentModules: List<DependentModule>,
  ) {
    val extractedModuleData = targetModuleCreator.createExtractedModule(module, directory)
    val newModule = extractedModuleData.module
    val directoryToMoveClassesTo = extractedModuleData.directoryToMoveClassesTo
    ModuleRootModificationUtil.updateModel(newModule) { model ->
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
        if (module != dependentModule.module) {
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
    }

    if (directoryToMoveClassesTo != null) {
      for (child in directory.children) {
        child.move(this, directoryToMoveClassesTo)
      }
    }
    SaveAndSyncHandler.getInstance().scheduleProjectSave(module.project)
  }

  @TestOnly
  suspend fun extractModuleFromDirectory(directory: VirtualFile, module: Module, moduleName: @NlsSafe String, targetSourceRoot: String?) {
    analyzeDependenciesAndCreateModule(directory, module, TargetModuleCreatorImpl(moduleName, targetSourceRoot))
  }
}

internal class TargetModuleCreatorImpl(
  private val moduleName: String,
  private val targetSourceRootPath: String?,
) : TargetModuleCreator {
  override fun createExtractedModule(originalModule: Module, directory: VirtualFile): TargetModuleCreator.ExtractedModuleData {
    val packagePrefix = PackageIndex.getInstance(originalModule.project).getPackageName(directory) ?: ""

    val targetSourceRoot = targetSourceRootPath?.let { VfsUtil.createDirectories(it) }
    val (contentRoot, imlFileDirectory) = if (targetSourceRoot != null) {
      val parent = targetSourceRoot.parent
      if (parent in ModuleRootManager.getInstance(originalModule).contentRoots) targetSourceRoot to originalModule.moduleNioFile.parent
      else parent to parent.toNioPath()
    }
    else {
      directory to originalModule.moduleNioFile.parent
    }

    val newModule = ModuleManager.getInstance(originalModule.project).newModule(imlFileDirectory.resolve("$moduleName.iml"),
                                                                        JAVA_MODULE_ENTITY_TYPE_ID_NAME)

    ModuleRootModificationUtil.updateModel(newModule) { model ->
      if (ModuleRootManager.getInstance(originalModule).isSdkInherited) {
        model.inheritSdk()
      }
      else {
        model.sdk = ModuleRootManager.getInstance(originalModule).sdk
      }
      val contentEntry = model.addContentEntry(contentRoot)
      if (targetSourceRoot != null) {
        contentEntry.addSourceFolder(targetSourceRoot, false)
      }
      else {
        contentEntry.addSourceFolder(directory, false, packagePrefix)
      }
    }

    val directoryToMoveClassesTo =
      if (targetSourceRoot != null) {
        VfsUtil.createDirectoryIfMissing(targetSourceRoot, packagePrefix.replace('.', '/'))
      }
      else null

    return TargetModuleCreator.ExtractedModuleData(newModule, directoryToMoveClassesTo)
  }
}