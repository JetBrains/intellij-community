// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public abstract class ModuleBuilder extends AbstractModuleBuilder {

  public static final ExtensionPointName<ModuleBuilderFactory> EP_NAME = ExtensionPointName.create("com.intellij.moduleBuilder");

  private static final Logger LOG = Logger.getInstance(ModuleBuilder.class);
  private final Set<ModuleConfigurationUpdater> myUpdaters = new HashSet<>();
  private final EventDispatcher<ModuleBuilderListener> myDispatcher = EventDispatcher.create(ModuleBuilderListener.class);
  protected Sdk myJdk;
  private String myName;
  @NonNls private String myModuleFilePath;
  private String myContentEntryPath;

  @NotNull
  public List<Class<? extends ModuleWizardStep>> getIgnoredSteps() {
    return Collections.emptyList();
  }

  @NotNull
  public static List<ModuleBuilder> getAllBuilders() {
    final ArrayList<ModuleBuilder> result = new ArrayList<>();
    for (final ModuleType<?> moduleType : ModuleTypeManager.getInstance().getRegisteredTypes()) {
      result.add(moduleType.createModuleBuilder());
    }
    for (ModuleBuilderFactory factory : EP_NAME.getExtensions()) {
      result.add(factory.createBuilder());
    }
    return ContainerUtil.filter(result, moduleBuilder -> moduleBuilder.isAvailable());
  }

  public static void deleteModuleFile(String moduleFilePath) {
    final File moduleFile = new File(moduleFilePath);
    if (moduleFile.exists()) {
      FileUtil.delete(moduleFile);
    }
    final VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(moduleFile);
    if (file != null) {
      file.refresh(false, false);
    }
  }

  protected boolean isAvailable() {
    return true;
  }

  @Nullable
  protected static String acceptParameter(String param) {
    return param != null && param.length() > 0 ? param : null;
  }

  public String getName() {
    return myName;
  }

  @Override
  public void setName(String name) {
    myName = acceptParameter(name);
  }

  @Override
  @Nullable
  public String getBuilderId() {
    ModuleType<?> moduleType = getModuleType();
    return moduleType == null ? null : moduleType.getId();
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    ModuleType moduleType = getModuleType();
    return moduleType == null ? ModuleWizardStep.EMPTY_ARRAY : moduleType.createWizardSteps(wizardContext, this, modulesProvider);
  }

  /**
   * Typically delegates to ModuleType (e.g. JavaModuleType) that is more generic than ModuleBuilder
   *
   * @param settingsStep step to be modified
   * @return callback ({@link ModuleWizardStep#validate()}
   *         and {@link ModuleWizardStep#updateDataModel()}
   *         will be invoked)
   */
  @Override
  @Nullable
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    return modifyStep(settingsStep);
  }

  public ModuleWizardStep modifyStep(SettingsStep settingsStep) {
    ModuleType<?> type = getModuleType();
    if (type == null) {
      return null;
    }
    else {
      final ModuleWizardStep step = type.modifySettingsStep(settingsStep, this);
      final List<WizardInputField<?>> fields = getAdditionalFields();
      for (WizardInputField<?> field : fields) {
        field.addToSettings(settingsStep);
      }
      return new ModuleWizardStep() {
        @Override
        public JComponent getComponent() {
          return null;
        }

        @Override
        public void updateDataModel() {
          if (step != null) {
            step.updateDataModel();
          }
        }

        @Override
        public boolean validate() throws ConfigurationException {
          for (WizardInputField<?> field : fields) {
            if (!field.validate()) {
              return false;
            }
          }
          return step == null || step.validate();
        }
      };
    }
  }

  @Override
  public ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep settingsStep) {
    ModuleType<?> type = getModuleType();
    return type == null ? null : type.modifyProjectTypeStep(settingsStep, this);
  }

  @NotNull
  protected List<WizardInputField<?>> getAdditionalFields() {
    return Collections.emptyList();
  }

  public String getModuleFilePath() {
    return myModuleFilePath;
  }

  @Override
  public void setModuleFilePath(@NonNls String path) {
    myModuleFilePath = acceptParameter(path);
  }

  public void addModuleConfigurationUpdater(ModuleConfigurationUpdater updater) {
    myUpdaters.add(updater);
  }

  @Nullable
  public String getContentEntryPath() {
    if (myContentEntryPath == null) {
      final String directory = getModuleFileDirectory();
      if (directory == null) {
        return null;
      }
      new File(directory).mkdirs();
      return directory;
    }
    return myContentEntryPath;
  }

  @Override
  public void setContentEntryPath(String moduleRootPath) {
    final String path = acceptParameter(moduleRootPath);
    if (path != null) {
      try {
        myContentEntryPath = FileUtil.resolveShortWindowsName(path);
      }
      catch (IOException e) {
        myContentEntryPath = path;
      }
    }
    else {
      myContentEntryPath = null;
    }
    if (myContentEntryPath != null) {
      myContentEntryPath = myContentEntryPath.replace(File.separatorChar, '/');
    }
  }

  protected @Nullable ContentEntry doAddContentEntry(ModifiableRootModel modifiableRootModel) {
    final String contentEntryPath = getContentEntryPath();
    if (contentEntryPath == null) return null;
    new File(contentEntryPath).mkdirs();
    final VirtualFile moduleContentRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(contentEntryPath.replace('\\', '/'));
    if (moduleContentRoot == null) return null;
    return modifiableRootModel.addContentEntry(moduleContentRoot);
  }

  @Nullable
  public String getModuleFileDirectory() {
    if (myModuleFilePath == null) {
      return null;
    }
    final String parent = new File(myModuleFilePath).getParent();
    if (parent == null) {
      return null;
    }
    return parent.replace(File.separatorChar, '/');
  }

  @NotNull
  public Module createModule(@NotNull ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
    LOG.assertTrue(myName != null);
    LOG.assertTrue(myModuleFilePath != null);

    deleteModuleFile(myModuleFilePath);
    final ModuleType moduleType = getModuleType();
    final Module module = moduleModel.newModule(myModuleFilePath, moduleType.getId());
    setupModule(module);

    return module;
  }

  protected void setupModule(Module module) throws ConfigurationException {
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    setupRootModel(modifiableModel);
    for (ModuleConfigurationUpdater updater : myUpdaters) {
      updater.update(module, modifiableModel);
    }
    modifiableModel.commit();
    setProjectType(module);
  }

  private void onModuleInitialized(final Module module) {
    myDispatcher.getMulticaster().moduleCreated(module);
  }

  public void setupRootModel(@NotNull ModifiableRootModel modifiableRootModel) throws ConfigurationException {
  }

  public abstract ModuleType<?> getModuleType();

  protected ProjectType getProjectType() {
    return null;
  }

  protected void setProjectType(Module module) {
    ProjectType projectType = getProjectType();
    if (projectType != null && ProjectTypeService.getProjectType(module.getProject()) == null) {
      ProjectTypeService.setProjectType(module.getProject(), projectType);
    }
  }

  @NotNull
  public Module createAndCommitIfNeeded(@NotNull Project project, @Nullable ModifiableModuleModel model, boolean runFromProjectWizard)
    throws InvalidDataException, ConfigurationException, IOException, JDOMException, ModuleWithNameAlreadyExists {
    final ModifiableModuleModel moduleModel = model != null ? model : ModuleManager.getInstance(project).getModifiableModel();
    final Module module = createModule(moduleModel);
    if (model == null) moduleModel.commit();

    if (runFromProjectWizard) {
      StartupManager.getInstance(module.getProject()).runAfterOpened(() -> {
        GuiUtils.invokeLaterIfNeeded(() -> {
          ApplicationManager.getApplication().runWriteAction(() -> onModuleInitialized(module));
        }, ModalityState.NON_MODAL, module.getDisposed());
      });
    }
    else {
      onModuleInitialized(module);
    }
    return module;
  }

  public void addListener(ModuleBuilderListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeListener(ModuleBuilderListener listener) {
    myDispatcher.removeListener(listener);
  }

  public boolean canCreateModule() {
    return true;
  }

  @Override
  @Nullable
  public List<Module> commit(@NotNull final Project project, final ModifiableModuleModel model, final ModulesProvider modulesProvider) {
    final Module module = commitModule(project, model);
    return module != null ? Collections.singletonList(module) : null;
  }

  @Nullable
  public Module commitModule(@NotNull final Project project, @Nullable final ModifiableModuleModel model) {
    if (canCreateModule()) {
      if (myName == null) {
        myName = project.getName();
      }
      if (myModuleFilePath == null) {
        myModuleFilePath = project.getBasePath() + File.separator + myName + ModuleFileType.DOT_DEFAULT_EXTENSION;
      }
      try {
        return ApplicationManager.getApplication().runWriteAction(
          (ThrowableComputable<Module, Exception>)() -> createAndCommitIfNeeded(project, model, true));
      }
      catch (Exception ex) {
        LOG.warn(ex);
        Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", ex.getMessage()), IdeBundle.message("title.add.module"));
      }
    }
    return null;
  }

  @Override
  public Icon getNodeIcon() {
    return getModuleType().getNodeIcon(false);
  }

  @NlsContexts.DetailedDescription
  public String getDescription() {
    return getModuleType().getDescription();
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  public String getPresentableName() {
    return getModuleTypeName();
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  protected String getModuleTypeName() {
    String name = getModuleType().getName();
    return StringUtil.trimEnd(name, " Module");
  }

  public String getGroupName() {
    return getPresentableName().split(" ")[0];
  }

  public String getParentGroup() {
    return null;
  }

  public int getWeight() { return 0; }

  public boolean isTemplate() {
    return false;
  }

  public boolean isTemplateBased() {
    return false;
  }

  public void updateFrom(ModuleBuilder from) {
    myName = from.getName();
    myContentEntryPath = from.getContentEntryPath();
    myModuleFilePath = from.getModuleFilePath();
  }

  public Sdk getModuleJdk() {
    return myJdk;
  }

  public void setModuleJdk(Sdk jdk) {
    myJdk = jdk;
  }

  @NotNull
  public FrameworkRole getDefaultAcceptableRole() {
    return getModuleType().getDefaultAcceptableRole();
  }

  public static abstract class ModuleConfigurationUpdater {

    public abstract void update(@NotNull Module module, @NotNull ModifiableRootModel rootModel);

  }
}
