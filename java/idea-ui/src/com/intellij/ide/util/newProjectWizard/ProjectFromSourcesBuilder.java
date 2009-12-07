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
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.SourcePathsBuilder;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 17, 2007
 */
public class ProjectFromSourcesBuilder extends ProjectBuilder implements SourcePathsBuilder {
  private List<Pair<String, String>> mySourcePaths = Collections.emptyList();
  private String myContentRootPath;
  private List<LibraryDescriptor> myChosenLibraries = Collections.emptyList();
  private Set<LibraryDescriptor> myChosenLibrariesSet;
  private List<ModuleDescriptor> myChosenModules = Collections.emptyList();
  private final List<ProjectConfigurationUpdater> myUpdaters = new ArrayList<ProjectConfigurationUpdater>();
  private final ModuleInsight myModuleInsight;

  public ProjectFromSourcesBuilder(final ModuleInsight moduleInsight) {
    myModuleInsight = moduleInsight;
  }

  public void setContentEntryPath(final String contentRootPath) {
    myContentRootPath = contentRootPath;
  }

  public String getContentEntryPath() {
    return myContentRootPath;
  }

  public void addSourcePath(final Pair<String, String> sourcePathInfo) {
    mySourcePaths.add(sourcePathInfo);
  }

  /**
   * @param paths list of pairs [SourcePath, PackagePrefix]
   */
  public void setSourcePaths(List<Pair<String,String>> paths) {
    mySourcePaths = paths;
  }

  public List<Pair<String, String>> getSourcePaths() {
    return mySourcePaths;
  }

  public List<Module> commit(final Project project, final ModifiableModuleModel model, final ModulesProvider modulesProvider) {
    final LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    final Map<LibraryDescriptor, Library> projectLibs = new HashMap<LibraryDescriptor, Library>();
    final List<Module> result = new ArrayList<Module>();
    // create project-level libraries
    Exception e = ApplicationManager.getApplication().runWriteAction(new Computable<Exception>() {
      public Exception compute() {
        try {
          for (LibraryDescriptor lib : myChosenLibraries) {
            if (lib.getLevel() == LibraryDescriptor.Level.PROJECT) {
              final Collection<File> files = lib.getJars();
              final Library projectLib = projectLibraryTable.createLibrary(lib.getName());
              final Library.ModifiableModel model = projectLib.getModifiableModel();
              for (File file : files) {
                model.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
              }
              model.commit();
              projectLibs.put(lib, projectLib);
            }
          }
          return null;
        }
        catch (Exception e) {
          return e;
        }
      }
    });
    if (e != null) {
      Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", e.getMessage()), IdeBundle.message("title.add.module"));
    }
    
    
    // create modules and set up dependencies
    final Map<String, String> sourceRootToPrefixMap = new HashMap<String, String>();
    for (Pair<String, String> pair : getSourcePaths()) {
      sourceRootToPrefixMap.put(FileUtil.toSystemIndependentName(pair.getFirst()), pair.getSecond());
    }
    final Map<ModuleDescriptor, Module> descriptorToModuleMap = new HashMap<ModuleDescriptor, Module>();
    Exception ex = ApplicationManager.getApplication().runWriteAction(new Computable<Exception>() {
      public Exception compute() {
        try {
          final ModifiableModuleModel moduleModel = model != null ? model : ModuleManager.getInstance(project).getModifiableModel();
          for (final ModuleDescriptor moduleDescriptor : myChosenModules) {
            final Module module = createModule(project, moduleDescriptor, sourceRootToPrefixMap, projectLibs, moduleModel);
            result.add(module);
            descriptorToModuleMap.put(moduleDescriptor, module);
          }
          moduleModel.commit();
        }
        catch (Exception e) {
          return e;
        }
        return null;
      }
    });
    if (ex != null) {
      Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", ex.getMessage()), IdeBundle.message("title.add.module"));
    }
    
    // setup dependencies between modules
    ex = ApplicationManager.getApplication().runWriteAction(new Computable<Exception>() {
      public Exception compute() {
        try {
          for (final ModuleDescriptor descriptor : myChosenModules) {
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
        catch (Exception e) {
          return e;
        }
        return null;
      }
    });
    
    if (ex != null) {
      Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", ex.getMessage()), IdeBundle.message("title.add.module"));
    }

    return result;
  }

  public void addConfigurationUpdater(ProjectConfigurationUpdater updater) {
    myUpdaters.add(updater);
  }

  @NotNull
  public Module createModule(final Project project, final ModuleDescriptor descriptor, final Map<String, String> sourceRootToPrefixMap,
                             final Map<LibraryDescriptor, Library> projectLibs, final ModifiableModuleModel moduleModel) 
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {

    final String moduleFilePath = descriptor.computeModuleFilePath();
    ModuleBuilder.deleteModuleFile(moduleFilePath);

    final Module module = moduleModel.newModule(moduleFilePath, StdModuleTypes.JAVA);
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    setupRootModel(descriptor, modifiableModel, sourceRootToPrefixMap, projectLibs);
    for (ProjectConfigurationUpdater updater : myUpdaters) {
      updater.updateModule(descriptor, module, modifiableModel);
    }
    modifiableModel.commit();
    return module;
  }

  private void setupRootModel(final ModuleDescriptor descriptor, final ModifiableRootModel rootModel, final Map<String, String> sourceRootToPrefixMap, final Map<LibraryDescriptor, Library> projectLibs) {
    final CompilerModuleExtension compilerModuleExtension = rootModel.getModuleExtension(CompilerModuleExtension.class);
    compilerModuleExtension.setExcludeOutput(true);
    rootModel.inheritSdk();

    final Set<File> contentRoots = descriptor.getContentRoots();
    for (File contentRoot : contentRoots) {
      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      VirtualFile moduleContentRoot = lfs.refreshAndFindFileByPath(FileUtil.toSystemIndependentName(contentRoot.getPath()));
      if (moduleContentRoot != null) {
        final ContentEntry contentEntry = rootModel.addContentEntry(moduleContentRoot);
        final Set<File> sourceRoots = descriptor.getSourceRoots(contentRoot);
        for (File srcRoot : sourceRoots) {
          final String srcpath = FileUtil.toSystemIndependentName(srcRoot.getPath());
          final VirtualFile sourceRoot = lfs.refreshAndFindFileByPath(srcpath);
          if (sourceRoot != null) {
            final String packagePrefix = sourceRootToPrefixMap.get(srcpath);
            if (packagePrefix == null || "".equals(packagePrefix)) {
              contentEntry.addSourceFolder(sourceRoot, shouldBeTestRoot(srcRoot));
            }
            else {
              contentEntry.addSourceFolder(sourceRoot, shouldBeTestRoot(srcRoot), packagePrefix);
            }
          }
        }
      }
    }
    compilerModuleExtension.inheritCompilerOutputPath(true);
    final LibraryTable moduleLibraryTable = rootModel.getModuleLibraryTable();
    for (LibraryDescriptor libDescriptor : myModuleInsight.getLibraryDependencies(descriptor)) {
      if (!isLibraryChosen(libDescriptor)) {
        continue;
      }
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

  public void setLibraries(final List<LibraryDescriptor> libraries) {
    myChosenLibraries = (libraries == null) ? Collections.<LibraryDescriptor>emptyList() : libraries;
    myChosenLibrariesSet = null;
  }

  public List<LibraryDescriptor> getLibraries() {
    return myChosenLibraries;
  }

  public void setModules(final List<ModuleDescriptor> modules) {
    myChosenModules = (modules == null) ? Collections.<ModuleDescriptor>emptyList() : modules;
  }

  public List<ModuleDescriptor> getModules() {
    return myChosenModules;
  }
  
  public boolean isLibraryChosen(LibraryDescriptor lib) {
    Set<LibraryDescriptor> available = myChosenLibrariesSet;
    if (available == null) {
      available = new HashSet<LibraryDescriptor>(myChosenLibraries);
      myChosenLibrariesSet = available;
    }
    return available.contains(lib);
  }

  public static interface ProjectConfigurationUpdater {

    void updateModule(final ModuleDescriptor descriptor, Module module, ModifiableRootModel rootModel);

  }

  @Override
  public boolean isSuitableSdk(final Sdk sdk) {
    return sdk.getSdkType() == JavaSdk.getInstance();
  }
}
