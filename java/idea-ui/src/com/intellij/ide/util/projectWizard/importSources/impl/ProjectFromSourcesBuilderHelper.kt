// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard.importSources.impl

import com.intellij.ide.IdeCoreBundle
import com.intellij.ide.util.importProject.LibraryDescriptor
import com.intellij.ide.util.importProject.ModuleDescriptor
import com.intellij.ide.util.importProject.ModuleInsight
import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.ExistingModuleLoader
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl.ProjectConfigurationUpdater
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleWithNameAlreadyExists
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.ArrayUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jdom.JDOMException
import java.io.File
import java.io.IOException

private val LOG = Logger.getInstance(ProjectFromSourcesBuilderHelper::class.java)

internal class ProjectFromSourcesBuilderHelper(private val project: Project,
                                               private val moduleModel: ModifiableModuleModel,
                                               private val updatedModulesProvider: ModulesProvider,
                                               private val commitModels: Boolean,
                                               private val myUpdaters: List<ProjectConfigurationUpdater>,
                                               private val selectedDescriptors: Collection<ProjectDescriptor>) {
  @RequiresEdt
  fun doCommit(): List<Module> {
    val modelsProvider: ModifiableModelsProvider = IdeaModifiableModelsProvider()
    val projectLibraryTable = modelsProvider.getLibraryTableModifiableModel(project)
    val projectLibs: MutableMap<LibraryDescriptor, Library> = HashMap()
    val result: MutableList<Module> = ArrayList()
    try {
      WriteAction.run<RuntimeException> {
        createProjectLevelLibraries(projectLibraryTable, projectLibs)
        if (commitModels) {
          projectLibraryTable.commit()
        }
      }
    }
    catch (e: Exception) {
      LOG.info(e)
      Messages.showErrorDialog(IdeCoreBundle.message("error.adding.module.to.project", e.message),
                               IdeCoreBundle.message("title.add.module"))
    }

    val descriptorToModuleMap: MutableMap<ModuleDescriptor, Module> = HashMap()

    try {
      WriteAction.run<Exception> {
        result.addAll(createModules(projectLibs, descriptorToModuleMap))
        if (commitModels) {
          moduleModel.commit()
        }
      }
    }
    catch (e: Exception) {
      LOG.info(e)
      Messages.showErrorDialog(IdeCoreBundle.message("error.adding.module.to.project", e.message),
                               IdeCoreBundle.message("title.add.module"))
    }

    setupDependenciesBetweenModules(modelsProvider, descriptorToModuleMap)
    return result
  }

  @Throws(IOException::class, ModuleWithNameAlreadyExists::class, JDOMException::class, ConfigurationException::class)
  private fun createModules(projectLibs: Map<LibraryDescriptor, Library>,
                            descriptorToModuleMap: MutableMap<ModuleDescriptor, Module>): List<Module> {
    val result: MutableList<Module> = ArrayList()
    val contentRootToModule: MutableMap<String, Module> = HashMap()
    for (module in moduleModel.modules) {
      // check that module exists in provider
      if (null != updatedModulesProvider.getModule(module.name)) {
        val moduleRootModel = updatedModulesProvider.getRootModel(module)
        for (url in moduleRootModel.contentRootUrls) {
          contentRootToModule[url] = module
        }
      }
    }
    for (descriptor in selectedDescriptors) {
      for (moduleDescriptor in descriptor.modules) {
        for (contentRoot in moduleDescriptor.contentRoots) {
          val url = VfsUtilCore.fileToUrl(contentRoot)
          val existingModule = contentRootToModule[url]
          if (existingModule != null && ArrayUtil.contains(existingModule, *moduleModel.modules)) {
            moduleModel.disposeModule(existingModule)
          }
        }
        var module: Module
        if (moduleDescriptor.isReuseExistingElement) {
          val moduleLoader =
            ExistingModuleLoader.setUpLoader(FileUtil.toSystemIndependentName(moduleDescriptor.computeModuleFilePath()))
          module = moduleLoader.createModule(moduleModel)
        }
        else {
          module = createModule(descriptor, moduleDescriptor, projectLibs)
        }
        result.add(module)
        descriptorToModuleMap[moduleDescriptor] = module
      }
    }
    return result
  }

  @Throws(InvalidDataException::class)
  private fun createModule(projectDescriptor: ProjectDescriptor,
                           descriptor: ModuleDescriptor,
                           projectLibs: Map<LibraryDescriptor, Library>): Module {
    val moduleFilePath = descriptor.computeModuleFilePath()
    ModuleBuilder.deleteModuleFile(moduleFilePath)

    val module = moduleModel.newModule(moduleFilePath, descriptor.moduleType.id)
    val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
    setupRootModel(projectDescriptor, descriptor, modifiableModel, projectLibs)
    descriptor.updateModuleConfiguration(module, modifiableModel)
    if (commitModels) {
      modifiableModel.commit()
    }
    return module
  }

  private fun setupRootModel(projectDescriptor: ProjectDescriptor,
                             descriptor: ModuleDescriptor,
                             rootModel: ModifiableRootModel,
                             projectLibs: Map<LibraryDescriptor, Library>) {
    val compilerModuleExtension = rootModel.getModuleExtension(
      CompilerModuleExtension::class.java)
    compilerModuleExtension.isExcludeOutput = true
    rootModel.inheritSdk()

    val contentRoots = descriptor.contentRoots
    for (contentRoot in contentRoots) {
      val lfs = LocalFileSystem.getInstance()
      val moduleContentRoot = lfs.refreshAndFindFileByPath(FileUtil.toSystemIndependentName(contentRoot.path))
      if (moduleContentRoot != null) {
        val contentEntry = rootModel.addContentEntry(moduleContentRoot)
        val sourceRoots = descriptor.getSourceRoots(contentRoot)
        for (srcRoot in sourceRoots) {
          val srcPath = FileUtil.toSystemIndependentName(srcRoot.directory.path)
          val sourceRoot = lfs.refreshAndFindFileByPath(srcPath)
          if (sourceRoot != null) {
            contentEntry.addSourceFolder(sourceRoot, shouldBeTestRoot(srcRoot.directory),
                                         ProjectFromSourcesBuilderImpl.getPackagePrefix(srcRoot))
          }
        }
      }
    }
    compilerModuleExtension.inheritCompilerOutputPath(true)
    val moduleLibraryTable = rootModel.moduleLibraryTable
    for (libDescriptor in ModuleInsight.getLibraryDependencies(descriptor, projectDescriptor.libraries)) {
      val projectLib = projectLibs[libDescriptor]
      if (projectLib != null) {
        rootModel.addLibraryEntry(projectLib)
      }
      else {
        // add as module library
        val jars = libDescriptor.jars
        for (file in jars) {
          val library = moduleLibraryTable.createLibrary()
          val modifiableModel = library.modifiableModel
          modifiableModel.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES)
          if (commitModels) {
            modifiableModel.commit()
          }
        }
      }
    }
  }

  private fun setupDependenciesBetweenModules(modelsProvider: ModifiableModelsProvider,
                                              descriptorToModuleMap: Map<ModuleDescriptor, Module>) {
    // setup dependencies between modules
    try {
      WriteAction.run<RuntimeException> {
        for (data in selectedDescriptors) {
          for (descriptor in data.modules) {
            val module = descriptorToModuleMap[descriptor]
            if (module == null) {
              continue
            }
            val deps = descriptor.dependencies
            if (deps.isEmpty()) {
              continue
            }
            val rootModel = ModuleRootManager.getInstance(module).modifiableModel
            for (dependentDescriptor in deps) {
              val dependentModule = descriptorToModuleMap[dependentDescriptor]
              if (dependentModule != null) {
                rootModel.addModuleOrderEntry(dependentModule)
              }
            }
            if (commitModels) {
              rootModel.commit()
            }
          }
        }
      }
    }
    catch (e: Exception) {
      LOG.info(e)
      Messages.showErrorDialog(IdeCoreBundle.message("error.adding.module.to.project", e.message),
                               IdeCoreBundle.message("title.add.module"))
    }

    WriteAction.run<RuntimeException> {
      for (updater in myUpdaters) {
        updater.updateProject(project, modelsProvider, updatedModulesProvider)
      }
    }
  }

  private fun createProjectLevelLibraries(projectLibraryTable: LibraryTable.ModifiableModel,
                                          projectLibs: MutableMap<LibraryDescriptor, Library>) {
    for (projectDescriptor in selectedDescriptors) {
      for (lib in projectDescriptor.libraries) {
        val files = lib.jars
        val projectLib = projectLibraryTable.createLibrary(lib.name)
        val libraryModel = projectLib.modifiableModel
        for (file in files) {
          libraryModel.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES)
        }
        if (commitModels) {
          libraryModel.commit()
        }
        projectLibs[lib] = projectLib
      }
    }
  }

  private fun shouldBeTestRoot(srcRoot: File): Boolean {
    if (ProjectFromSourcesBuilderImpl.isTestRootName(srcRoot.name)) {
      return true
    }
    val parentFile = srcRoot.parentFile
    return parentFile != null && ProjectFromSourcesBuilderImpl.isTestRootName(parentFile.name)
  }
}
