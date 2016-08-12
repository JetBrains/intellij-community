/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.facet.Facet;
import com.intellij.facet.FacetModel;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.ui.FacetEditorImpl;
import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.projectWizard.NewProjectWizard;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
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
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  private final Map<Module, ModuleEditor> myModuleEditors = new TreeMap<>((o1, o2) -> {
    String n1 = o1.getName();
    String n2 = o2.getName();
    int result = n1.compareToIgnoreCase(n2);
    if (result != 0) {
      return result;
    }
    return n1.compareTo(n2);
  });

  private boolean myModified = false;
  private ModifiableModuleModel myModuleModel;
  private boolean myModuleModelCommitted = false;
  private ProjectFacetsConfigurator myFacetsConfigurator;

  private StructureConfigurableContext myContext;
  private final List<ModuleEditor.ChangeListener> myAllModulesChangeListeners = new ArrayList<>();

  public ModulesConfigurator(Project project) {
    myProject = project;
    myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();
  }

  public void setContext(final StructureConfigurableContext context) {
    myContext = context;
    myFacetsConfigurator = createFacetsConfigurator();
  }

  public ProjectFacetsConfigurator getFacetsConfigurator() {
    return myFacetsConfigurator;
  }

  public void disposeUIResources() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      for (final ModuleEditor moduleEditor : myModuleEditors.values()) {
        Disposer.dispose(moduleEditor);
      }
      myModuleEditors.clear();

      myModuleModel.dispose();

      if (myFacetsConfigurator != null) {
        myFacetsConfigurator.disposeEditors();
      }
    });
  }

  @Override
  @NotNull
  public Module[] getModules() {
    return myModuleModel.getModules();
  }

  @Override
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
    return myModuleEditors.get(module);
  }

  @Override
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
    final ModuleEditor moduleEditor = new HeaderHidingTabbedModuleEditor(myProject, this, module) {
      @Override
      public ProjectFacetsConfigurator getFacetsConfigurator() {
        return myFacetsConfigurator;
      }
    };

    myModuleEditors.put(moduleEditor.getModule(), moduleEditor);

    moduleEditor.addChangeListener(this);
    Disposer.register(moduleEditor, new Disposable() {
      @Override
      public void dispose() {
        moduleEditor.removeChangeListener(ModulesConfigurator.this);
      }
    });
    return moduleEditor;
  }

  @Override
  public FacetModel getFacetModel(@NotNull Module module) {
    return myFacetsConfigurator.getOrCreateModifiableModel(module);
  }

  public void resetModuleEditors() {
    myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();

    ApplicationManager.getApplication().runWriteAction(() -> {
      if (!myModuleEditors.isEmpty()) {
        LOG.error("module editors was not disposed");
        myModuleEditors.clear();
      }
      final Module[] modules = myModuleModel.getModules();
      if (modules.length > 0) {
        for (Module module : modules) {
          getOrCreateModuleEditor(module);
        }
      }
    });
    myFacetsConfigurator.resetEditors();
    myModified = false;
  }

  @Override
  public void moduleStateChanged(final ModifiableRootModel moduleRootModel) {
    for (ModuleEditor.ChangeListener listener : myAllModulesChangeListeners) {
      listener.moduleStateChanged(moduleRootModel);
    }
    myContext.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(myContext, moduleRootModel.getModule()));
  }

  public void addAllModuleChangeListener(ModuleEditor.ChangeListener listener) {
    myAllModulesChangeListeners.add(listener);
  }

  public void apply() throws ConfigurationException {
    // validate content and source roots 
    final Map<VirtualFile, String> contentRootToModuleNameMap = new HashMap<>();
    final Map<VirtualFile, VirtualFile> srcRootsToContentRootMap = new HashMap<>();
    for (final ModuleEditor moduleEditor : myModuleEditors.values()) {
      final ModifiableRootModel rootModel = moduleEditor.getModifiableRootModel();
      final ContentEntry[] contents = rootModel.getContentEntries();
      final String moduleName = moduleEditor.getName();
      Set<VirtualFile> sourceRoots = new HashSet<>();
      for (ContentEntry content : contents) {
        for (VirtualFile root : content.getSourceFolderFiles()) {
          if (!sourceRoots.add(root)) {
            throw new ConfigurationException(ProjectBundle.message("module.paths.validation.duplicate.source.root.in.same.module.error", root.getPresentableUrl(), moduleName));
          }
        }
      }

      for (ContentEntry contentEntry : contents) {
        final VirtualFile contentRoot = contentEntry.getFile();
        if (contentRoot == null) {
          continue;
        }
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
            if (VfsUtilCore.isAncestor(anotherContentRoot, contentRoot, true)) {
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

      for (VirtualFile candidateContent = srcRoot; candidateContent != null && !candidateContent.equals(correspondingContent); candidateContent = candidateContent.getParent()) {
        final String moduleName = contentRootToModuleNameMap.get(candidateContent);
        if (moduleName != null && !moduleName.equals(expectedModuleName)) {
          throw new ConfigurationException(
            ProjectBundle.message("module.paths.validation.source.root.belongs.to.another.module.error", srcRoot.getPresentableUrl(), expectedModuleName, moduleName)
          );
        }
      }
    }

    final List<ModifiableRootModel> models = new ArrayList<>(myModuleEditors.size());
    for (ModuleEditor moduleEditor : myModuleEditors.values()) {
      moduleEditor.canApply();
    }
    
    final Map<Sdk, Sdk> modifiedToOriginalMap = new HashMap<>();
    final ProjectSdksModel projectJdksModel = ProjectStructureConfigurable.getInstance(myProject).getProjectJdksModel();
    for (Map.Entry<Sdk, Sdk> entry : projectJdksModel.getProjectSdks().entrySet()) {
      modifiedToOriginalMap.put(entry.getValue(), entry.getKey());
    }

    final Ref<ConfigurationException> exceptionRef = Ref.create();
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        for (final ModuleEditor moduleEditor : myModuleEditors.values()) {
          final ModifiableRootModel model = moduleEditor.apply();
          if (model != null) {
            if (!model.isSdkInherited()) {
              // make sure the sdk is set to original SDK stored in the JDK Table
              final Sdk modelSdk = model.getSdk();
              if (modelSdk != null) {
                final Sdk original = modifiedToOriginalMap.get(modelSdk);
                if (original != null) {
                  model.setSdk(original);
                }
              }
            }
            models.add(model);
          }
        }
        myFacetsConfigurator.applyEditors();
      }
      catch (ConfigurationException e) {
        exceptionRef.set(e);
        return;
      }

      try {
        final ModifiableRootModel[] rootModels = models.toArray(new ModifiableRootModel[models.size()]);
        ModifiableModelCommitter.multiCommit(rootModels, myModuleModel);
        myModuleModelCommitted = true;
        myFacetsConfigurator.commitFacets();

      }
      finally {
        ModuleStructureConfigurable.getInstance(myProject).getFacetEditorFacade().clearMaps(false);

        myFacetsConfigurator = createFacetsConfigurator();
        myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();
        myModuleModelCommitted = false;
      }
    });

    if (!exceptionRef.isNull()) {
      throw exceptionRef.get();
    }

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

  public boolean isModuleModelCommitted() {
    return myModuleModelCommitted;
  }

  public List<Module> deleteModules(final Collection<Module> modules) {
    List<Module> deleted = new ArrayList<>();
    List<ModuleEditor> moduleEditors = new ArrayList<>();
    for (Module module : modules) {
      ModuleEditor moduleEditor = getModuleEditor(module);
      if (moduleEditor != null) {
        deleted.add(module);
        moduleEditors.add(moduleEditor);
      }
    }
    if (doRemoveModules(moduleEditors)) {
      return deleted;
    }
    return Collections.emptyList();
  }


  @Nullable
  public List<Module> addModule(Component parent, boolean anImport) {
    if (myProject.isDefault()) return null;
    final ProjectBuilder builder = runModuleWizard(parent, anImport);
    if (builder != null ) {
      final List<Module> modules = new ArrayList<>();
      DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, () -> {
        final List<Module> committedModules;
        if (builder instanceof ProjectImportBuilder<?>) {
          final ModifiableArtifactModel artifactModel =
            ProjectStructureConfigurable.getInstance(myProject).getArtifactsStructureConfigurable().getModifiableArtifactModel();
          committedModules = ((ProjectImportBuilder<?>)builder).commit(myProject, myModuleModel, this, artifactModel);
        }
        else {
          committedModules = builder.commit(myProject, myModuleModel, this);
        }
        if (committedModules != null) {
          modules.addAll(committedModules);
        }
        ApplicationManager.getApplication().runWriteAction(() -> {
          for (Module module : modules) {
            getOrCreateModuleEditor(module);
          }
        });
      });
      return modules;
    }
    return null;
  }

  private Module createModule(final ModuleBuilder builder) {
    try {
      return ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Module, Exception>() {
        @Override
        public Module compute() throws Exception {
          return builder.createModule(myModuleModel);
        }
      });
    }
    catch (Exception e) {
      Messages.showErrorDialog(ProjectBundle.message("module.add.error.message", e.getMessage()),
                               ProjectBundle.message("module.add.error.title"));
      return null;
    }
  }

  @Nullable
  public Module addModule(final ModuleBuilder moduleBuilder) {
    final Module module = createModule(moduleBuilder);
    if (module != null) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        getOrCreateModuleEditor(module);
      });
      processModuleCountChanged();
    }
    return module;
  }

  @Nullable
  ProjectBuilder runModuleWizard(Component dialogParent, boolean anImport) {
    AbstractProjectWizard wizard;
    if (anImport) {
      wizard = ImportModuleAction.selectFileAndCreateWizard(myProject, dialogParent);
      if (wizard == null) return null;
      if (wizard.getStepCount() == 0) {
        ProjectBuilder builder = wizard.getProjectBuilder();
        Disposer.dispose(wizard.getDisposable());
        return builder;
      }
    }
    else {
      wizard = new NewProjectWizard(myProject, dialogParent, this);
    }
    if (wizard.showAndGet()) {
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


  private boolean doRemoveModules(@NotNull List<ModuleEditor> selectedEditors) {
    if (selectedEditors.isEmpty()) return true;

    String question;
    if (myModuleEditors.size() == selectedEditors.size()) {
      question = ProjectBundle.message("module.remove.last.confirmation", selectedEditors.size());
    }
    else {
      question = ProjectBundle.message("module.remove.confirmation", selectedEditors.get(0).getModule().getName(), selectedEditors.size());
    }
    int result =
      Messages.showYesNoDialog(myProject, question, ProjectBundle.message("module.remove.confirmation.title", selectedEditors.size()), Messages.getQuestionIcon());
    if (result != Messages.YES) {
      return false;
    }
    for (ModuleEditor editor : selectedEditors) {
      myModuleEditors.remove(editor.getModule());

      final Module moduleToRemove = editor.getModule();
      // remove all dependencies on the module which is about to be removed
      List<ModifiableRootModel> modifiableRootModels = new ArrayList<>();
      for (final ModuleEditor moduleEditor : myModuleEditors.values()) {
        final ModifiableRootModel modifiableRootModel = moduleEditor.getModifiableRootModelProxy();
        ContainerUtil.addIfNotNull(modifiableRootModels, modifiableRootModel);
      }

      ModuleDeleteProvider.removeModule(moduleToRemove, null, modifiableRootModels, myModuleModel);
      Disposer.dispose(editor);
    }
    processModuleCountChanged();

    return true;
  }


  private void processModuleCountChanged() {
    for (ModuleEditor moduleEditor : myModuleEditors.values()) {
      moduleEditor.moduleCountChanged();
    }
  }

  public void processModuleCompilerOutputChanged(String baseUrl) {
    for (ModuleEditor moduleEditor : myModuleEditors.values()) {
      moduleEditor.updateCompilerOutputPathChanged(baseUrl, moduleEditor.getName());
    }
  }

  public boolean isModified() {
    if (myModuleModel.isChanged()) {
      return true;
    }
    for (ModuleEditor moduleEditor : myModuleEditors.values()) {
      if (moduleEditor.isModified()) {
        return true;
      }
    }
    return myModified || myFacetsConfigurator.isModified();
  }

  public static boolean showArtifactSettings(@NotNull Project project, @Nullable final Artifact artifact) {
    final ProjectStructureConfigurable configurable = ProjectStructureConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> configurable.select(artifact, true));
  }

  public static boolean showFacetSettingsDialog(@NotNull final Facet facet,
                                                @Nullable final String tabNameToSelect) {
    final Project project = facet.getModule().getProject();
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, config, () -> {
      final ModuleStructureConfigurable modulesConfig = config.getModulesConfig();
      config.select(facet, true).doWhenDone(() -> {
        if (tabNameToSelect != null) {
          FacetEditorImpl facetEditor = modulesConfig.getFacetConfigurator().getOrCreateEditor(facet);
          facetEditor.setSelectedTabName(tabNameToSelect);
        }
      });
    });
  }

  public static boolean showDialog(Project project, @Nullable final String moduleToSelect, @Nullable final String editorNameToSelect) {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, config, () -> config.select(moduleToSelect, editorNameToSelect, true));
  }

  public void moduleRenamed(Module module, final String oldName, final String name) {
    ModuleEditor moduleEditor = myModuleEditors.get(module);
    if (moduleEditor != null) {
      moduleEditor.setModuleName(name);
      moduleEditor.updateCompilerOutputPathChanged(
        ProjectStructureConfigurable.getInstance(myProject).getProjectConfig().getCompilerOutputUrl(), name);
      myContext.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(myContext, module));
    }
  }

  public StructureConfigurableContext getContext() {
    return myContext;
  }
}
