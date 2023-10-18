// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard.importSources.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.JavaUiBundle;
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
import com.intellij.openapi.module.Module;
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
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
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
public final class ProjectFromSourcesBuilderImpl extends ProjectImportBuilder implements ProjectFromSourcesBuilder {
  private static final Logger LOG = Logger.getInstance(ProjectFromSourcesBuilderImpl.class);
  private String myBaseProjectPath;
  private final List<ProjectConfigurationUpdater> myUpdaters = new ArrayList<>();
  private final Map<ProjectStructureDetector, ProjectDescriptor> myProjectDescriptors = new LinkedHashMap<>();
  private MultiMap<ProjectStructureDetector, DetectedProjectRoot> myRoots = MultiMap.empty();
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
      LibrariesContainer container = LibrariesContainerFactory.createContainer(myContext, myModulesProvider);
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

  public void setBaseProjectPath(String contentRootPath) {
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
    return JavaUiBundle.message("existing.sources");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Folder;
  }

  @Override
  public boolean isMarked(Object element) {
    return false;
  }

  @Override
  public void setOpenProjectSettingsAfter(boolean on) {
  }

  @Override
  public void setFileToImport(@NotNull String path) {
    setBaseProjectPath(path);
  }

  @Override
  public List<Module> commit(@NotNull Project project, ModifiableModuleModel model, ModulesProvider modulesProvider) {
    ModulesProvider updatedModulesProvider;
    ModifiableModuleModel moduleModel;
    boolean commitModels;

    if (model != null) {
      // from project structure
      moduleModel = model;
      updatedModulesProvider = modulesProvider;
      commitModels = false;
    } else {
      moduleModel = ModuleManager.getInstance(project).getModifiableModel();
      updatedModulesProvider = new DefaultModulesProvider(project);
      commitModels = true;
    }

    return new ProjectFromSourcesBuilderHelper(
      project,
      moduleModel,
      updatedModulesProvider,
      commitModels,
      myUpdaters,
      getSelectedDescriptors()).doCommit();
  }

  private static class ProjectFromSourcesBuilderHelper {
    private final @NotNull Project project;
    private final ModifiableModuleModel moduleModel;
    private final ModulesProvider updatedModulesProvider;
    private final boolean commitModels;
    private final List<ProjectConfigurationUpdater> myUpdaters;
    private final Collection<ProjectDescriptor> selectedDescriptors;

    private ProjectFromSourcesBuilderHelper(@NotNull Project project,
                                            ModifiableModuleModel moduleModel,
                                            ModulesProvider updatedModulesProvider,
                                            boolean commitModels,
                                            List<ProjectConfigurationUpdater> updaters,
                                            Collection<ProjectDescriptor> selectedDescriptors) {
      this.project = project;
      this.moduleModel = moduleModel;
      this.updatedModulesProvider = updatedModulesProvider;
      this.commitModels = commitModels;
      myUpdaters = updaters;
      this.selectedDescriptors = selectedDescriptors;
    }

    @RequiresEdt
    private List<Module> doCommit() {
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
              contentEntry.addSourceFolder(sourceRoot, shouldBeTestRoot(srcRoot.getDirectory()), getPackagePrefix(srcRoot));
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
        for (ProjectConfigurationUpdater updater : myUpdaters) {
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

  @Override
  public @NotNull List<Module> commit(Project project,
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
    for (ProjectStructureDetector projectStructureDetector : ProjectStructureDetector.EP_NAME.getExtensionList()) {
      if (projectStructureDetector != thisDetector && !getProjectRoots(projectStructureDetector).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void setupModulesByContentRoots(ProjectDescriptor projectDescriptor, Collection<? extends DetectedProjectRoot> roots) {
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

  public static String getPackagePrefix(DetectedSourceRoot srcRoot) {
    return srcRoot.getPackagePrefix();
  }

  @NotNull
  @Override
  public ProjectDescriptor getProjectDescriptor(@NotNull ProjectStructureDetector detector) {
    return myProjectDescriptors.get(detector);
  }

  private static boolean shouldBeTestRoot(File srcRoot) {
    if (isTestRootName(srcRoot.getName())) {
      return true;
    }
    File parentFile = srcRoot.getParentFile();
    return parentFile != null && isTestRootName(parentFile.getName());
  }

  private static boolean isTestRootName(String name) {
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
  public boolean isSuitableSdkType(SdkTypeId sdkTypeId) {
    for (ProjectDescriptor projectDescriptor : getSelectedDescriptors()) {
      for (ModuleDescriptor moduleDescriptor : projectDescriptor.getModules()) {
        try {
          ModuleType<?> moduleType = getModuleType(moduleDescriptor);
          if (moduleType != null && !moduleType.createModuleBuilder().isSuitableSdkType(sdkTypeId)) {
            return false;
          }
        }
        catch (Exception ignore) {
        }
      }
    }
    return true;
  }

  @Nullable
  private static ModuleType<?> getModuleType(ModuleDescriptor moduleDescriptor) throws InvalidDataException, JDOMException, IOException {
    if (moduleDescriptor.isReuseExistingElement()) {
      File file = new File(moduleDescriptor.computeModuleFilePath());
      if (file.exists()) {
        Element rootElement = JDOMUtil.load(file);
        String type = rootElement.getAttributeValue("type");
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
