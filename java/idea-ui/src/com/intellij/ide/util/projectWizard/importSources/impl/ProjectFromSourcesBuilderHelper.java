// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard.importSources.impl;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.util.importProject.LibraryDescriptor;
import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ModuleInsight;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.ExistingModuleLoader;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

class ProjectFromSourcesBuilderHelper {
  private static final Logger LOG = Logger.getInstance(ProjectFromSourcesBuilderHelper.class);

  private final @NotNull Project project;
  private final ModifiableModuleModel moduleModel;
  private final ModulesProvider updatedModulesProvider;
  private final boolean commitModels;
  private final List<ProjectFromSourcesBuilderImpl.ProjectConfigurationUpdater> myUpdaters;
  private final Collection<ProjectDescriptor> selectedDescriptors;

  ProjectFromSourcesBuilderHelper(@NotNull Project project,
                                  ModifiableModuleModel moduleModel,
                                  ModulesProvider updatedModulesProvider,
                                  boolean commitModels,
                                  List<ProjectFromSourcesBuilderImpl.ProjectConfigurationUpdater> updaters,
                                  Collection<ProjectDescriptor> selectedDescriptors) {
    this.project = project;
    this.moduleModel = moduleModel;
    this.updatedModulesProvider = updatedModulesProvider;
    this.commitModels = commitModels;
    myUpdaters = updaters;
    this.selectedDescriptors = selectedDescriptors;
  }

  @RequiresEdt
  List<Module> doCommit() {
    ModifiableModelsProvider modelsProvider = new IdeaModifiableModelsProvider();
    LibraryTable.ModifiableModel projectLibraryTable = modelsProvider.getLibraryTableModifiableModel(project);
    Map<LibraryDescriptor, Library> projectLibs = new HashMap<>();
    List<Module> result = new ArrayList<>();
    try {
      WriteAction.run(() -> {
        createProjectLevelLibraries(projectLibraryTable, projectLibs);
        if (commitModels) {
          projectLibraryTable.commit();
        }
      });
    }
    catch (Exception e) {
      LOG.info(e);
      Messages.showErrorDialog(IdeCoreBundle.message("error.adding.module.to.project", e.getMessage()),
                               IdeCoreBundle.message("title.add.module"));
    }

    Map<ModuleDescriptor, Module> descriptorToModuleMap = new HashMap<>();

    try {
      WriteAction.run(() -> {
        result.addAll(createModules(projectLibs, descriptorToModuleMap));
        if (commitModels) {
          moduleModel.commit();
        }
      });
    }
    catch (Exception e) {
      LOG.info(e);
      Messages.showErrorDialog(IdeCoreBundle.message("error.adding.module.to.project", e.getMessage()),
                               IdeCoreBundle.message("title.add.module"));
    }

    setupDependenciesBetweenModules(modelsProvider, descriptorToModuleMap);
    return result;
  }

  private List<Module> createModules(Map<LibraryDescriptor, Library> projectLibs, Map<ModuleDescriptor, Module> descriptorToModuleMap)
    throws IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
    List<Module> result = new ArrayList<>();
    Map<String, Module> contentRootToModule = new HashMap<>();
    for (Module module : moduleModel.getModules()) {
      // check that module exists in provider
      if (null != updatedModulesProvider.getModule(module.getName())) {
        ModuleRootModel moduleRootModel = updatedModulesProvider.getRootModel(module);
        for (String url : moduleRootModel.getContentRootUrls()) {
          contentRootToModule.put(url, module);
        }
      }
    }
    for (ProjectDescriptor descriptor : selectedDescriptors) {
      for (ModuleDescriptor moduleDescriptor : descriptor.getModules()) {
        for (File contentRoot : moduleDescriptor.getContentRoots()) {
          String url = VfsUtilCore.fileToUrl(contentRoot);
          Module existingModule = contentRootToModule.get(url);
          if (existingModule != null && ArrayUtil.contains(existingModule, moduleModel.getModules())) {
            moduleModel.disposeModule(existingModule);
          }
        }
        Module module;
        if (moduleDescriptor.isReuseExistingElement()) {
          ExistingModuleLoader moduleLoader =
            ExistingModuleLoader.setUpLoader(FileUtil.toSystemIndependentName(moduleDescriptor.computeModuleFilePath()));
          module = moduleLoader.createModule(moduleModel);
        }
        else {
          module = createModule(descriptor, moduleDescriptor, projectLibs);
        }
        result.add(module);
        descriptorToModuleMap.put(moduleDescriptor, module);
      }
    }
    return result;
  }

  @NotNull
  private Module createModule(ProjectDescriptor projectDescriptor,
                              ModuleDescriptor descriptor,
                              Map<LibraryDescriptor, Library> projectLibs)
    throws InvalidDataException {

    String moduleFilePath = descriptor.computeModuleFilePath();
    ModuleBuilder.deleteModuleFile(moduleFilePath);

    Module module = moduleModel.newModule(moduleFilePath, descriptor.getModuleType().getId());
    ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    setupRootModel(projectDescriptor, descriptor, modifiableModel, projectLibs);
    descriptor.updateModuleConfiguration(module, modifiableModel);
    if (commitModels) {
      modifiableModel.commit();
    }
    return module;
  }

  private void setupRootModel(ProjectDescriptor projectDescriptor,
                              ModuleDescriptor descriptor,
                              ModifiableRootModel rootModel,
                              Map<LibraryDescriptor, Library> projectLibs) {
    CompilerModuleExtension compilerModuleExtension = rootModel.getModuleExtension(CompilerModuleExtension.class);
    compilerModuleExtension.setExcludeOutput(true);
    rootModel.inheritSdk();

    Set<File> contentRoots = descriptor.getContentRoots();
    for (File contentRoot : contentRoots) {
      LocalFileSystem lfs = LocalFileSystem.getInstance();
      VirtualFile moduleContentRoot = lfs.refreshAndFindFileByPath(FileUtil.toSystemIndependentName(contentRoot.getPath()));
      if (moduleContentRoot != null) {
        ContentEntry contentEntry = rootModel.addContentEntry(moduleContentRoot);
        Collection<DetectedSourceRoot> sourceRoots = descriptor.getSourceRoots(contentRoot);
        for (DetectedSourceRoot srcRoot : sourceRoots) {
          String srcpath = FileUtil.toSystemIndependentName(srcRoot.getDirectory().getPath());
          VirtualFile sourceRoot = lfs.refreshAndFindFileByPath(srcpath);
          if (sourceRoot != null) {
            contentEntry.addSourceFolder(sourceRoot, shouldBeTestRoot(srcRoot.getDirectory()),
                                         ProjectFromSourcesBuilderImpl.getPackagePrefix(srcRoot));
          }
        }
      }
    }
    compilerModuleExtension.inheritCompilerOutputPath(true);
    LibraryTable moduleLibraryTable = rootModel.getModuleLibraryTable();
    for (LibraryDescriptor libDescriptor : ModuleInsight.getLibraryDependencies(descriptor, projectDescriptor.getLibraries())) {
      Library projectLib = projectLibs.get(libDescriptor);
      if (projectLib != null) {
        rootModel.addLibraryEntry(projectLib);
      }
      else {
        // add as module library
        Collection<File> jars = libDescriptor.getJars();
        for (File file : jars) {
          Library library = moduleLibraryTable.createLibrary();
          Library.ModifiableModel modifiableModel = library.getModifiableModel();
          modifiableModel.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
          if (commitModels) {
            modifiableModel.commit();
          }
        }
      }
    }
  }

  private static boolean shouldBeTestRoot(File srcRoot) {
    if (ProjectFromSourcesBuilderImpl.isTestRootName(srcRoot.getName())) {
      return true;
    }
    File parentFile = srcRoot.getParentFile();
    return parentFile != null && ProjectFromSourcesBuilderImpl.isTestRootName(parentFile.getName());
  }

  private void setupDependenciesBetweenModules(ModifiableModelsProvider modelsProvider,
                                               Map<ModuleDescriptor, Module> descriptorToModuleMap) {
    // setup dependencies between modules
    try {
      WriteAction.run(() -> {
        for (ProjectDescriptor data : selectedDescriptors) {
          for (ModuleDescriptor descriptor : data.getModules()) {
            Module module = descriptorToModuleMap.get(descriptor);
            if (module == null) {
              continue;
            }
            Set<ModuleDescriptor> deps = descriptor.getDependencies();
            if (deps.isEmpty()) {
              continue;
            }
            ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
            for (ModuleDescriptor dependentDescriptor : deps) {
              Module dependentModule = descriptorToModuleMap.get(dependentDescriptor);
              if (dependentModule != null) {
                rootModel.addModuleOrderEntry(dependentModule);
              }
            }
            if (commitModels) {
              rootModel.commit();
            }
          }
        }
      });
    }
    catch (Exception e) {
      LOG.info(e);
      Messages.showErrorDialog(IdeCoreBundle.message("error.adding.module.to.project", e.getMessage()),
                               IdeCoreBundle.message("title.add.module"));
    }

    WriteAction.run(() -> {
      for (ProjectFromSourcesBuilderImpl.ProjectConfigurationUpdater updater : myUpdaters) {
        updater.updateProject(project, modelsProvider, updatedModulesProvider);
      }
    });
  }

  private void createProjectLevelLibraries(LibraryTable.ModifiableModel projectLibraryTable,
                                           Map<LibraryDescriptor, Library> projectLibs) {
    for (ProjectDescriptor projectDescriptor : selectedDescriptors) {
      for (LibraryDescriptor lib : projectDescriptor.getLibraries()) {
        Collection<File> files = lib.getJars();
        Library projectLib = projectLibraryTable.createLibrary(lib.getName());
        Library.ModifiableModel libraryModel = projectLib.getModifiableModel();
        for (File file : files) {
          libraryModel.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
        }
        if (commitModels) {
          libraryModel.commit();
        }
        projectLibs.put(lib, projectLib);
      }
    }
  }
}
