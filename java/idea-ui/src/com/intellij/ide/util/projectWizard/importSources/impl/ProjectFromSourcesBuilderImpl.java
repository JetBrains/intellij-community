// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard.importSources.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.util.projectWizard.importSources.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
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

  @Override
  public @NotNull Set<String> getExistingModuleNames() {
    if (myModuleNames == null) {
      myModuleNames = new HashSet<>();
      for (Module module : myModulesProvider.getModules()) {
        myModuleNames.add(module.getName());
      }
    }
    return myModuleNames;
  }

  @Override
  public @NotNull Set<String> getExistingProjectLibraryNames() {
    if (myProjectLibrariesNames == null) {
      myProjectLibrariesNames = new HashSet<>();
      LibrariesContainer container = LibrariesContainerFactory.createContainer(myContext, myModulesProvider);
      for (Library library : container.getLibraries(LibrariesContainer.LibraryLevel.PROJECT)) {
        myProjectLibrariesNames.add(library.getName());
      }
    }
    return myProjectLibrariesNames;
  }

  @Override
  public @NotNull WizardContext getContext() {
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

  @Override
  public @NotNull Collection<DetectedProjectRoot> getProjectRoots(@NotNull ProjectStructureDetector detector) {
    return myRoots.get(detector);
  }

  @Override
  public @NotNull String getName() {
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
  public @NotNull List<Module> commit(@NotNull Project project, ModifiableModuleModel model, ModulesProvider modulesProvider) {
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
      getSelectedDescriptors()).commit();
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

  @Override
  public @NotNull ProjectDescriptor getProjectDescriptor(@NotNull ProjectStructureDetector detector) {
    return myProjectDescriptors.get(detector);
  }

  static boolean isTestRootName(String name) {
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

  private static @Nullable ModuleType<?> getModuleType(ModuleDescriptor moduleDescriptor) throws InvalidDataException, JDOMException, IOException {
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
