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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetModel;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.ui.FacetEditorImpl;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.util.graph.GraphGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 15, 2003
 */
public class ModulesConfigurator implements ModulesProvider, ModuleEditor.ChangeListener {
  private static final Logger LOG = Logger.getInstance("#" + ModulesConfigurator.class.getName());

  private final Project myProject;
  private final ProjectConfigurable myProjectConfigurable;
  private final List<ModuleEditor> myModuleEditors = new ArrayList<ModuleEditor>();
  private final Comparator<ModuleEditor> myModuleEditorComparator = new Comparator<ModuleEditor>() {
    final ModulesAlphaComparator myModulesComparator = new ModulesAlphaComparator();

    public int compare(ModuleEditor editor1, ModuleEditor editor2) {
      return myModulesComparator.compare(editor1.getModule(), editor2.getModule());
    }

    @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
    public boolean equals(Object o) {
      return false;
    }
  };
  private boolean myModified = false;
  private ModifiableModuleModel myModuleModel;
  private ProjectFacetsConfigurator myFacetsConfigurator;

  private StructureConfigurableContext myContext;
  private final List<ModuleEditor.ChangeListener> myAllModulesChangeListeners = new ArrayList<ModuleEditor.ChangeListener>();

  public ModulesConfigurator(Project project, ProjectSdksModel projectJdksModel) {
    myProject = project;
    myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    myProjectConfigurable = new ProjectConfigurable(project, this, projectJdksModel);
  }

  public void setContext(final StructureConfigurableContext context) {
    myContext = context;
    myFacetsConfigurator = createFacetsConfigurator();
  }

  public ProjectFacetsConfigurator getFacetsConfigurator() {
    return myFacetsConfigurator;
  }

  public void disposeUIResources() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (final ModuleEditor moduleEditor : myModuleEditors) {
          Disposer.dispose(moduleEditor);
        }
        myModuleEditors.clear();

        myModuleModel.dispose();

        myFacetsConfigurator.disposeEditors();
      }
    });

  }

  public ProjectConfigurable getModulesConfigurable() {
    return myProjectConfigurable;
  }

  @NotNull
  public Module[] getModules() {
    return myModuleModel.getModules();
  }

  @Nullable
  public Module getModule(String name) {
    final Module moduleByName = myModuleModel.findModuleByName(name);
    if (moduleByName != null) {
      return moduleByName;
    }
    return myModuleModel.getModuleToBeRenamed(name); //if module was renamed
  }

  @Nullable
  public ModuleEditor getModuleEditor(Module module) {
    for (final ModuleEditor moduleEditor : myModuleEditors) {
      if (module.equals(moduleEditor.getModule())) {
        return moduleEditor;
      }
    }
    return null;
  }

  public ModuleRootModel getRootModel(@NotNull Module module) {
    return getOrCreateModuleEditor(module).getRootModel();
  }

  public ModuleEditor getOrCreateModuleEditor(Module module) {
    LOG.assertTrue(getModule(module.getName()) != null, "Module has been deleted");
    ModuleEditor editor = getModuleEditor(module);
    if (editor == null) {
      editor = doCreateModuleEditor(module);
    }
    return editor;
  }

  private ModuleEditor doCreateModuleEditor(final Module module) {
    final ModuleEditor moduleEditor = new ModuleEditor(myProject, this, module) {
      @Override
      public ProjectFacetsConfigurator getFacetsConfigurator() {
        return myFacetsConfigurator;
      }
    };

    final ProjectFacetsConfigurator configurator = myFacetsConfigurator;
    moduleEditor.addChangeListener(configurator);
    myModuleEditors.add(moduleEditor);

    moduleEditor.addChangeListener(this);
    Disposer.register(moduleEditor, new Disposable() {
      public void dispose() {
        moduleEditor.removeChangeListener(ModulesConfigurator.this);
        moduleEditor.removeChangeListener(configurator);
      }
    });
    return moduleEditor;
  }

  public FacetModel getFacetModel(@NotNull Module module) {
    return myFacetsConfigurator.getOrCreateModifiableModel(module);
  }

  public void resetModuleEditors() {
    myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        assert myModuleEditors.isEmpty();
        final Module[] modules = myModuleModel.getModules();
        if (modules.length > 0) {
          for (Module module : modules) {
            getOrCreateModuleEditor(module);
          }
          Collections.sort(myModuleEditors, myModuleEditorComparator);
        }
      }
    });
    myFacetsConfigurator.resetEditors();
    myModified = false;
  }

  public void moduleStateChanged(final ModifiableRootModel moduleRootModel) {
    myProjectConfigurable.updateCircularDependencyWarning();
    for (ModuleEditor.ChangeListener listener : myAllModulesChangeListeners) {
      listener.moduleStateChanged(moduleRootModel);
    }
  }

  public void addAllModuleChangeListener(ModuleEditor.ChangeListener listener) {
    myAllModulesChangeListeners.add(listener);
  }

  public GraphGenerator<ModifiableRootModel> createGraphGenerator() {
    final Map<Module, ModifiableRootModel> models = new HashMap<Module, ModifiableRootModel>();
    for (ModuleEditor moduleEditor : myModuleEditors) {
      models.put(moduleEditor.getModule(), moduleEditor.getModifiableRootModel());
    }
    return ModuleCompilerUtil.createGraphGenerator(models);
  }

  public void apply() throws ConfigurationException {
    // validate content and source roots 
    final Map<VirtualFile, String> contentRootToModuleNameMap = new com.intellij.util.containers.HashMap<VirtualFile, String>();
    final Map<VirtualFile, VirtualFile> srcRootsToContentRootMap = new com.intellij.util.containers.HashMap<VirtualFile, VirtualFile>();
    for (final ModuleEditor moduleEditor : myModuleEditors) {
      final ModifiableRootModel rootModel = moduleEditor.getModifiableRootModel();
      final ContentEntry[] contents = rootModel.getContentEntries();
      for (ContentEntry contentEntry : contents) {
        final VirtualFile contentRoot = contentEntry.getFile();
        if (contentRoot == null) {
          continue;
        }
        final String moduleName = moduleEditor.getName();
        final String previousName = contentRootToModuleNameMap.put(contentRoot, moduleName);
        if (previousName != null && !previousName.equals(moduleName)) {
          throw new ConfigurationException(
            ProjectBundle.message("module.paths.validation.duplicate.content.error", contentRoot.getPresentableUrl(), previousName, moduleName)
          );
        }
        for (VirtualFile srcRoot : contentEntry.getSourceFolderFiles()) {
          final VirtualFile anotherContentRoot = srcRootsToContentRootMap.put(srcRoot, contentRoot);
          if (anotherContentRoot != null) {
            final String problematicModule;
            final String correctModule;
            if (VfsUtil.isAncestor(anotherContentRoot, contentRoot, true)) {
              problematicModule = contentRootToModuleNameMap.get(anotherContentRoot);
              correctModule = contentRootToModuleNameMap.get(contentRoot);
            }
            else {
              problematicModule = contentRootToModuleNameMap.get(contentRoot);
              correctModule = contentRootToModuleNameMap.get(anotherContentRoot);
            }
            throw new ConfigurationException(
              ProjectBundle.message("module.paths.validation.duplicate.source.root.error", problematicModule, srcRoot.getPresentableUrl(), correctModule)
            );
          }
        }
      }
    }
    // additional validation: directories marked as src roots must belong to the same module as their corresponding content root
    for (Map.Entry<VirtualFile, VirtualFile> entry : srcRootsToContentRootMap.entrySet()) {
      final VirtualFile srcRoot = entry.getKey();
      final VirtualFile correspondingContent = entry.getValue();
      final String expectedModuleName = contentRootToModuleNameMap.get(correspondingContent);

      for (VirtualFile candidateContent = srcRoot; !candidateContent.equals(correspondingContent); candidateContent = candidateContent.getParent()) {
        final String moduleName = contentRootToModuleNameMap.get(candidateContent);
        if (moduleName != null && !moduleName.equals(expectedModuleName)) {
          throw new ConfigurationException(
            ProjectBundle.message("module.paths.validation.source.root.belongs.to.another.module.error", srcRoot.getPresentableUrl(), expectedModuleName, moduleName)
          );
        }
      }
    }

    final ProjectRootManagerImpl projectRootManager = ProjectRootManagerImpl.getInstanceImpl(myProject);
    final List<ModifiableRootModel> models = new ArrayList<ModifiableRootModel>(myModuleEditors.size());
    for (ModuleEditor moduleEditor : myModuleEditors) {
      moduleEditor.canApply();
    }
    for (final ModuleEditor moduleEditor : myModuleEditors) {
      final ModifiableRootModel model = moduleEditor.apply();
      if (model != null) {
        models.add(model);
      }
    }
    myFacetsConfigurator.applyEditors();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          final ModifiableRootModel[] rootModels = models.toArray(new ModifiableRootModel[models.size()]);
          projectRootManager.multiCommit(myModuleModel, rootModels);
          myFacetsConfigurator.commitFacets();

        }
        finally {
          ModuleStructureConfigurable.getInstance(myProject).getFacetEditorFacade().clearMaps(false);

          for (final ModuleEditor moduleEditor : myModuleEditors) {
            moduleEditor.removeChangeListener(myFacetsConfigurator);
          }

          myFacetsConfigurator = createFacetsConfigurator();
          myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();

          final ProjectFacetsConfigurator configurator = myFacetsConfigurator;

          for (final ModuleEditor moduleEditor : myModuleEditors) {
            moduleEditor.addChangeListener(configurator);
            Disposer.register(moduleEditor, new Disposable() {
              public void dispose() {
                moduleEditor.removeChangeListener(configurator);
              }
            });
          }
        }
      }
    });

    myModified = false;
  }

  private ProjectFacetsConfigurator createFacetsConfigurator() {
    return new ProjectFacetsConfigurator(myContext, myFacetsConfigurator);
  }

  public void setModified(final boolean modified) {
    myModified = modified;
  }

  public ModifiableModuleModel getModuleModel() {
    return myModuleModel;
  }

  public boolean deleteModule(final Module module) {
    return doRemoveModule(getModuleEditor(module));
  }


  @Nullable
  public List<Module> addModule(Component parent) {
    if (myProject.isDefault()) return null;
    final ProjectBuilder builder = runModuleWizard(parent);
    if (builder != null ) {
      final List<Module> modules = new ArrayList<Module>();
      final List<Module> commitedModules;
      if (builder instanceof ProjectImportBuilder<?>) {
        final ModifiableArtifactModel artifactModel =
            ProjectStructureConfigurable.getInstance(myProject).getArtifactsStructureConfigurable().getModifiableArtifactModel();
        commitedModules = ((ProjectImportBuilder<?>)builder).commit(myProject, myModuleModel, this, artifactModel);
      }
      else {
        commitedModules = builder.commit(myProject, myModuleModel, this);
      }
      if (commitedModules != null) {
        modules.addAll(commitedModules);
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
         public void run() {
           for (Module module : modules) {
             getOrCreateModuleEditor(module);
           }
         }
       });
      return modules;
    }
    return null;
  }

  private Module createModule(final ModuleBuilder builder) {
    final Exception[] ex = new Exception[]{null};
    final Module module = ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
      @SuppressWarnings({"ConstantConditions"})
      public Module compute() {
        try {
          return builder.createModule(myModuleModel);
        }
        catch (Exception e) {
          ex[0] = e;
          return null;
        }
      }
    });
    if (ex[0] != null) {
      Messages.showErrorDialog(ProjectBundle.message("module.add.error.message", ex[0].getMessage()),
                               ProjectBundle.message("module.add.error.title"));
    }
    return module;
  }

  @Nullable
  public Module addModule(final ModuleBuilder moduleBuilder) {
    final Module module = createModule(moduleBuilder);
    if (module != null) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          getOrCreateModuleEditor(module);
          Collections.sort(myModuleEditors, myModuleEditorComparator);
        }
      });
      processModuleCountChanged();
    }
    return module;
  }

  @Nullable
  ProjectBuilder runModuleWizard(Component dialogParent) {
    AddModuleWizard wizard = new AddModuleWizard(dialogParent, myProject, this);
    wizard.show();
    if (wizard.isOK()) {
      final ProjectBuilder builder = wizard.getProjectBuilder();
      if (builder instanceof ModuleBuilder) {
        final ModuleBuilder moduleBuilder = (ModuleBuilder)builder;
        if (moduleBuilder.getName() == null) {
          moduleBuilder.setName(wizard.getProjectName());
        }
        if (moduleBuilder.getModuleFilePath() == null) {
          moduleBuilder.setModuleFilePath(wizard.getModuleFilePath());
        }
      }
      if (!builder.validate(myProject, myProject)) {
        return null;
      }
      return wizard.getProjectBuilder();
    }

    return null;
  }


  private boolean doRemoveModule(ModuleEditor selectedEditor) {

    String question;
    if (myModuleEditors.size() == 1) {
      question = ProjectBundle.message("module.remove.last.confirmation");
    }
    else {
      question = ProjectBundle.message("module.remove.confirmation", selectedEditor.getModule().getName());
    }
    int result =
      Messages.showYesNoDialog(myProject, question, ProjectBundle.message("module.remove.confirmation.title"), Messages.getQuestionIcon());
    if (result != 0) {
      return false;
    }
    // do remove
    myModuleEditors.remove(selectedEditor);

    // destroyProcess removed module
    final Module moduleToRemove = selectedEditor.getModule();
    // remove all dependencies on the module that is about to be removed
    List<ModifiableRootModel> modifiableRootModels = new ArrayList<ModifiableRootModel>();
    for (final ModuleEditor moduleEditor : myModuleEditors) {
      final ModifiableRootModel modifiableRootModel = moduleEditor.getModifiableRootModelProxy();
      modifiableRootModels.add(modifiableRootModel);
    }

    // destroyProcess editor
    ModuleDeleteProvider.removeModule(moduleToRemove, null, modifiableRootModels, myModuleModel);
    processModuleCountChanged();
    Disposer.dispose(selectedEditor);
    
    return true;
  }


  private void processModuleCountChanged() {
    for (ModuleEditor moduleEditor : myModuleEditors) {
      moduleEditor.moduleCountChanged();
    }
  }

  public void processModuleCompilerOutputChanged(String baseUrl) {
    for (ModuleEditor moduleEditor : myModuleEditors) {
      moduleEditor.updateCompilerOutputPathChanged(baseUrl, moduleEditor.getName());
    }
  }

  public boolean isModified() {
    if (myModuleModel.isChanged()) {
      return true;
    }
    for (ModuleEditor moduleEditor : myModuleEditors) {
      if (moduleEditor.isModified()) {
        return true;
      }
    }
    return myModified || myFacetsConfigurator.isModified();
  }

  public static boolean showSdkSettings(@NotNull Project project, final Sdk sdk) {
    final ProjectStructureConfigurable configurable = ProjectStructureConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, configurable, new Runnable() {
      public void run() {
        configurable.select(sdk, true);
      }
    });
  }

  public static boolean showLibrarySettings(@NotNull Project project, @NotNull final LibraryOrderEntry library) {
    final ProjectStructureConfigurable configurable = ProjectStructureConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, configurable, new Runnable() {
      public void run() {
        configurable.select(library, true);
      }
    });
  }

  public static boolean showArtifactSettings(@NotNull Project project, @Nullable final Artifact artifact) {
    final ProjectStructureConfigurable configurable = ProjectStructureConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, configurable, new Runnable() {
      public void run() {
        configurable.select(artifact, true);
      }
    });
  }

  public static boolean showFacetSettingsDialog(@NotNull final Facet facet,
                                                @Nullable final String tabNameToSelect) {
    final Project project = facet.getModule().getProject();
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, config, new Runnable() {
      public void run() {
        final ModuleStructureConfigurable modulesConfig = config.getModulesConfig();
        config.select(facet, true).doWhenDone(new Runnable() {
          public void run() {
            if (tabNameToSelect != null) {
              FacetEditorImpl facetEditor = modulesConfig.getFacetConfigurator().getOrCreateEditor(facet);
              facetEditor.setSelectedTabName(tabNameToSelect);
            }
          }
        });
      }
    });
  }

  public static boolean showDialog(Project project,
                                   @Nullable final String moduleToSelect,
                                   final String tabNameToSelect,
                                   final boolean showModuleWizard) {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, config, new Runnable() {
      public void run() {
        final ModuleStructureConfigurable modulesConfig = config.getModulesConfig();
        config.select(moduleToSelect, tabNameToSelect, true).doWhenDone(new Runnable() {
          public void run() {
            modulesConfig.setStartModuleWizard(showModuleWizard);
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                modulesConfig.setStartModuleWizard(false);
              }
            });
          }
        });
      }
    });
  }

  public void moduleRenamed(Module module, final String oldName, final String name) {
    for (ModuleEditor moduleEditor : myModuleEditors) {
      if (module == moduleEditor.getModule() && Comparing.strEqual(moduleEditor.getName(), oldName)) {
        moduleEditor.setModuleName(name);
        moduleEditor.updateCompilerOutputPathChanged(ProjectStructureConfigurable.getInstance(myProject).getProjectConfig().getCompilerOutputUrl(), name);
        myContext.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(myContext, module), true, false);
        return;
      }
    }
  }

}
