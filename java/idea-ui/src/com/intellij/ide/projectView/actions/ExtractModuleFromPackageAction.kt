// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.actions

import com.intellij.analysis.AnalysisScope
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectView.impl.ModuleNameValidator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.packageDependencies.ForwardDependenciesBuilder
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.TestOnly

class ExtractModuleFromPackageAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val module = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile) ?: return
    val directory = PsiManager.getInstance(module.project).findDirectory(virtualFile) ?: return
    val suggestedModuleName = "${module.name}.${directory.name}"
    val initialSelection = TextRange(suggestedModuleName.length - directory.name.length, suggestedModuleName.length)
    val moduleName = Messages.showInputDialog(project, JavaUiBundle.message("dialog.message.module.name"),
                                              JavaUiBundle.message("dialog.title.extract.module.from.package"), null,
                                              suggestedModuleName, ModuleNameValidator(project), initialSelection)
    if (moduleName == null) return

    analyzeDependenciesAndCreateModule(directory, module, moduleName)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = project != null && file != null && file.isDirectory
                                         && ProjectFileIndex.getInstance(project).isInSourceContent(file)
  }

  companion object {
    private fun analyzeDependenciesAndCreateModule(directory: PsiDirectory, module: Module, moduleName: @NlsSafe String) {
      val dependenciesBuilder = ForwardDependenciesBuilder(module.project, AnalysisScope(directory))
      object : Task.Backgroundable(module.project,
                                   JavaUiBundle.message("progress.title.extract.module.analyzing.dependencies", directory.name)) {
        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = false
          dependenciesBuilder.analyze()
          val usedModules = LinkedHashSet<Module>()
          val usedLibraries = LinkedHashSet<Library>()
          runReadAction {
            val fileIndex = ProjectFileIndex.getInstance(module.project)
            dependenciesBuilder.directDependencies.values.asSequence().flatten().forEach { file ->
              val virtualFile = file.virtualFile ?: return@forEach
              val depModule = fileIndex.getModuleForFile(virtualFile)
              if (depModule != null) {
                usedModules.add(depModule)
                return@forEach
              }
              val library = fileIndex.getOrderEntriesForFile(virtualFile).asSequence()
                .filterIsInstance<LibraryOrderEntry>()
                .filter { !it.isModuleLevel }
                .mapNotNull { it.library }
                .firstOrNull()
              if (library != null) {
                usedLibraries.add(library)
              }
            }
          }
          ApplicationManager.getApplication().invokeLater {
            runWriteAction {
              extractModule(directory, module, moduleName, usedModules, usedLibraries)
            }
          }
        }
      }.queue()
    }

    private fun extractModule(directory: PsiDirectory, module: Module, moduleName: @NlsSafe String,
                              usedModules: Set<Module>, usedLibraries: Set<Library>) {
      val newModule = ModuleManager.getInstance(module.project).newModule(module.moduleNioFile.parent.resolve("$moduleName.iml"),
                                                                          ModuleTypeId.JAVA_MODULE)
      val packagePrefix = JavaDirectoryService.getInstance().getPackage(directory)?.qualifiedName ?: ""
      ModuleRootModificationUtil.updateModel(newModule) { model ->
        if (ModuleRootManager.getInstance(module).isSdkInherited) {
          model.inheritSdk()
        }
        else {
          model.sdk = ModuleRootManager.getInstance(module).sdk
        }
        model.addContentEntry(directory.virtualFile).addSourceFolder(directory.virtualFile, false, packagePrefix)
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
    }

    @TestOnly
    fun extractModuleFromDirectory(directory: PsiDirectory, module: Module, moduleName: @NlsSafe String) {
      analyzeDependenciesAndCreateModule(directory, module, moduleName)
    }
  }
}

