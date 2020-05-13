// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.configurationStore.Scheme_implKt;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.LanguageRuntimeType;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentsConfigurable;
import com.intellij.execution.target.TargetEnvironmentsManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorListener;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class SingleConfigurationConfigurable<Config extends RunConfiguration> extends BaseRCSettingsConfigurable {
  private static final LayeredIcon GEAR_WITH_DROPDOWN_ICON = new LayeredIcon(AllIcons.General.GearPlain, AllIcons.General.Dropdown);
  private static final LayeredIcon GEAR_WITH_DROPDOWN_DISABLED_ICON =
    new LayeredIcon(IconLoader.getDisabledIcon(AllIcons.General.GearPlain), IconLoader.getDisabledIcon(AllIcons.General.Dropdown));
  private static final LayeredIcon GEAR_WITH_DROPDOWN_ERROR_ICON = new LayeredIcon(AllIcons.General.Error, AllIcons.General.Dropdown);

  private enum RCStorageType {Workspace, DotIdeaFolder, ArbitraryFileInProject}

  public static final DataKey<String> RUN_ON_TARGET_NAME_KEY = DataKey.create("RunOnTargetName");

  private static final Logger LOG = Logger.getInstance(SingleConfigurationConfigurable.class);

  private final PlainDocument myNameDocument = new PlainDocument();

  @NotNull private final Project myProject;
  @Nullable private final Executor myExecutor;

  private ValidationResult myLastValidationResult = null;
  private boolean myValidationResultValid = false;
  private MyValidatableComponent myComponent;
  private final String myDisplayName;
  private final String myHelpTopic;
  private final boolean myBrokenConfiguration;
  private RCStorageType myRCStorageType;
  @Nullable @SystemIndependent @NonNls private String myFolderPathIfStoredInArbitraryFile;
  private boolean myIsAllowRunningInParallel = false;
  private String myDefaultTargetName;
  private String myFolderName;
  private boolean myChangingNameFromCode;

  @Nullable Boolean myDotIdeaStorageVcsIgnored = null; // used as cache; null means not initialized yet

  private SingleConfigurationConfigurable(@NotNull RunnerAndConfigurationSettings settings, @Nullable Executor executor) {
    super(ConfigurationSettingsEditorWrapper.createWrapper(settings), settings);

    myProject = settings.getConfiguration().getProject();
    myExecutor = executor;

    final Config configuration = getConfiguration();
    myDisplayName = getSettings().getName();
    myHelpTopic = configuration.getType().getHelpTopic();

    myBrokenConfiguration = !configuration.getType().isManaged();
    setFolderName(getSettings().getFolderName());

    setNameText(configuration.getName());
    myNameDocument.addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(@NotNull DocumentEvent event) {
        setModified(true);
        if (!myChangingNameFromCode) {
          RunConfiguration runConfiguration = getSettings().getConfiguration();
          if (runConfiguration instanceof LocatableConfigurationBase) {
            ((LocatableConfigurationBase)runConfiguration).setNameChangedByUser(true);
          }
        }
      }
    });

    getEditor().addSettingsEditorListener(new SettingsEditorListener<RunnerAndConfigurationSettings>() {
      @Override
      public void stateChanged(@NotNull SettingsEditor<RunnerAndConfigurationSettings> settingsEditor) {
        myValidationResultValid = false;
      }
    });
  }

  @NotNull
  public static <Config extends RunConfiguration> SingleConfigurationConfigurable<Config> editSettings(@NotNull RunnerAndConfigurationSettings settings,
                                                                                                       @Nullable Executor executor) {
    SingleConfigurationConfigurable<Config> configurable = new SingleConfigurationConfigurable<>(settings, executor);
    configurable.reset();
    return configurable;
  }

  @Override
  boolean isSpecificallyModified() {
    return isStorageModified();
  }

  private boolean isStorageModified() {
    RunnerAndConfigurationSettings original = getSettings();
    switch (myRCStorageType) {
      case Workspace:
        return !original.isStoredInLocalWorkspace();
      case DotIdeaFolder:
        return !original.isStoredInDotIdeaFolder();
      case ArbitraryFileInProject:
        return !original.isStoredInArbitraryFileInProject() ||
               !PathUtil.getParentPath(StringUtil.notNullize(original.getPathIfStoredInArbitraryFileInProject()))
                 .equals(myFolderPathIfStoredInArbitraryFile);
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    RunnerAndConfigurationSettings settings = getSettings();

    RunConfiguration runConfiguration = settings.getConfiguration();
    settings.setName(getNameText());
    runConfiguration.setAllowRunningInParallel(myIsAllowRunningInParallel);
    if (runConfiguration instanceof TargetEnvironmentAwareRunProfile) {
      ((TargetEnvironmentAwareRunProfile)runConfiguration).setDefaultTargetName(myDefaultTargetName);
    }
    settings.setFolderName(myFolderName);

    if (isStorageModified()) {
      switch (myRCStorageType) {
        case Workspace:
          settings.storeInLocalWorkspace();
          break;
        case DotIdeaFolder:
          settings.storeInDotIdeaFolder();
          break;
        case ArbitraryFileInProject:
          if (getErrorIfBadFolderPathForStoringInArbitraryFile(myProject, myFolderPathIfStoredInArbitraryFile) != null) {
            // don't apply incorrect UI to the model
          }
          else {
            String fileName = getFileNameByRCName(settings.getName());
            settings.storeInArbitraryFileInProject(myFolderPathIfStoredInArbitraryFile + "/" + fileName);
          }
          break;
        default:
          throw new IllegalStateException("Unexpected value: " + myRCStorageType);
      }
    }

    super.apply();
    RunManagerImpl.getInstanceImpl(myProject).addConfiguration(settings);
  }

  @Override
  public void reset() {
    RunnerAndConfigurationSettings configuration = getSettings();
    if (configuration instanceof RunnerAndConfigurationSettingsImpl) {
      configuration = ((RunnerAndConfigurationSettingsImpl)configuration).clone();
    }
    setNameText(configuration.getName());
    super.reset();
    if (myComponent == null) {
      myComponent = new MyValidatableComponent();
    }
    myComponent.doReset(configuration);
  }

  @NonNls
  @NotNull
  private static String getFileNameByRCName(@NotNull String rcName) {
    return Scheme_implKt.getMODERN_NAME_CONVERTER().invoke(rcName) + ".run.xml";
  }

  @Nullable
  @Contract("_,null -> !null")
  private static String getErrorIfBadFolderPathForStoringInArbitraryFile(@NotNull Project project,
                                                                         @Nullable @NonNls @SystemIndependent String path) {
    if (getDotIdeaStoragePath(project).equals(path)) return null; // that's ok

    if (StringUtil.isEmpty(path)) return ExecutionBundle.message("run.configuration.storage.folder.path.not.specified");
    if (path.endsWith("/.idea") || path.contains("/.idea/")) {
      return ExecutionBundle.message("run.configuration.storage.folder.dot.idea.forbidden", File.separator);
    }

    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file != null && !file.isDirectory()) return ExecutionBundle.message("run.configuration.storage.folder.path.expected");

    String folderName = PathUtil.getFileName(path);
    String parentPath = PathUtil.getParentPath(path);
    while (file == null && !parentPath.isEmpty()) {
      if (!PathUtil.isValidFileName(folderName)) {
        return ExecutionBundle.message("run.configuration.storage.folder.path.expected");
      }
      file = LocalFileSystem.getInstance().findFileByPath(parentPath);
      folderName = PathUtil.getFileName(parentPath);
      parentPath = PathUtil.getParentPath(parentPath);
    }

    if (file == null) return ExecutionBundle.message("run.configuration.storage.folder.not.within.project");
    if (!file.isDirectory()) return ExecutionBundle.message("run.configuration.storage.folder.path.expected");

    if (ProjectFileIndex.getInstance(project).getContentRootForFile(file, true) == null) {
      if (ProjectFileIndex.getInstance(project).getContentRootForFile(file, false) == null) {
        return ExecutionBundle.message("run.configuration.storage.folder.not.within.project");
      }
      else {
        return ExecutionBundle.message("run.configuration.storage.folder.in.excluded.root");
      }
    }

    return null; // ok
  }

  void updateWarning() {
    myValidationResultValid = false;
    if (myComponent != null) {
      myComponent.updateWarning();
    }
  }

  @Override
  public final JComponent createComponent() {
    myComponent.myNameText.setEnabled(!myBrokenConfiguration);
    JComponent result = myComponent.getWholePanel();
    DataManager.registerDataProvider(result, dataId -> {
      if (ConfigurationSettingsEditorWrapper.CONFIGURATION_EDITOR_KEY.is(dataId)) {
        return getEditor();
      }
      if (RUN_ON_TARGET_NAME_KEY.is(dataId)) {
        RunOnTargetComboBox runOnComboBox = (RunOnTargetComboBox)myComponent.myRunOnComboBox;
        if (runOnComboBox != null) {
          return runOnComboBox.getSelectedTargetName();
        }
      }
      return null;
    });
    return result;
  }

  final JComponent getValidationComponent() {
    return myComponent.myValidationPanel;
  }

  public boolean isStoredInFile() {
    return myRCStorageType == RCStorageType.DotIdeaFolder || myRCStorageType == RCStorageType.ArbitraryFileInProject;
  }

  @Nullable
  private ValidationResult getValidationResult() {
    if (!myValidationResultValid) {
      myLastValidationResult = null;
      RunnerAndConfigurationSettings snapshot = null;
      try {
        snapshot = createSnapshot(false);
        snapshot.setName(getNameText());
        snapshot.checkSettings(myExecutor);
        for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
          ProgramRunner<?> runner = ProgramRunner.getRunner(executor.getId(), snapshot.getConfiguration());
          if (runner != null) {
            checkConfiguration(runner, snapshot);
          }
        }
      }
      catch (ConfigurationException e) {
        myLastValidationResult = createValidationResult(snapshot, e);
      }

      myValidationResultValid = true;
    }
    return myLastValidationResult;
  }

  private ValidationResult createValidationResult(RunnerAndConfigurationSettings snapshot, ConfigurationException e) {
    if (!e.shouldShowInDumbMode() && DumbService.isDumb(myProject)) return null;

    return new ValidationResult(
      e.getLocalizedMessage(),
      e instanceof RuntimeConfigurationException ? e.getTitle() : ExecutionBundle.message("invalid.data.dialog.title"),
      getQuickFix(snapshot, e));
  }

  @Nullable
  private Runnable getQuickFix(RunnerAndConfigurationSettings snapshot, ConfigurationException exception) {
    Runnable quickFix = exception.getQuickFix();
    if (quickFix != null && snapshot != null) {
      return () -> {
        quickFix.run();
        getEditor().resetFrom(snapshot);
      };
    }
    return quickFix;
  }

  private static void checkConfiguration(@NotNull ProgramRunner<?> runner, @NotNull RunnerAndConfigurationSettings snapshot)
    throws RuntimeConfigurationException {
    RunnerSettings runnerSettings = snapshot.getRunnerSettings(runner);
    ConfigurationPerRunnerSettings configurationSettings = snapshot.getConfigurationSettings(runner);
    try {
      runner.checkConfiguration(runnerSettings, configurationSettings);
    }
    catch (AbstractMethodError e) {
      //backward compatibility
    }
  }

  @Override
  public final void disposeUIResources() {
    super.disposeUIResources();
    myComponent = null;
  }

  public final String getNameText() {
    try {
      return myNameDocument.getText(0, myNameDocument.getLength());
    }
    catch (BadLocationException e) {
      LOG.error(e);
      return "";
    }
  }

  public final void addNameListener(DocumentListener listener) {
    myNameDocument.addDocumentListener(listener);
  }

  public final void addSharedListener(ActionListener listener) {
    myComponent.myStoreAsFileCheckBox.addActionListener(listener);
  }

  public final void setNameText(final String name) {
    myChangingNameFromCode = true;
    try {
      try {
        if (!myNameDocument.getText(0, myNameDocument.getLength()).equals(name)) {
          myNameDocument.replace(0, myNameDocument.getLength(), name, null);
        }
      }
      catch (BadLocationException e) {
        LOG.error(e);
      }
    }
    finally {
      myChangingNameFromCode = false;
    }
  }

  public final boolean isValid() {
    return getValidationResult() == null;
  }

  public final JTextField getNameTextField() {
    return myComponent.myNameText;
  }

  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public String getHelpTopic() {
    return myHelpTopic;
  }

  @NotNull
  public Config getConfiguration() {
    //noinspection unchecked
    return (Config)getSettings().getConfiguration();
  }

  @NotNull
  public RunnerAndConfigurationSettings createSnapshot(boolean cloneBeforeRunTasks) throws ConfigurationException {
    RunnerAndConfigurationSettings snapshot = getEditor().getSnapshot();
    RunConfiguration runConfiguration = snapshot.getConfiguration();
    runConfiguration.setAllowRunningInParallel(myIsAllowRunningInParallel);
    if (runConfiguration instanceof TargetEnvironmentAwareRunProfile) {
      ((TargetEnvironmentAwareRunProfile)runConfiguration).setDefaultTargetName(myDefaultTargetName);
    }
    if (cloneBeforeRunTasks) {
      RunManagerImplKt.cloneBeforeRunTasks(runConfiguration);
    }
    return snapshot;
  }

  @Override
  public String toString() {
    return myDisplayName;
  }

  public void setFolderName(@Nullable String folderName) {
    if (!Objects.equals(myFolderName, folderName)) {
      myFolderName = folderName;
      setModified(true);
    }
  }

  @Nullable
  public String getFolderName() {
    return myFolderName;
  }

  /**
   * @return full path to .idea/runConfigurations folder (for directory-based projects) or full path to the project.ipr file (for file-based projects)
   */
  @NonNls
  @NotNull
  private static String getDotIdeaStoragePath(@NotNull Project project) {
    // notNullize is to make inspections happy. Paths can't be null for non-default project
    return ProjectKt.isDirectoryBased(project)
           ? RunManagerImpl.getInstanceImpl(project).getDotIdeaRunConfigurationsPath$intellij_platform_execution_impl()
           : StringUtil.notNullize(project.getProjectFilePath());
  }

  private void setStorageTypeAndPathToTheBestPossibleState() {
    // all that tricky logic, see the flowchart from the issue description https://youtrack.jetbrains.com/issue/UX-1126

    // 1. If this RC had been shared before Run Configurations dialog was opened - use the state that was used before.
    // This handles the case when user opens shared RC for editing and clicks the 'Save to file' check box two times.
    RunnerAndConfigurationSettings settings = getSettings();
    if (settings.isStoredInDotIdeaFolder()) {
      myRCStorageType = RCStorageType.DotIdeaFolder;
      myFolderPathIfStoredInArbitraryFile = null;
      return;
    }

    if (settings.isStoredInArbitraryFileInProject()) {
      myRCStorageType = RCStorageType.ArbitraryFileInProject;
      myFolderPathIfStoredInArbitraryFile =
        PathUtil.getParentPath(StringUtil.notNullize(settings.getPathIfStoredInArbitraryFileInProject()));
      return;
    }

    // 2. For IPR-based projects keep using project.ipr file to store RCs by default
    if (!ProjectKt.isDirectoryBased(myProject)) {
      myRCStorageType = RCStorageType.DotIdeaFolder;
      myFolderPathIfStoredInArbitraryFile = null;
      return;
    }


    // 3. If the project is not under VCS, keep using .idea/runConfigurations
    RunConfigurationVcsSupport vcsSupport = myProject.getService(RunConfigurationVcsSupport.class);
    if (!vcsSupport.hasActiveVcss(myProject)) {
      myRCStorageType = RCStorageType.DotIdeaFolder;
      myFolderPathIfStoredInArbitraryFile = null;
      return;
    }

    // 4. If .idea/runConfigurations is not excluded from VCS (e.g. not in .gitignore), then use it
    if (!isDotIdeaStorageVcsIgnored(vcsSupport)) {
      myRCStorageType = RCStorageType.DotIdeaFolder;
      myFolderPathIfStoredInArbitraryFile = null;
      return;
    }

    // notNullize is to make inspections happy. Paths can't be null for non-default project
    VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(StringUtil.notNullize(myProject.getBasePath()));
    LOG.assertTrue(baseDir != null);

    // 5. If project base dir is not within project content, use .idea/runConfigurations
    if (!ProjectFileIndex.getInstance(myProject).isInContent(baseDir)) {
      myRCStorageType = RCStorageType.DotIdeaFolder;
      myFolderPathIfStoredInArbitraryFile = null;
      return;
    }

    // 6. If there are other RCs stored in arbitrary files (and all in the same folder) - suggest that folder
    Collection<String> otherFolders = getFolderPathsWithinProjectWhereRunConfigurationsStored(myProject);
    if (otherFolders.size() == 1) {
      myRCStorageType = RCStorageType.ArbitraryFileInProject;
      myFolderPathIfStoredInArbitraryFile = otherFolders.iterator().next();
      return;
    }

    // default is .../project_base_dir/.run/ folder
    myRCStorageType = RCStorageType.ArbitraryFileInProject;
    myFolderPathIfStoredInArbitraryFile = baseDir.getPath() + "/.run";
  }

  private boolean isDotIdeaStorageVcsIgnored(RunConfigurationVcsSupport vcsSupport) {
    if (myDotIdeaStorageVcsIgnored == null) {
      myDotIdeaStorageVcsIgnored = vcsSupport.isDirectoryVcsIgnored(myProject, getDotIdeaStoragePath(myProject));
    }
    return myDotIdeaStorageVcsIgnored.booleanValue();
  }

  private static Collection<String> getFolderPathsWithinProjectWhereRunConfigurationsStored(@NotNull Project project) {
    Set<String> result = new THashSet<>();
    for (RunnerAndConfigurationSettings settings : RunManager.getInstance(project).getAllSettings()) {
      String filePath = settings.getPathIfStoredInArbitraryFileInProject();
      // two conditions on the next line are effectively equivalent, this is to make inspections happy
      if (settings.isStoredInArbitraryFileInProject() && filePath != null) {
        result.add(PathUtil.getParentPath(filePath));
      }
    }
    return result;
  }

  private class MyValidatableComponent {
    private JLabel myNameLabel;
    private JTextField myNameText;
    private JComponent myWholePanel;
    private JPanel myComponentPlace;
    private JBLabel myWarningLabel;
    private JButton myFixButton;
    private JSeparator mySeparator;
    private JBCheckBox myIsAllowRunningInParallelCheckBox;

    private JBCheckBox myStoreAsFileCheckBox;
    private ActionButton myStoreAsFileGearButton;

    private JPanel myValidationPanel;
    private JBScrollPane myJBScrollPane;

    private ComboBox myRunOnComboBox;
    private JLabel myManageTargetsLabel;
    private JPanel myRunOnPanel;
    private JPanel myRunOnPanelInner;

    private Runnable myQuickFix = null;
    private boolean myWindowResizedOnce = false;

    MyValidatableComponent() {
      myNameLabel.setLabelFor(myNameText);
      myNameText.setDocument(myNameDocument);

      getEditor().addSettingsEditorListener(settingsEditor -> updateWarning());
      myWarningLabel.setCopyable(true);
      myWarningLabel.setAllowAutoWrapping(true);
      myWarningLabel.setIcon(AllIcons.General.BalloonError);

      myComponentPlace.setLayout(new GridBagLayout());
      myComponentPlace.add(getEditorComponent(),
                           new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                  JBUI.emptyInsets(), 0, 0));
      myComponentPlace.doLayout();
      myFixButton.setIcon(AllIcons.Actions.QuickfixBulb);
      updateWarning();
      myFixButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          if (myQuickFix == null) {
            return;
          }
          myQuickFix.run();
          myValidationResultValid = false;
          updateWarning();
        }
      });

      myIsAllowRunningInParallelCheckBox.addActionListener(e -> {
        setModified(true);
        myIsAllowRunningInParallel = myIsAllowRunningInParallelCheckBox.isSelected();
      });

      myStoreAsFileCheckBox.addActionListener(e -> {
        if (myStoreAsFileCheckBox.isSelected()) {
          setStorageTypeAndPathToTheBestPossibleState();
        }
        else {
          myRCStorageType = RCStorageType.Workspace;
          myFolderPathIfStoredInArbitraryFile = null;
        }

        setModified(true);

        myStoreAsFileGearButton.setEnabled(myStoreAsFileCheckBox.isSelected());
        if (myStoreAsFileCheckBox.isSelected()) {
          manageStorageFileLocation();
        }
      });

      myRunOnPanel.setBorder(JBUI.Borders.emptyLeft(5));
      UI.PanelFactory.panel(myRunOnPanelInner)
        .withLabel(ExecutionBundle.message("run.on"))
        .withComment(ExecutionBundle.message("edit.run.configuration.run.configuration.run.on.comment"))
        .addToPanel(myRunOnPanel, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0,
                                                         GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                         JBUI.emptyInsets(), 0, 0), false);
      JLabel runOnLabel = UIUtil.findComponentOfType(myRunOnPanel, JLabel.class);
      if (runOnLabel != null) {
        runOnLabel.setLabelFor(myRunOnComboBox);
        Dimension nameSize = myNameLabel.getPreferredSize();
        Dimension runOnSize = runOnLabel.getPreferredSize();
        double width = Math.max(nameSize.getWidth(), runOnSize.getWidth());
        myNameLabel.setPreferredSize(new Dimension((int)width, (int)nameSize.getHeight()));
        runOnLabel.setPreferredSize(new Dimension((int)width, (int)runOnSize.getHeight()));
      }

      myRunOnComboBox.addActionListener(e -> {
        String chosenTarget = ((RunOnTargetComboBox)myRunOnComboBox).getSelectedTargetName();
        if (!StringUtil.equals(myDefaultTargetName, chosenTarget)) {
          setModified(true);
          myDefaultTargetName = chosenTarget;
        }
      });
    }

    private void doReset(RunnerAndConfigurationSettings settings) {
      RunConfiguration configuration = settings.getConfiguration();
      boolean isManagedRunConfiguration = configuration.getType().isManaged();
      myRCStorageType = settings.isStoredInArbitraryFileInProject()
                        ? RCStorageType.ArbitraryFileInProject
                        : settings.isStoredInDotIdeaFolder()
                          ? RCStorageType.DotIdeaFolder
                          : RCStorageType.Workspace;
      myFolderPathIfStoredInArbitraryFile =
        PathUtil.getParentPath(StringUtil.notNullize(settings.getPathIfStoredInArbitraryFileInProject()));

      myStoreAsFileCheckBox.setVisible(!settings.isTemplate());
      myStoreAsFileCheckBox.setEnabled(isManagedRunConfiguration);
      myStoreAsFileCheckBox.setSelected(myRCStorageType == RCStorageType.DotIdeaFolder ||
                                        myRCStorageType == RCStorageType.ArbitraryFileInProject);
      myStoreAsFileGearButton.setVisible(!settings.isTemplate() && isManagedRunConfiguration);
      myStoreAsFileGearButton.setEnabled(myStoreAsFileCheckBox.isSelected());

      boolean targetAware =
        configuration instanceof TargetEnvironmentAwareRunProfile && Experiments.getInstance().isFeatureEnabled("run.targets");
      myRunOnPanel.setVisible(targetAware);
      if (targetAware) {
        String defaultTargetName = ((TargetEnvironmentAwareRunProfile)configuration).getDefaultTargetName();
        LanguageRuntimeType<?> defaultRuntime = ((TargetEnvironmentAwareRunProfile)configuration).getDefaultLanguageRuntimeType();
        ((RunOnTargetComboBox)myRunOnComboBox).setDefaultLanguageRuntimeTime(defaultRuntime);
        resetRunOnComboBox(defaultTargetName);
        myDefaultTargetName = defaultTargetName;
      }

      myIsAllowRunningInParallel = configuration.isAllowRunningInParallel();
      myIsAllowRunningInParallelCheckBox.setEnabled(isManagedRunConfiguration);
      myIsAllowRunningInParallelCheckBox.setSelected(myIsAllowRunningInParallel);
      myIsAllowRunningInParallelCheckBox.setVisible(getEditor() instanceof ConfigurationSettingsEditorWrapper &&
                                                    settings.getFactory().getSingletonPolicy().isPolicyConfigurable());
    }

    private void resetRunOnComboBox(@Nullable String targetNameToChoose) {
      ((RunOnTargetComboBox)myRunOnComboBox).initModel();
      ((RunOnTargetComboBox)myRunOnComboBox).addTargets(TargetEnvironmentsManager.getInstance().getTargets().resolvedConfigs());
      ((RunOnTargetComboBox)myRunOnComboBox).selectTarget(targetNameToChoose);
    }

    public final JComponent getWholePanel() {
      return myWholePanel;
    }

    public JComponent getEditorComponent() {
      return getEditor().getComponent();
    }

    @Nullable
    public ValidationResult getValidationResult() {
      return SingleConfigurationConfigurable.this.getValidationResult();
    }

    private void updateWarning() {
      final ValidationResult configurationException = getValidationResult();

      if (configurationException != null) {
        mySeparator.setVisible(true);
        myWarningLabel.setVisible(true);
        myWarningLabel.setText(generateWarningLabelText(configurationException));
        final Runnable quickFix = configurationException.getQuickFix();
        if (quickFix == null) {
          myFixButton.setVisible(false);
        }
        else {
          myFixButton.setVisible(true);
          myQuickFix = quickFix;
        }
        myValidationPanel.setVisible(true);
        Window window = UIUtil.getWindow(myWholePanel);
        if (!myWindowResizedOnce && window != null && window.isShowing()) {
          Dimension size = window.getSize();
          window.setSize(size.width, size.height + myValidationPanel.getPreferredSize().height);
          myWindowResizedOnce = true;
        }
      }
      else {
        mySeparator.setVisible(false);
        myWarningLabel.setVisible(false);
        myFixButton.setVisible(false);
        myValidationPanel.setVisible(false);
      }
    }

    @NonNls
    private String generateWarningLabelText(final ValidationResult configurationException) {
      return "<html><body><b>" + configurationException.getTitle() + ": </b>" + configurationException.getMessage() + "</body></html>";
    }

    private void createUIComponents() {
      myComponentPlace = new NonOpaquePanel();
      myStoreAsFileGearButton = createStoreAsFileGearButton();
      myRunOnComboBox = new RunOnTargetComboBox(myProject);
      myManageTargetsLabel =
        LinkLabel.create(ExecutionBundle.message("edit.run.configuration.run.configuration.manage.targets.label"), () -> {
          String selectedName = ((RunOnTargetComboBox)myRunOnComboBox).getSelectedTargetName();
          TargetEnvironmentsConfigurable configurable = new TargetEnvironmentsConfigurable(myProject, selectedName);
          if (ShowSettingsUtil.getInstance().editConfigurable(myWholePanel, configurable)) {
            resetRunOnComboBox(selectedName);
          }
        });
      myJBScrollPane = wrapWithScrollPane(null);
    }

    @NotNull
    private ActionButton createStoreAsFileGearButton() {
      AnAction showStoragePathAction = new DumbAwareAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          manageStorageFileLocation();
        }
      };
      Presentation presentation = new Presentation(ExecutionBundle.message("run.configuration.manage.file.location"));
      presentation.setIcon(GEAR_WITH_DROPDOWN_ICON);
      presentation.setDisabledIcon(GEAR_WITH_DROPDOWN_DISABLED_ICON);
      return new ActionButton(showStoragePathAction, presentation, ActionPlaces.TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
        @Override
        public Icon getIcon() {
          if (myStoreAsFileCheckBox.isSelected() &&
              myRCStorageType == RCStorageType.ArbitraryFileInProject &&
              getErrorIfBadFolderPathForStoringInArbitraryFile(myProject, myFolderPathIfStoredInArbitraryFile) != null) {
            return GEAR_WITH_DROPDOWN_ERROR_ICON;
          }
          return super.getIcon();
        }
      };
    }

    private void manageStorageFileLocation() {
      Disposable balloonDisposable = Disposer.newDisposable();

      Function<String, String> pathToErrorMessage = path -> getErrorIfBadFolderPathForStoringInArbitraryFile(myProject, path);
      RunConfigurationStorageUi storageUi =
        new RunConfigurationStorageUi(myProject, getDotIdeaStoragePath(myProject), pathToErrorMessage, balloonDisposable);

      Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(storageUi.getMainPanel())
        .setDialogMode(true)
        .setBorderInsets(JBUI.insets(20, 15, 10, 15))
        .setFillColor(UIUtil.getPanelBackground())
        .setHideOnAction(false)
        .setHideOnLinkClick(false)
        .setHideOnKeyOutside(false) // otherwise any keypress in file chooser hides the underlying balloon
        .setBlockClicksThroughBalloon(true)
        .setRequestFocus(true)
        .createBalloon();
      balloon.setAnimationEnabled(false);

      String path = myRCStorageType == RCStorageType.DotIdeaFolder
                    ? getDotIdeaStoragePath(myProject)
                    : StringUtil.notNullize(myFolderPathIfStoredInArbitraryFile);

      Set<String> pathsToSuggest = new LinkedHashSet<>();
      if (getErrorIfBadFolderPathForStoringInArbitraryFile(myProject, path) == null) {
        pathsToSuggest.add(path);
      }
      if (getSettings().isStoredInArbitraryFileInProject()) {
        pathsToSuggest.add(PathUtil.getParentPath(StringUtil.notNullize(getSettings().getPathIfStoredInArbitraryFileInProject())));
      }
      pathsToSuggest.add(getDotIdeaStoragePath(myProject));
      pathsToSuggest.addAll(getFolderPathsWithinProjectWhereRunConfigurationsStored(myProject));

      storageUi.reset(path, pathsToSuggest, () -> balloon.hide());

      balloon.addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          Disposer.dispose(balloonDisposable);

          String newPath = storageUi.getPath();
          if (!newPath.equals(path)) {
            applyChangedStoragePath(newPath);
            setModified(true);
          }
        }
      });

      balloon.show(RelativePoint.getSouthOf(myStoreAsFileCheckBox), Balloon.Position.below);
    }

    private void applyChangedStoragePath(String newPath) {
      if (newPath.equals(getDotIdeaStoragePath(myProject))) {
        myRCStorageType = RCStorageType.DotIdeaFolder;
        myFolderPathIfStoredInArbitraryFile = null;
      }
      else {
        myRCStorageType = RCStorageType.ArbitraryFileInProject;
        myFolderPathIfStoredInArbitraryFile = newPath;
      }
    }
  }
}
