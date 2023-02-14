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

  private void createProjectLevelLibraries(LibraryTable.ModifiableModel projectLibraryTable, Map<LibraryDescriptor, Library> projectLibs) {
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
  }

  private List<Module> createModules(Map<LibraryDescriptor, Library> projectLibs,
                                     Map<ModuleDescriptor, Module> descriptorToModuleMap,
                                     ModulesProvider updatedModulesProvider,
                                     ModifiableModuleModel moduleModel)
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
    for (ProjectDescriptor descriptor : getSelectedDescriptors()) {
      for (final ModuleDescriptor moduleDescriptor : descriptor.getModules()) {
        for (File contentRoot : moduleDescriptor.getContentRoots()) {
          String url = VfsUtilCore.fileToUrl(contentRoot);
          Module existingModule = contentRootToModule.get(url);
          if (existingModule != null && ArrayUtil.contains(existingModule, moduleModel.getModules())) {
            moduleModel.disposeModule(existingModule);
          }
        }
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
    return result;
  }

  private void setupDependenciesBetweenModules(@NotNull Project project,
                                               ModifiableModelsProvider modelsProvider,
                                               Map<ModuleDescriptor, Module> descriptorToModuleMap,
                                               ModulesProvider updatedModulesProvider) {
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
      Messages.showErrorDialog(IdeCoreBundle.message("error.adding.module.to.project", e.getMessage()),
                               IdeCoreBundle.message("title.add.module"));
    }

    WriteAction.run(() -> {
      for (ProjectConfigurationUpdater updater : myUpdaters) {
        updater.updateProject(project, modelsProvider, updatedModulesProvider);
      }
    });
  }

  private List<Module> doCommit(@NotNull Project project,
                                ModifiableModuleModel moduleModel,
                                ModulesProvider updatedModulesProvider,
                                boolean commitModels) {
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

    final Map<ModuleDescriptor, Module> descriptorToModuleMap = new HashMap<>();

    try {
      WriteAction.run(() -> {
        result.addAll(createModules(projectLibs, descriptorToModuleMap, updatedModulesProvider, moduleModel));
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

    setupDependenciesBetweenModules(project, modelsProvider, descriptorToModuleMap, updatedModulesProvider);
    return result;
  }

  @Override
  public List<Module> commit(@NotNull Project project, ModifiableModuleModel model, ModulesProvider modulesProvider) {
    ModulesProvider updatedModulesProvider;
    final ModifiableModuleModel moduleModel;
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

    return doCommit(project, moduleModel, updatedModulesProvider, commitModels);
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

  @NotNull
  private static Module createModule(ProjectDescriptor projectDescriptor, final ModuleDescriptor descriptor,
                                     final Map<LibraryDescriptor, Library> projectLibs, final ModifiableModuleModel moduleModel)
    throws InvalidDataException {

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
          final ModuleType<?> moduleType = getModuleType(moduleDescriptor);
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
