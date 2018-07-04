/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard.importSources.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.importProject.LibraryDescriptor;
import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ModuleInsight;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.ExistingModuleLoader;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.util.projectWizard.importSources.*;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.util.containers.MultiMap;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class ProjectFromSourcesBuilderImpl extends ProjectImportBuilder implements ProjectFromSourcesBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl");
  private static final String NAME = "Existing Sources";
  private String myBaseProjectPath;
  private final List<ProjectConfigurationUpdater> myUpdaters = new ArrayList<>();
  private final Map<ProjectStructureDetector, ProjectDescriptor> myProjectDescriptors = new LinkedHashMap<>();
  private MultiMap<ProjectStructureDetector, DetectedProjectRoot> myRoots = MultiMap.emptyInstance();
  private final WizardContext myContext;
  private final ModulesProvider myModulesProvider;
  private Set<String> myModuleNames;
  private Set<String> myProjectLibrariesNames;

  public ProjectFromSourcesBuilderImpl(WizardContext context, ModulesProvider modulesProvider) {
    myContext = context;
    myModulesProvider = modulesProvider;
    for (ProjectStructureDetector detector : ProjectStructureDetector.EP_NAME.getExtensions()) {
      myProjectDescriptors.put(detector, new ProjectDescriptor());
    }
  }

  @NotNull
  @Override
  public Set<String> getExistingModuleNames() {
    if (myModuleNames == null) {
      myModuleNames = new HashSet<>();
      for (Module module : myModulesProvider.getModules()) {
        myModuleNames.add(module.getName());
      }
    }
    return myModuleNames;
  }

  @NotNull
  @Override
  public Set<String> getExistingProjectLibraryNames() {
    if (myProjectLibrariesNames == null) {
      myProjectLibrariesNames = new HashSet<>();
      final LibrariesContainer container = LibrariesContainerFactory.createContainer(myContext, myModulesProvider);
      for (Library library : container.getLibraries(LibrariesContainer.LibraryLevel.PROJECT)) {
        myProjectLibrariesNames.add(library.getName());
      }
    }
    return myProjectLibrariesNames;
  }

  @NotNull
  @Override
  public WizardContext getContext() {
    return myContext;
  }

  public void setBaseProjectPath(final String contentRootPath) {
    myBaseProjectPath = contentRootPath;
  }

  @Override
  public String getBaseProjectPath() {
    return myBaseProjectPath;
  }

  public void setupProjectStructure(MultiMap<ProjectStructureDetector, DetectedProjectRoot> roots) {
    myRoots = roots;
    for (ProjectStructureDetector detector : roots.keySet()) {
      detector.setupProjectStructure(roots.get(detector), getProjectDescriptor(detector), this);
    }
  }

  @NotNull
  @Override
  public Collection<DetectedProjectRoot> getProjectRoots(@NotNull ProjectStructureDetector detector) {
    return myRoots.get(detector);
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Folder;
  }

  @Override
  public List getList() {
    return null;
  }

  @Override
  public boolean isMarked(Object element) {
    return false;
  }

  @Override
  public void setList(List list) throws ConfigurationException {
  }

  @Override
  public void setOpenProjectSettingsAfter(boolean on) {
  }

  @Override
  public void setFileToImport(String path) {
    setBaseProjectPath(path);
  }

  @Override
  public List<Module> commit(@NotNull final Project project, final ModifiableModuleModel model, final ModulesProvider modulesProvider) {
    final boolean fromProjectStructure = model != null;
    ModifiableModelsProvider modelsProvider = new IdeaModifiableModelsProvider();
    final LibraryTable.ModifiableModel projectLibraryTable = modelsProvider.getLibraryTableModifiableModel(project);
    final Map<LibraryDescriptor, Library> projectLibs = new HashMap<>();
    final List<Module> result = new ArrayList<>();
    try {
      WriteAction.run(() -> {
        // create project-level libraries
        for (ProjectDescriptor projectDescriptor : getSelectedDescriptors()) {
          for (LibraryDescriptor lib : projectDescriptor.getLibraries()) {
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
        if (!fromProjectStructure) {
          projectLibraryTable.commit();
        }
      });
    }
    catch (Exception e) {
      LOG.info(e);
      Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", e.getMessage()), IdeBundle.message("title.add.module"));
    }

    final Map<ModuleDescriptor, Module> descriptorToModuleMap = new HashMap<>();

    try {
      WriteAction.run(() -> {
        final ModifiableModuleModel moduleModel = fromProjectStructure ? model : ModuleManager.getInstance(project).getModifiableModel();
        for (ProjectDescriptor descriptor : getSelectedDescriptors()) {
          for (final ModuleDescriptor moduleDescriptor : descriptor.getModules()) {
            final Module module;
            if (moduleDescriptor.isReuseExistingElement()) {
              final ExistingModuleLoader moduleLoader =
                ExistingModuleLoader.setUpLoader(FileUtil.toSystemIndependentName(moduleDescriptor.computeModuleFilePath()));
              module = moduleLoader.createModule(moduleModel);
            }
            else {
              module = createModule(descriptor, moduleDescriptor, projectLibs, moduleModel);
            }
            result.add(module);
            descriptorToModuleMap.put(moduleDescriptor, module);
          }
        }

        if (!fromProjectStructure) {
          moduleModel.commit();
        }
      });
    }
    catch (Exception e) {
      LOG.info(e);
      Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", e.getMessage()), IdeBundle.message("title.add.module"));
    }

    // setup dependencies between modules
    try {
      WriteAction.run(() -> {
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
      });
    }
    catch (Exception e) {
      LOG.info(e);
      Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", e.getMessage()), IdeBundle.message("title.add.module"));
    }

    WriteAction.run(() -> {
      ModulesProvider updatedModulesProvider = fromProjectStructure ? modulesProvider : new DefaultModulesProvider(project);
      for (ProjectConfigurationUpdater updater : myUpdaters) {
        updater.updateProject(project, modelsProvider, updatedModulesProvider);
      }
    });
    return result;
  }

  @Nullable
  @Override
  public List<Module> commit(Project project,
                             ModifiableModuleModel model,
                             ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel) {
    return commit(project, model, modulesProvider);
  }

  public Collection<ProjectDescriptor> getSelectedDescriptors() {
    return myProjectDescriptors.values();
  }

  public void addConfigurationUpdater(ProjectConfigurationUpdater updater) {
    myUpdaters.add(updater);
  }

  @Override
  public boolean hasRootsFromOtherDetectors(ProjectStructureDetector thisDetector) {
    for (ProjectStructureDetector projectStructureDetector : Extensions.getExtensions(ProjectStructureDetector.EP_NAME)) {
      if (projectStructureDetector != thisDetector && !getProjectRoots(projectStructureDetector).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void setupModulesByContentRoots(ProjectDescriptor projectDescriptor, Collection<DetectedProjectRoot> roots) {
    if (projectDescriptor.getModules().isEmpty()) {
      List<ModuleDescriptor> modules = new ArrayList<>();
      for (DetectedProjectRoot root : roots) {
        if (root instanceof DetectedContentRoot) {
          modules.add(new ModuleDescriptor(root.getDirectory(), ((DetectedContentRoot)root).getModuleType(), Collections.emptyList()));
        }
      }
      projectDescriptor.setModules(modules);
    }
  }

  @NotNull
  private static Module createModule(ProjectDescriptor projectDescriptor, final ModuleDescriptor descriptor,
                                     final Map<LibraryDescriptor, Library> projectLibs, final ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {

    final String moduleFilePath = descriptor.computeModuleFilePath();
    ModuleBuilder.deleteModuleFile(moduleFilePath);

    final Module module = moduleModel.newModule(moduleFilePath, descriptor.getModuleType().getId());
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    setupRootModel(projectDescriptor, descriptor, modifiableModel, projectLibs);
    descriptor.updateModuleConfiguration(module, modifiableModel);
    modifiableModel.commit();
    return module;
  }

  private static void setupRootModel(ProjectDescriptor projectDescriptor, final ModuleDescriptor descriptor,
                                     final ModifiableRootModel rootModel, final Map<LibraryDescriptor, Library> projectLibs) {
    final CompilerModuleExtension compilerModuleExtension = rootModel.getModuleExtension(CompilerModuleExtension.class);
    compilerModuleExtension.setExcludeOutput(true);
    rootModel.inheritSdk();

    final Set<File> contentRoots = descriptor.getContentRoots();
    for (File contentRoot : contentRoots) {
      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      VirtualFile moduleContentRoot = lfs.refreshAndFindFileByPath(FileUtil.toSystemIndependentName(contentRoot.getPath()));
      if (moduleContentRoot != null) {
        final ContentEntry contentEntry = rootModel.addContentEntry(moduleContentRoot);
        final Collection<DetectedSourceRoot> sourceRoots = descriptor.getSourceRoots(contentRoot);
        for (DetectedSourceRoot srcRoot : sourceRoots) {
          final String srcpath = FileUtil.toSystemIndependentName(srcRoot.getDirectory().getPath());
          final VirtualFile sourceRoot = lfs.refreshAndFindFileByPath(srcpath);
          if (sourceRoot != null) {
            contentEntry.addSourceFolder(sourceRoot, shouldBeTestRoot(srcRoot.getDirectory()), getPackagePrefix(srcRoot));
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

  public static String getPackagePrefix(final DetectedSourceRoot srcRoot) {
    return srcRoot.getPackagePrefix();
  }

  @NotNull
  @Override
  public ProjectDescriptor getProjectDescriptor(@NotNull ProjectStructureDetector detector) {
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
           "testSrc".equalsIgnoreCase(name) ||
           "tst".equalsIgnoreCase(name);
  }

  public interface ProjectConfigurationUpdater {
    void updateProject(@NotNull Project project, @NotNull ModifiableModelsProvider modelsProvider, @NotNull ModulesProvider modulesProvider);
  }

  @Override
  public boolean isSuitableSdkType(final SdkTypeId sdkTypeId) {
    for (ProjectDescriptor projectDescriptor : getSelectedDescriptors()) {
      for (ModuleDescriptor moduleDescriptor : projectDescriptor.getModules()) {
        try {
          final ModuleType moduleType = getModuleType(moduleDescriptor);
          if (moduleType != null && !moduleType.createModuleBuilder().isSuitableSdkType(sdkTypeId)) return false;
        }
        catch (Exception ignore) {
        }
      }
    }
    return true;
  }

  @Nullable
  private static ModuleType getModuleType(ModuleDescriptor moduleDescriptor) throws InvalidDataException, JDOMException, IOException {
    if (moduleDescriptor.isReuseExistingElement()) {
      final File file = new File(moduleDescriptor.computeModuleFilePath());
      if (file.exists()) {
        final Element rootElement = JDOMUtil.load(file);
        final String type = rootElement.getAttributeValue("type");
        if (type != null) {
          return ModuleTypeManager.getInstance().findByID(type);
        }
      }
      return null;
    }
    else {
      return moduleDescriptor.getModuleType();
    }
  }
}
