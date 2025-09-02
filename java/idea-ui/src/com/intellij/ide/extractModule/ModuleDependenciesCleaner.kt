// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.extractModule

import com.intellij.analysis.AnalysisScope
import com.intellij.ide.JavaUiBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.packageDependencies.DependenciesToolWindow
import com.intellij.packageDependencies.ForwardDependenciesBuilder
import com.intellij.packageDependencies.ui.DependenciesPanel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.JavaPsiFacade
import com.intellij.ui.content.ContentFactory
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Finds and removes dependencies which aren't used in the code.
 */
class ModuleDependenciesCleaner(
  private val module: Module,
  dependenciesToCheck: Collection<Module>,
) {
  private val dependenciesToCheck = dependenciesToCheck.toSet()
  private val project = module.project
  private val usedModules: MutableSet<Module> = HashSet()
  private val usedLibraries: MutableSet<Library> = HashSet()

  internal suspend fun findDependenciesToRemove(moduleFileProcessor: ExtractModuleFileProcessor): Set<Module> {
    readAction {
      val fileIndex = ProjectFileIndex.getInstance(module.project)
      moduleFileProcessor.referencedClasses
        .asSequence()
        .mapNotNull { className ->
          findFile(className)?.virtualFile
        }
        .forEach { virtualFile ->
          val dependencyModule = fileIndex.getModuleForFile(virtualFile)
          if (dependencyModule != null) {
            usedModules.add(dependencyModule)
          }
          else {
            fileIndex.getOrderEntriesForFile(virtualFile).asSequence()
              .filterIsInstance<LibraryOrderEntry>()
              .filter { !it.isModuleLevel }
              .mapNotNull { it.library }
              .firstOrNull()
              ?.let { usedLibraries.add(it) }
          }
        }
    }

    val dependenciesToRemove =
      withBackgroundProgress(project, JavaUiBundle.message("progress.title.searching.for.redundant.dependencies", module.name)) {
        readAction { processRedundantDependencies(usedModules, moduleFileProcessor.gatheredClassLinks) }
      } ?: return emptySet()

    return dependenciesToRemove
  }

  @RequiresReadLock
  private fun processRedundantDependencies(usedModules: Set<Module>, classLinks: Map<String, Set<String>>): Set<Module>? {
    val dependenciesToRemove = dependenciesToCheck - usedModules
    if (dependenciesToRemove.isEmpty()) {
      val fileIndex = ProjectFileIndex.getInstance(module.project)
      val builder = ForwardDependenciesBuilder(project, AnalysisScope(module))

      for ((className, dependenciesNames) in classLinks) {
        val file = findFile(className)
        if (file == null || !file.isValid) continue
        val relevantDependencies = dependenciesNames.mapNotNullTo(LinkedHashSet()) {
          findFile(it)?.takeIf { psiFile ->
            val virtualFile = psiFile.virtualFile
            virtualFile != null && fileIndex.getModuleForFile(virtualFile) in dependenciesToCheck
          }
        }

        if (relevantDependencies.isNotEmpty()) {
          builder.directDependencies[file] = relevantDependencies
          builder.dependencies[file] = relevantDependencies
        }
      }
      showNothingToCleanNotification(builder)
      return null
    }

    return dependenciesToRemove
  }

  private fun findFile(className: String) = JavaPsiFacade.getInstance(module.project).findClass(className, module.getModuleWithDependenciesAndLibrariesScope(
    false))?.containingFile

  fun removeDependencies(dependenciesToRemove: Set<Module>) {
    if (module.isDisposed || dependenciesToRemove.any { it.isDisposed } || dependenciesToRemove.isEmpty()) return

    val model = ModuleRootManager.getInstance(module).modifiableModel
    val rootModelProvider = object : RootModelProvider {
      override fun getModules(): Array<Module> {
        return ModuleManager.getInstance(project).modules
      }

      override fun getRootModel(module: Module): ModuleRootModel {
        return if (module == this@ModuleDependenciesCleaner.module) model else ModuleRootManager.getInstance(module)
      }
    }
    val transitiveDependenciesToAdd = LinkedHashSet<Module>()
    val removedDependencies = ArrayList<Module>()
    for (dependency in dependenciesToRemove) {
      val entry = OrderEntryUtil.findModuleOrderEntry(model, dependency)
      if (entry != null) {
        val additionalDependencies = JavaProjectDependenciesAnalyzer.findExportedDependenciesReachableViaThisDependencyOnly(module, dependency, rootModelProvider)
        additionalDependencies.entries
          .filter { (it.key as? ModuleOrderEntry)?.module in usedModules }
          .mapNotNullTo(transitiveDependenciesToAdd) { (it.value as? ModuleOrderEntry)?.module }
        model.removeOrderEntry(entry)
        removedDependencies.add(dependency)
      }
    }
    for (dependency in transitiveDependenciesToAdd) {
      model.addModuleOrderEntry(dependency)
    }
    model.commit()
    showSuccessNotification(removedDependencies, transitiveDependenciesToAdd)
  }

  private fun showSuccessNotification(dependenciesToRemove: List<Module>, transitiveDependenciesToAdd: Set<Module>) {
    val transitiveDependenciesMessage =
      if (transitiveDependenciesToAdd.isNotEmpty()) JavaUiBundle.message("notification.content.transitive.dependencies.were.added", transitiveDependenciesToAdd.first().name, transitiveDependenciesToAdd.size - 1)
      else ""
    val notification = Notification(
      "Dependencies",
      JavaUiBundle.message("notification.title.dependencies.were.cleaned.up", module.name),
      JavaUiBundle.message("notification.content.unused.dependencies.were.removed", dependenciesToRemove.first().name, dependenciesToRemove.size - 1, transitiveDependenciesMessage),
      NotificationType.INFORMATION
    )
    notification.notify(project)
  }

  private fun showNothingToCleanNotification(builder: ForwardDependenciesBuilder) {
    val notification = Notification(
      "Dependencies",
      JavaUiBundle.message("notification.content.none.module.dependencies.can.be.safely.removed", module.name),
      NotificationType.INFORMATION
    )
    notification.addAction(ShowDependenciesAction(module, builder))
    notification.notify(project)
  }

  private class ShowDependenciesAction(private val module: Module, private val builder: ForwardDependenciesBuilder)
    : NotificationAction(JavaUiBundle.message("notification.action.text.show.dependencies")) {
    private val project = module.project

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      val panel = DependenciesPanel(project, listOf(builder), LinkedHashSet())
      val content = ContentFactory.getInstance().createContent(panel, JavaUiBundle.message("tab.title.module.dependencies", module.name), false)
      content.setDisposer(panel)
      panel.setContent(content)
      DependenciesToolWindow.getInstance(project).addContent(content)
    }
  }
}
