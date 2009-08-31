package com.intellij.openapi.roots.ui.configuration;

import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetModel;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.ui.FacetEditorImpl;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
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
  private final Project myProject;
  //private final ModuleStructureConfigurable myProjectRootConfigurable;

  private boolean myModified = false;

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
  private ModifiableModuleModel myModuleModel;
  private ProjectFacetsConfigurator myFacetsConfigurator;

  private StructureConfigurableContext myContext;

  public ModulesConfigurator(Project project, ProjectJdksModel projectJdksModel) {
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
          final ModifiableRootModel model = moduleEditor.dispose();
          if (model != null) {
            model.dispose();
          }
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

  public ModuleRootModel getRootModel(Module module) {
    final ModuleEditor editor = getModuleEditor(module);
    ModuleRootModel rootModel = null;
    if (editor != null) {
      rootModel = editor.getModifiableRootModel();
    }
    if (rootModel == null && getModule(module.getName()) != null) {
      createModuleEditor(module);
      rootModel = getModuleEditor(module).getModifiableRootModel();
    }

    return rootModel;
  }

  public FacetModel getFacetModel(Module module) {
    return myFacetsConfigurator.getOrCreateModifiableModel(module);
  }

  public void resetModuleEditors() {
    myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (final ModuleEditor moduleEditor : myModuleEditors) {
          moduleEditor.removeChangeListener(ModulesConfigurator.this);
        }
        myModuleEditors.clear();
        final Module[] modules = myModuleModel.getModules();
        if (modules.length > 0) {
          for (Module module : modules) {
            createModuleEditor(module);
          }
          Collections.sort(myModuleEditors, myModuleEditorComparator);
        }
      }
    });
    myFacetsConfigurator.resetEditors();
    myModified = false;
  }

  public void createModuleEditor(final Module module) {
    final ModuleEditor moduleEditor = new ModuleEditor(myProject, this, myFacetsConfigurator, module);
    myModuleEditors.add(moduleEditor);
    moduleEditor.addChangeListener(this);
  }

  public void moduleStateChanged(final ModifiableRootModel moduleRootModel) {
    myProjectConfigurable.updateCircularDependencyWarning();
  }

  public GraphGenerator<ModifiableRootModel> createGraphGenerator() {
    final Map<Module, ModifiableRootModel> models = new HashMap<Module, ModifiableRootModel>();
    for (ModuleEditor moduleEditor : myModuleEditors) {
      models.put(moduleEditor.getModule(), moduleEditor.getModifiableRootModel());
    }
    return ModuleCompilerUtil.createGraphGenerator(models);
  }

  public void apply() throws ConfigurationException {
    final ProjectRootManagerImpl projectRootManager = ProjectRootManagerImpl.getInstanceImpl(myProject);

    final ConfigurationException[] ex = new ConfigurationException[1];

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          myFacetsConfigurator.applyEditors();
          final List<ModifiableRootModel> models = new ArrayList<ModifiableRootModel>(myModuleEditors.size());
          for (final ModuleEditor moduleEditor : myModuleEditors) {
            final ModifiableRootModel model = moduleEditor.applyAndDispose();
            if (model != null) {
              models.add(model);
            }
          }

          final ModifiableRootModel[] rootModels = models.toArray(new ModifiableRootModel[models.size()]);
          projectRootManager.multiCommit(myModuleModel, rootModels);
          myFacetsConfigurator.commitFacets();

        }
        catch (ConfigurationException e) {
          ex[0] = e;
        }
        finally {
          myFacetsConfigurator.disposeEditors();
          ModuleStructureConfigurable.getInstance(myProject).getFacetEditorFacade().clearMaps();
          myFacetsConfigurator = createFacetsConfigurator();
          myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();
        }
        ApplicationManager.getApplication().saveAll();
      }
    });

    if (ex[0] != null) {
      throw ex[0];
    }

    ApplicationManager.getApplication().saveAll();

    myModified = false;
  }

  private ProjectFacetsConfigurator createFacetsConfigurator() {
    return new ProjectFacetsConfigurator(myContext, myProject);
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
             createModuleEditor(module);
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
          createModuleEditor(module);
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
    final ModifiableRootModel model = selectedEditor.dispose();
    ModuleDeleteProvider.removeModule(moduleToRemove, model, modifiableRootModels, myModuleModel);
    processModuleCountChanged();
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
        myContext.invalidateModuleName(moduleEditor.getModule());
        return;
      }
    }
  }

}
