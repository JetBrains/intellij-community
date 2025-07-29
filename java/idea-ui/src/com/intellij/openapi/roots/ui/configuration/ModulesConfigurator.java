// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetModel;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.ui.FacetEditorImpl;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.projectWizard.NewProjectWizard;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
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
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.util.Producer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public final class ModulesConfigurator implements ModulesProvider, ModuleEditor.ChangeListener {
  private static final Logger LOG = Logger.getInstance(ModulesConfigurator.class);

  private final Project myProject;
  private final ProjectStructureConfigurable myProjectStructureConfigurable;

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
  private MutableEntityStorage myMutableEntityStorage;

  private StructureConfigurableContext myContext;
  private final List<ModuleEditor.ChangeListener> myAllModulesChangeListeners = new ArrayList<>();

  /**
   * @deprecated use {@link ModuleManager} to access modules instead
   */
  @Deprecated(forRemoval = true)
  public ModulesConfigurator(Project project) {
    this(project, ProjectStructureConfigurable.getInstance(project));
  }

  public ModulesConfigurator(Project project, ProjectStructureConfigurable projectStructureConfigurable) {
    myProject = project;
    myProjectStructureConfigurable = projectStructureConfigurable;
    initModuleModel();
  }

  private void initModuleModel() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    if (moduleManager instanceof ModuleManagerBridgeImpl) {
      myMutableEntityStorage = MutableEntityStorage.from(WorkspaceModel.getInstance(myProject).getCurrentSnapshot());
      myModuleModel = ((ModuleManagerBridgeImpl)moduleManager).getModifiableModel(myMutableEntityStorage);
    }
    else {
      myModuleModel = moduleManager.getModifiableModel();
      myMutableEntityStorage = null;
    }
  }

  public @Nullable MutableEntityStorage getWorkspaceEntityStorageBuilder() {
    return myMutableEntityStorage;
  }

  public void setContext(final StructureConfigurableContext context) {
    myContext = context;
    myFacetsConfigurator = createFacetsConfigurator();
  }

  public ProjectStructureConfigurable getProjectStructureConfigurable() {
    return myProjectStructureConfigurable;
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

      if (myModuleModel != null) {
        myModuleModel.dispose();
      }
      myMutableEntityStorage = null;

      if (myFacetsConfigurator != null) {
        myFacetsConfigurator.disposeEditors();
      }
      myModuleModel = null;
    });
  }

  @Override
  public Module @NotNull [] getModules() {
    return myModuleModel.getModules();
  }

  @Override
  public @Nullable Module getModule(@NotNull String name) {
    if (myModuleModel == null) return null;

    final Module moduleByName = myModuleModel.findModuleByName(name);
    if (moduleByName != null) {
      return moduleByName;
    }
    return myModuleModel.getModuleToBeRenamed(name); //if module was renamed
  }

  public @Nullable ModuleEditor getModuleEditor(Module module) {
    return myModuleEditors.get(module);
  }

  @Override
  public ModuleRootModel getRootModel(@NotNull Module module) {
    return getOrCreateModuleEditor(module).getRootModel();
  }

  public @NotNull ModuleEditor getOrCreateModuleEditor(@NotNull Module module) {
    String moduleName = module.getName();
    LOG.assertTrue(getModule(moduleName) != null, "Module " + moduleName + " has been deleted");
    ModuleEditor editor = getModuleEditor(module);
    if (editor == null) {
      editor = doCreateModuleEditor(module);
    }
    return editor;
  }

  private @NotNull ModuleEditor doCreateModuleEditor(@NotNull Module module) {
    final ModuleEditor moduleEditor = new HeaderHidingTabbedModuleEditor(myProject, this, module) {
      @Override
      public ProjectFacetsConfigurator getFacetsConfigurator() {
        return myFacetsConfigurator;
      }
    };

    myModuleEditors.put(moduleEditor.getModule(), moduleEditor);

    moduleEditor.addChangeListener(this);
    return moduleEditor;
  }

  @Override
  public @NotNull FacetModel getFacetModel(@NotNull Module module) {
    return myFacetsConfigurator.getOrCreateModifiableModel(module);
  }

  public void resetModuleEditors() {
    initModuleModel();

    ApplicationManager.getApplication().runWriteAction(() -> {
      if (!myModuleEditors.isEmpty()) {
        LOG.error("module editors was not disposed");
        myModuleEditors.clear();
      }
      final Module[] modules = myModuleModel.getModules();
      for (Module module : modules) {
        getOrCreateModuleEditor(module);
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
    validateContentAndSourceRoots();
    validateModuleEditors();
    
    final Map<Sdk, Sdk> modifiedToOriginalMap = createSdkMapping();

    ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Object, ConfigurationException>)() -> {
      final List<ModifiableRootModel> models = applyModuleEditors(modifiedToOriginalMap);
      commitChanges(models);
      return null;
    });

    myModified = false;
  }
  
  /**
   * Validates content and source roots across all modules.
   * Checks for duplicate source roots within the same module,
   * duplicate content roots across modules, and ensures source roots
   * belong to the correct module.
   */
  private void validateContentAndSourceRoots() throws ConfigurationException {
    final Map<VirtualFile, String> contentRootToModuleNameMap = new HashMap<>();
    final Map<VirtualFile, VirtualFile> srcRootsToContentRootMap = new HashMap<>();
    
    // First pass: validate within each module
    for (final ModuleEditor moduleEditor : myModuleEditors.values()) {
      validateModuleRoots(moduleEditor, contentRootToModuleNameMap, srcRootsToContentRootMap);
    }
    
    // Second pass: validate source roots across modules
    validateSourceRootsAcrossModules(srcRootsToContentRootMap, contentRootToModuleNameMap);
  }
  
  /**
   * Validates roots within a single module.
   */
  private static void validateModuleRoots(@NotNull ModuleEditor moduleEditor,
                                          @NotNull Map<VirtualFile, String> contentRootToModuleNameMap,
                                          @NotNull Map<VirtualFile, VirtualFile> srcRootsToContentRootMap) throws ConfigurationException {
    final ModuleRootModel rootModel = moduleEditor.getRootModel();
    final ContentEntry[] contents = rootModel.getContentEntries();
    final String moduleName = moduleEditor.getName();
    
    // Check for duplicate source roots within the same module
    Set<VirtualFile> sourceRoots = new HashSet<>();
    for (ContentEntry content : contents) {
      for (VirtualFile root : content.getSourceFolderFiles()) {
        if (!sourceRoots.add(root)) {
          throw new ConfigurationException(JavaUiBundle.message("module.paths.validation.duplicate.source.root.in.same.module.error", root.getPresentableUrl(), moduleName));
        }
      }
    }

    // Check for duplicate content roots across modules and map source roots to content roots
    for (ContentEntry contentEntry : contents) {
      final VirtualFile contentRoot = contentEntry.getFile();
      if (contentRoot == null) {
        continue;
      }
      
      // Check for duplicate content roots
      final String previousName = contentRootToModuleNameMap.put(contentRoot, moduleName);
      if (previousName != null && !previousName.equals(moduleName)) {
        throw new ConfigurationException(
          JavaUiBundle.message("module.paths.validation.duplicate.content.error", contentRoot.getPresentableUrl(), previousName, moduleName)
        );
      }
      
      // Map source roots to content roots and check for duplicates
      for (VirtualFile srcRoot : contentEntry.getSourceFolderFiles()) {
        final VirtualFile anotherContentRoot = srcRootsToContentRootMap.put(srcRoot, contentRoot);
        if (anotherContentRoot != null) {
          throwDuplicateSourceRootError(srcRoot, contentRoot, anotherContentRoot, contentRootToModuleNameMap);
        }
      }
    }
  }
  
  /**
   * Throws an appropriate error for duplicate source roots.
   */
  private static void throwDuplicateSourceRootError(@NotNull VirtualFile srcRoot,
                                                    @NotNull VirtualFile contentRoot,
                                                    @NotNull VirtualFile anotherContentRoot,
                                                    @NotNull Map<VirtualFile, String> contentRootToModuleNameMap) throws ConfigurationException {
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
      JavaUiBundle.message("module.paths.validation.duplicate.source.root.error", problematicModule, srcRoot.getPresentableUrl(), correctModule)
    );
  }
  
  /**
   * Validates that source roots belong to the same module as their corresponding content root.
   */
  private static void validateSourceRootsAcrossModules(@NotNull Map<VirtualFile, VirtualFile> srcRootsToContentRootMap,
                                                       @NotNull Map<VirtualFile, String> contentRootToModuleNameMap) throws ConfigurationException {
    for (Map.Entry<VirtualFile, VirtualFile> entry : srcRootsToContentRootMap.entrySet()) {
      final VirtualFile srcRoot = entry.getKey();
      final VirtualFile correspondingContent = entry.getValue();
      final String expectedModuleName = contentRootToModuleNameMap.get(correspondingContent);

      for (VirtualFile candidateContent = srcRoot; candidateContent != null && !candidateContent.equals(correspondingContent); candidateContent = candidateContent.getParent()) {
        final String moduleName = contentRootToModuleNameMap.get(candidateContent);
        if (moduleName != null && !moduleName.equals(expectedModuleName)) {
          throw new ConfigurationException(
            JavaUiBundle.message("module.paths.validation.source.root.belongs.to.another.module.error", srcRoot.getPresentableUrl(), expectedModuleName, moduleName)
          );
        }
      }
    }
  }
  
  /**
   * Validates that all module editors can be applied.
   */
  private void validateModuleEditors() throws ConfigurationException {
    for (ModuleEditor moduleEditor : myModuleEditors.values()) {
      moduleEditor.canApply();
    }
  }
  
  /**
   * Creates a mapping from modified SDKs to original SDKs.
   */
  private @NotNull Map<Sdk, Sdk> createSdkMapping() {
    final Map<Sdk, Sdk> modifiedToOriginalMap = new HashMap<>();
    final ProjectSdksModel projectJdksModel = myProjectStructureConfigurable.getProjectJdksModel();
    for (Map.Entry<Sdk, Sdk> entry : projectJdksModel.getProjectSdks().entrySet()) {
      modifiedToOriginalMap.put(entry.getValue(), entry.getKey());
    }
    return modifiedToOriginalMap;
  }
  
  /**
   * Applies changes from module editors and collects modifiable root models.
   * Returns the list of models to commit or an empty list if an exception occurred.
   */
  private @NotNull List<ModifiableRootModel> applyModuleEditors(@NotNull Map<Sdk, Sdk> modifiedToOriginalMap) throws ConfigurationException {
    final List<ModifiableRootModel> models = new ArrayList<>(myModuleEditors.size());
    for (final ModuleEditor moduleEditor : myModuleEditors.values()) {
      final ModifiableRootModel model = moduleEditor.apply();
      if (model != null) {
        updateModelSdk(model, modifiedToOriginalMap);
        models.add(model);
      }
    }
    myFacetsConfigurator.applyEditors();

    return models;
  }
  
  /**
   * Updates the SDK in the model to use the original SDK from the JDK Table.
   */
  private static void updateModelSdk(@NotNull ModifiableRootModel model,
                                     @NotNull Map<Sdk, Sdk> modifiedToOriginalMap) {
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
  }
  
  /**
   * Commits changes to the module model and facets, then resets the state.
   */
  private void commitChanges(@NotNull List<ModifiableRootModel> models) {
    try {
      for (ModuleEditor editor : myModuleEditors.values()) {
        editor.resetModifiableModel();
      }
      ModifiableModelCommitter.multiCommit(models, myModuleModel);
      myModuleModelCommitted = true;
      myFacetsConfigurator.commitFacets();
    }
    finally {
      cleanupAfterCommit();
    }
  }
  
  /**
   * Cleans up after committing changes.
   */
  private void cleanupAfterCommit() {
    myProjectStructureConfigurable.getModulesConfig().getFacetEditorFacade().clearMaps(false);
    myFacetsConfigurator = createFacetsConfigurator();
    initModuleModel();
    myModuleModelCommitted = false;
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

  private @Nullable List<Module> addModule(Producer<@Nullable AbstractProjectWizard> createWizardAction) {
    var wizard = createWizardAction.produce();
    if (null == wizard) return null;

    try {
      return doAddModule(wizard);
    }
    finally {
      Disposer.dispose(wizard.getDisposable());
    }
  }

  private @Nullable List<Module> doAddModule(@NotNull AbstractProjectWizard wizard) {
    var builder = runWizard(wizard);
    if (null == builder) return null;

    List<Module> modules;
    if (builder instanceof ProjectImportBuilder<?>) {
      var artifactModel = myProjectStructureConfigurable.getArtifactsStructureConfigurable().getModifiableArtifactModel();
      modules = ((ProjectImportBuilder<?>)builder).commit(myProject, myModuleModel, this, artifactModel);
    }
    else {
      modules = builder.commit(myProject, myModuleModel, this);
    }
    if (null != modules && !modules.isEmpty()) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        for (Module module : modules) {
          if (module != null && getModule(module.getName()) != null) {
            getOrCreateModuleEditor(module);
          }
        }
      });
    }
    return modules;
  }

  public @Nullable List<Module> addImportModule(Component parent) {
    if (myProject.isDefault()) return null;
    return addModule(() -> createImportModuleWizard(parent));
  }

  public @Nullable List<Module> addNewModule(@Nullable String defaultPath) {
    if (myProject.isDefault()) return null;
    return addModule(() -> createNewModuleWizard(defaultPath));
  }

  private Module createModule(final ModuleBuilder builder) {
    try {
      return ApplicationManager.getApplication().runWriteAction(
        (ThrowableComputable<Module, Exception>)() -> builder.createModule(myModuleModel));
    }
    catch (Exception e) {
      LOG.error(JavaUiBundle.message("module.add.error.message", e.getMessage()), e);
      Messages.showErrorDialog(JavaUiBundle.message("module.add.error.message", e.getMessage()),
                               JavaUiBundle.message("module.add.error.title"));
      return null;
    }
  }

  public @Nullable Module addModule(final ModuleBuilder moduleBuilder) {
    final Module module = createModule(moduleBuilder);
    if (module != null) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        getOrCreateModuleEditor(module);
      });
      processModuleCountChanged();
    }
    return module;
  }

  private @Nullable ProjectBuilder runWizard(@Nullable AbstractProjectWizard wizard) {
    if (wizard == null) return null;

    if (wizard.getStepCount() == 0) {
      var builder = wizard.getProjectBuilder();
      if (!builder.validate(myProject, myProject)) {
        builder = null;
      }
      return builder;
    }

    if (!wizard.showAndGet()) {
      return null;
    }
    return wizard.getBuilder(myProject);
  }

  private @Nullable AbstractProjectWizard createImportModuleWizard(Component dialogParent) {
    return ImportModuleAction.selectFileAndCreateWizard(myProject, dialogParent);
  }

  private @NotNull AbstractProjectWizard createNewModuleWizard(@Nullable String defaultPath) {
    var wizardFactory = ApplicationManager.getApplication().getService(NewProjectWizardFactory.class);
    return wizardFactory.create(myProject, this, defaultPath);
  }

  public void deleteModules(@NotNull List<? extends ModuleEditor> selectedEditors) {
    WriteAction.run(() -> {
      for (ModuleEditor editor : selectedEditors) {
        myModuleEditors.remove(editor.getModule());

        final Module moduleToRemove = editor.getModule();
        // remove all dependencies on the module which is about to be removed
        List<ModifiableRootModel> modifiableRootModels = new ArrayList<>();
        for (final ModuleEditor moduleEditor : myModuleEditors.values()) {
          final ModifiableRootModel modifiableRootModel = moduleEditor.getModifiableRootModelProxy();
          ContainerUtil.addIfNotNull(modifiableRootModels, modifiableRootModel);
        }

        ModuleDeleteProvider.removeModule(moduleToRemove, modifiableRootModels, myModuleModel);
        Disposer.dispose(editor);
      }
    });
    processModuleCountChanged();
  }

  public boolean canDeleteModules(@NotNull List<? extends ModuleEditor> selectedEditors) {
    String question;
    if (myModuleEditors.size() == selectedEditors.size()) {
      question = JavaUiBundle.message("module.remove.last.confirmation", selectedEditors.size());
    }
    else {
      question = JavaUiBundle.message("module.remove.confirmation", selectedEditors.get(0).getModule().getName(), selectedEditors.size());
    }
    int result =
      Messages.showYesNoDialog(myProject, question, JavaUiBundle.message("module.remove.confirmation.title", selectedEditors.size()), Messages.getQuestionIcon());
    return result == Messages.YES;
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

  public static boolean showArtifactSettings(@NotNull Project project, final @Nullable Artifact artifact) {
    final ProjectStructureConfigurable configurable = ProjectStructureConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> configurable.select(artifact, true));
  }

  public static boolean showFacetSettingsDialog(@NotNull Facet<?> facet, @Nullable String tabNameToSelect) {
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

  public static boolean showDialog(@NotNull Project project, @Nullable String moduleToSelect, @Nullable String editorNameToSelect) {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(project);
    return ShowSettingsUtil.getInstance().editConfigurable(project, config, () -> config.select(moduleToSelect, editorNameToSelect, true));
  }

  public void moduleRenamed(@NotNull Module module, String oldName, @NotNull String name) {
    ModuleEditor moduleEditor = myModuleEditors.get(module);
    if (moduleEditor != null) {
      moduleEditor.setModuleName(name);
      moduleEditor.updateCompilerOutputPathChanged(myProjectStructureConfigurable.getProjectConfig().getCompilerOutputUrl(), name);
      myContext.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(myContext, module));
    }
  }

  public StructureConfigurableContext getContext() {
    return myContext;
  }

  @ApiStatus.Internal
  public interface NewProjectWizardFactory {
    @NotNull NewProjectWizard create(@Nullable Project project, @NotNull ModulesProvider modulesProvider, @Nullable String defaultPath);
  }

  static final class NewProjectWizardFactoryImpl implements NewProjectWizardFactory {
    @Override
    public @NotNull NewProjectWizard create(@Nullable Project project,
                                            @NotNull ModulesProvider modulesProvider,
                                            @Nullable String defaultPath) {
      return new NewProjectWizard(project, modulesProvider, defaultPath);
    }
  }
}
