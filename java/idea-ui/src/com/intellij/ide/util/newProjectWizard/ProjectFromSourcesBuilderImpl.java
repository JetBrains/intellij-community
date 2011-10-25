/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.importProject.LibraryDescriptor;
import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ModuleInsight;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.newProjectWizard.modes.ImportImlMode;
import com.intellij.ide.util.projectWizard.ExistingModuleLoader;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 17, 2007
 */
public class ProjectFromSourcesBuilderImpl extends ProjectBuilder implements ProjectFromSourcesBuilder {
  private String myBaseProjectPath;
  private final List<ProjectConfigurationUpdater> myUpdaters = new ArrayList<ProjectConfigurationUpdater>();
  private final Map<ProjectStructureDetector, ProjectDescriptor> myProjectDescriptors = new LinkedHashMap<ProjectStructureDetector, ProjectDescriptor>();
  private MultiMap<ProjectStructureDetector, DetectedProjectRoot> myRoots = MultiMap.emptyInstance();

  public ProjectFromSourcesBuilderImpl() {
    for (ProjectStructureDetector detector : ProjectStructureDetector.EP_NAME.getExtensions()) {
      myProjectDescriptors.put(detector, new ProjectDescriptor());
    }
  }

  public void setBaseProjectPath(final String contentRootPath) {
    myBaseProjectPath = contentRootPath;
  }

  @Override
  public String getBaseProjectPath() {
    return myBaseProjectPath;
  }

  public void setProjectRoots(MultiMap<ProjectStructureDetector, DetectedProjectRoot> roots) {
    myRoots = roots;
  }

  @NotNull
  @Override
  public Collection<DetectedProjectRoot> getProjectRoots(ProjectStructureDetector detector) {
    return myRoots.get(detector);
  }

  public List<Module> commit(final Project project, final ModifiableModuleModel model, final ModulesProvider modulesProvider) {
    final LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    final Map<LibraryDescriptor, Library> projectLibs = new HashMap<LibraryDescriptor, Library>();
    final List<Module> result = new ArrayList<Module>();
    try {
      AccessToken token = WriteAction.start();
      try {
        // create project-level libraries
        for (ProjectDescriptor projectDescriptor : getSelectedDescriptors()) {
          for (LibraryDescriptor lib : projectDescriptor.getLibraries()) {
            if (lib.getLevel() == LibraryDescriptor.Level.PROJECT) {
              final Collection<File> files = lib.getJars();
              final Library projectLib = projectLibraryTable.createLibrary(lib.getName());
              final Library.ModifiableModel libraryModel = projectLib.getModifiableModel();
              for (File file : files) {
                libraryModel.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
              }
              libraryModel.commit();
              projectLibs.put(lib, projectLib);
            }
          }
        }
      }
      finally {
        token.finish();
      }
    }
    catch (Exception e) {
      Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", e.getMessage()), IdeBundle.message("title.add.module"));
    }

    final Map<ModuleDescriptor, Module> descriptorToModuleMap = new HashMap<ModuleDescriptor, Module>();

    try {
      AccessToken token = WriteAction.start();
      try {
        final ModifiableModuleModel moduleModel = model != null ? model : ModuleManager.getInstance(project).getModifiableModel();
        for (ProjectDescriptor descriptor : getSelectedDescriptors()) {
          for (final ModuleDescriptor moduleDescriptor : descriptor.getModules()) {
            final Module module;
            if (moduleDescriptor.isReuseExistingElement()) {
              final ExistingModuleLoader moduleLoader =
                ImportImlMode.setUpLoader(FileUtil.toSystemIndependentName(moduleDescriptor.computeModuleFilePath()));
              module = moduleLoader.createModule(moduleModel);
            }
            else {
              module = createModule(descriptor, moduleDescriptor, projectLibs, moduleModel);
            }
            result.add(module);
            descriptorToModuleMap.put(moduleDescriptor, module);
          }
        }

        moduleModel.commit();
      }
      finally {
        token.finish();
      }
    }
    catch (Exception e) {
      Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", e.getMessage()), IdeBundle.message("title.add.module"));
    }

    // setup dependencies between modules
    try {
      AccessToken token = WriteAction.start();
      try {
        for (ProjectDescriptor data : getSelectedDescriptors()) {
          for (final ModuleDescriptor descriptor : data.getModules()) {
            final Module module = descriptorToModuleMap.get(descriptor);
            if (module == null) {
              continue;
            }
            final Set<ModuleDescriptor> deps = descriptor.getDependencies();
            if (deps.size() == 0) {
              continue;
            }
            final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
            for (ModuleDescriptor dependentDescriptor : deps) {
              final Module dependentModule = descriptorToModuleMap.get(dependentDescriptor);
              if (dependentModule != null) {
                rootModel.addModuleOrderEntry(dependentModule);
              }
            }
            rootModel.commit();
          }
        }
      }
      finally {
        token.finish();
      }
    }
    catch (Exception e) {
      Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", e.getMessage()), IdeBundle.message("title.add.module"));
    }

    AccessToken token = WriteAction.start();
    try {
      for (ProjectConfigurationUpdater updater : myUpdaters) {
        updater.updateProject(project);
      }
    }
    finally {
      token.finish();
    }


    return result;
  }

  public Collection<ProjectDescriptor> getSelectedDescriptors() {
    return myProjectDescriptors.values();
  }

  public void addConfigurationUpdater(ProjectConfigurationUpdater updater) {
    myUpdaters.add(updater);
  }

  @NotNull
  private Module createModule(ProjectDescriptor projectDescriptor, final ModuleDescriptor descriptor, final Map<LibraryDescriptor, Library> projectLibs,
                              final ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {

    final String moduleFilePath = descriptor.computeModuleFilePath();
    ModuleBuilder.deleteModuleFile(moduleFilePath);

    final Module module = moduleModel.newModule(moduleFilePath, descriptor.getModuleType());
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    setupRootModel(projectDescriptor, descriptor, modifiableModel, projectLibs);
    descriptor.updateModuleConfiguration(module, modifiableModel);
    modifiableModel.commit();
    return module;
  }

  private void setupRootModel(ProjectDescriptor projectDescriptor, final ModuleDescriptor descriptor, final ModifiableRootModel rootModel,
                              final Map<LibraryDescriptor, Library> projectLibs) {
    final CompilerModuleExtension compilerModuleExtension = rootModel.getModuleExtension(CompilerModuleExtension.class);
    compilerModuleExtension.setExcludeOutput(true);
    rootModel.inheritSdk();

    final Set<File> contentRoots = descriptor.getContentRoots();
    for (File contentRoot : contentRoots) {
      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      VirtualFile moduleContentRoot = lfs.refreshAndFindFileByPath(FileUtil.toSystemIndependentName(contentRoot.getPath()));
      if (moduleContentRoot != null) {
        final ContentEntry contentEntry = rootModel.addContentEntry(moduleContentRoot);
        final Collection<JavaModuleSourceRoot> sourceRoots = descriptor.getSourceRoots(contentRoot);
        for (JavaModuleSourceRoot srcRoot : sourceRoots) {
          final String srcpath = FileUtil.toSystemIndependentName(srcRoot.getDirectory().getPath());
          final VirtualFile sourceRoot = lfs.refreshAndFindFileByPath(srcpath);
          if (sourceRoot != null) {
            contentEntry.addSourceFolder(sourceRoot, shouldBeTestRoot(srcRoot.getDirectory()), srcRoot.getPackagePrefix());
          }
        }
      }
    }
    compilerModuleExtension.inheritCompilerOutputPath(true);
    final LibraryTable moduleLibraryTable = rootModel.getModuleLibraryTable();
    for (LibraryDescriptor libDescriptor : ModuleInsight.getLibraryDependencies(descriptor, projectDescriptor.getLibraries())) {
      final Library projectLib = projectLibs.get(libDescriptor);
      if (projectLib != null) {
        rootModel.addLibraryEntry(projectLib);
      }
      else {
        // add as module library
        final Collection<File> jars = libDescriptor.getJars();
        for (File file : jars) {
          Library library = moduleLibraryTable.createLibrary();
          Library.ModifiableModel modifiableModel = library.getModifiableModel();
          modifiableModel.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
          modifiableModel.commit();
        }
      }
    }

  }

  @NotNull
  @Override
  public ProjectDescriptor getProjectDescriptor(ProjectStructureDetector detector) {
    return myProjectDescriptors.get(detector);
  }

  private static boolean shouldBeTestRoot(final File srcRoot) {
    if (isTestRootName(srcRoot.getName())) {
      return true;
    }
    final File parentFile = srcRoot.getParentFile();
    return parentFile != null && isTestRootName(parentFile.getName());
  }

  private static boolean isTestRootName(final String name) {
    return "test".equalsIgnoreCase(name) || 
           "tests".equalsIgnoreCase(name) || 
           "testSource".equalsIgnoreCase(name) || 
           "testSources".equalsIgnoreCase(name) || 
           "testSrc".equalsIgnoreCase(name);
  }

  public interface ProjectConfigurationUpdater {
    void updateProject(@NotNull Project project);
  }

  @Override
  public boolean isSuitableSdk(final Sdk sdk) {
    for (ProjectDescriptor projectDescriptor : getSelectedDescriptors()) {
      for (ModuleDescriptor moduleDescriptor : projectDescriptor.getModules()) {
        try {
          final File file = new File(moduleDescriptor.computeModuleFilePath());
          if (file.exists()) {
            final Element rootElement = JDOMUtil.loadDocument(file).getRootElement();
            final String type = rootElement.getAttributeValue("type");
            if (type != null) {
              final ModuleType moduleType = ModuleTypeManager.getInstance().findByID(type);
              if (moduleType != null && !moduleType.createModuleBuilder().isSuitableSdk(sdk)) return false;
            }
          }
        }
        catch (Exception ignore) {
        }
      }
    }
    return sdk.getSdkType() == JavaSdk.getInstance();
  }
}
