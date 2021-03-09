// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.RunOnTargetComboBox;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.*;
import com.intellij.execution.ui.RunnerAndConfigurationSettingsEditor;
import com.intellij.execution.ui.TargetAwareRunConfigurationEditor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.NonBlockingReadAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ConfigurationQuickFix;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class SingleConfigurationConfigurable<Config extends RunConfiguration> extends BaseRCSettingsConfigurable {

  public static final DataKey<String> RUN_ON_TARGET_NAME_KEY = DataKey.create("RunOnTargetName");

  private static final Logger LOG = Logger.getInstance(SingleConfigurationConfigurable.class);

  private final PlainDocument myNameDocument = new PlainDocument();

  @NotNull private final Project myProject;
  @Nullable private final Executor myExecutor;
  private MyValidatableComponent myComponent;
  private final @NlsContexts.ConfigurableName String myDisplayName;
  private final String myHelpTopic;
  private final boolean myBrokenConfiguration;
  private boolean myIsAllowRunningInParallel = false;
  private String myDefaultTargetName;
  private String myFolderName;
  private boolean myChangingNameFromCode;
  private CancellablePromise<ValidationResult> myCancellablePromise;
  private final Alarm myValidationAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, getEditor());

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

    getEditor().addSettingsEditorListener(new SettingsEditorListener<>() {
      @Override
      public void stateChanged(@NotNull SettingsEditor<RunnerAndConfigurationSettings> settingsEditor) {
        requestToUpdateWarning();
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
    return myComponent != null && myComponent.myRCStorageUi != null && myComponent.myRCStorageUi.isModified();
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

    if (myComponent.myRCStorageUi != null) {
      myComponent.myRCStorageUi.apply(settings);
    }

    super.apply();
    RunManagerImpl.getInstanceImpl(myProject).addConfiguration(settings);
  }

  @Override
  public void reset() {
    RunnerAndConfigurationSettings configuration = getSettings();
    setNameText(configuration.getName());
    super.reset();
    if (myComponent == null) {
      myComponent = new MyValidatableComponent();
    }
    myComponent.doReset();
  }

  void requestToUpdateWarning() {
    if (myComponent != null && myValidationAlarm.isEmpty()) {
      myValidationAlarm.addRequest(() -> {
        boolean inplaceValidationSupported = getEditor() instanceof RunnerAndConfigurationSettingsEditor && ((RunnerAndConfigurationSettingsEditor)getEditor()).isInplaceValidationSupported();
        if (myComponent != null && !inplaceValidationSupported) {
          validateResultOnBackgroundThread(configurationException -> myComponent.updateValidationResultVisibility(configurationException));
        }
      }, 100, ModalityState.stateForComponent(myComponent.myWholePanel));
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
    Dimension size = result.getPreferredSize();
    result.setPreferredSize(new Dimension(Math.min(size.width, 800), Math.min(size.height, 600)));
    return result;
  }

  final JComponent getValidationComponent() {
    return myComponent.myValidationPanel;
  }

  public boolean isStoredInFile() {
    return myComponent != null && myComponent.myRCStorageUi != null && myComponent.myRCStorageUi.isStoredInFile();
  }
  
  private void validateResultOnBackgroundThread(Consumer<ValidationResult> onUIThread) {
    if (myCancellablePromise != null && !myCancellablePromise.isDone()) {
      myCancellablePromise.cancel();
    }
    myCancellablePromise = getValidateAction()
      .finishOnUiThread(ModalityState.current(), onUIThread)
      .submit(NonUrgentExecutor.getInstance());
  }

  public boolean isValid() {
    return getValidateAction().executeSynchronously() == null;
  }
  
  private NonBlockingReadAction<ValidationResult> getValidateAction() {
    return ReadAction.nonBlocking(() -> {
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
        return createValidationResult(snapshot, e);
      }
      return null;
    })
      .expireWith(getEditor());
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
    ConfigurationQuickFix quickFix = exception.getConfigurationQuickFix();
    if (quickFix != null && snapshot != null) {
      return () -> {
        quickFix.applyFix(DataManager.getInstance().getDataContext(myComponent.myWholePanel));
        getEditor().resetFrom(snapshot);
      };
    }
    return quickFix == null ? null :
           () -> quickFix.applyFix(DataManager.getInstance().getDataContext(myComponent.myWholePanel));
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
    if (myComponent.myRCStorageUi != null) {
      myComponent.myRCStorageUi.addStoreAsFileCheckBoxListener(listener);
    }
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

  private class MyValidatableComponent {
    private JLabel myNameLabel;
    private JTextField myNameText;
    private JComponent myWholePanel;
    private JPanel myComponentPlace;
    private JBLabel myWarningLabel;
    private JButton myFixButton;
    private JSeparator mySeparator;
    private JBCheckBox myIsAllowRunningInParallelCheckBox;

    private JPanel myRCStoragePanel;
    private final @Nullable RunConfigurationStorageUi myRCStorageUi;

    private JPanel myValidationPanel;
    private JBScrollPane myJBScrollPane;

    private ComboBox myRunOnComboBox;
    private ActionLink myManageTargetsLabel;
    private JPanel myRunOnPanel;
    private JPanel myRunOnPanelInner;

    private Runnable myQuickFix = null;
    private boolean myWindowResizedOnce = false;

    MyValidatableComponent() {
      myNameLabel.setLabelFor(myNameText);
      myNameText.setDocument(myNameDocument);

      getEditor().addSettingsEditorListener(settingsEditor -> requestToUpdateWarning());
      myWarningLabel.setCopyable(true);
      myWarningLabel.setAllowAutoWrapping(true);
      myWarningLabel.setIcon(AllIcons.General.BalloonError);

      myComponentPlace.setLayout(new GridBagLayout());
      myComponentPlace.add(getEditorComponent(),
                           new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                  JBUI.emptyInsets(), 0, 0));
      myComponentPlace.doLayout();
      myFixButton.setIcon(AllIcons.Actions.QuickfixBulb);
      requestToUpdateWarning();
      myFixButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          if (myQuickFix == null) {
            return;
          }
          myQuickFix.run();
          requestToUpdateWarning();
        }
      });

      myIsAllowRunningInParallelCheckBox.addActionListener(e -> {
        setModified(true);
        myIsAllowRunningInParallel = myIsAllowRunningInParallelCheckBox.isSelected();
      });

      myRCStorageUi = !myProject.isDefault() ? new RunConfigurationStorageUi(myProject, () -> setModified(true))
                                             : null;
      if (myRCStorageUi != null) {
        myRCStorageUi.setShowCompatibilityHint(true);
        myRCStoragePanel.add(myRCStorageUi.createComponent());
      }

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
          setTargetName(chosenTarget);
          requestToUpdateWarning();
        }
      });
      //hide validation result
      updateValidationResultVisibility(null);
    }

    private void doReset() {
      RunConfiguration configuration = getSettings().getConfiguration();
      boolean isManagedRunConfiguration = configuration.getType().isManaged();

      if (myRCStorageUi != null) {
        myRCStorageUi.reset(getSettings());
      }

      boolean targetAware =
        configuration instanceof TargetEnvironmentAwareRunProfile &&
        ((TargetEnvironmentAwareRunProfile)configuration).getDefaultLanguageRuntimeType() != null &&
        Experiments.getInstance().isFeatureEnabled("run.targets");
      myRunOnPanel.setVisible(targetAware);
      if (targetAware) {
        String defaultTargetName = ((TargetEnvironmentAwareRunProfile)configuration).getDefaultTargetName();
        LanguageRuntimeType<?> defaultRuntime = ((TargetEnvironmentAwareRunProfile)configuration).getDefaultLanguageRuntimeType();
        ((RunOnTargetComboBox)myRunOnComboBox).setDefaultLanguageRuntimeType(defaultRuntime);
        resetRunOnComboBox(defaultTargetName);
        setTargetName(defaultTargetName);
      }

      myIsAllowRunningInParallel = configuration.isAllowRunningInParallel();
      myIsAllowRunningInParallelCheckBox.setEnabled(isManagedRunConfiguration);
      myIsAllowRunningInParallelCheckBox.setSelected(myIsAllowRunningInParallel);
      myIsAllowRunningInParallelCheckBox.setVisible(getEditor() instanceof ConfigurationSettingsEditorWrapper &&
                                                    getSettings().getFactory().getSingletonPolicy().isPolicyConfigurable());
    }

    private void resetRunOnComboBox(@Nullable String targetNameToChoose) {
      ((RunOnTargetComboBox)myRunOnComboBox).initModel();
      List<TargetEnvironmentConfiguration> configs = TargetEnvironmentsManager.getInstance(myProject).getTargets().resolvedConfigs();
      ((RunOnTargetComboBox)myRunOnComboBox).addTargets(ContainerUtil.filter(configs, configuration -> {
        return TargetEnvironmentConfigurationKt.getTargetType(configuration).isSystemCompatible();
      }));
      ((RunOnTargetComboBox)myRunOnComboBox).selectTarget(targetNameToChoose);
    }

    public final JComponent getWholePanel() {
      return myWholePanel;
    }

    public JComponent getEditorComponent() {
      return getEditor().getComponent();
    }

    private void updateValidationResultVisibility(ValidationResult configurationException) {
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

    private @NlsContexts.Label String generateWarningLabelText(final ValidationResult configurationException) {
      return new HtmlBuilder().append(configurationException.getTitle()).append(": ")
        .wrapWith("b").wrapWith("body").addRaw(configurationException.getMessage()).wrapWith("html").toString();
    }

    private void createUIComponents() {
      myComponentPlace = new NonOpaquePanel();
      myRunOnComboBox = new RunOnTargetComboBox(myProject);
      myManageTargetsLabel =
        new ActionLink(ExecutionBundle.message("edit.run.configuration.run.configuration.manage.targets.label"), e -> {
          String selectedName = ((RunOnTargetComboBox)myRunOnComboBox).getSelectedTargetName();
          LanguageRuntimeType<?> languageRuntime = ((RunOnTargetComboBox)myRunOnComboBox).getDefaultLanguageRuntimeType();
          TargetEnvironmentsConfigurable configurable = new TargetEnvironmentsConfigurable(myProject, selectedName, languageRuntime);
          if (configurable.openForEditing()) {
            TargetEnvironmentConfiguration lastEdited = configurable.getSelectedTargetConfig();
            String chosenTargetName = lastEdited != null ? lastEdited.getDisplayName() : selectedName;
            resetRunOnComboBox(chosenTargetName);
            setTargetName(chosenTargetName);
            requestToUpdateWarning();
          }
        });
      myJBScrollPane = wrapWithScrollPane(null);
    }
  }

  private void setTargetName(String chosenTarget) {
    myDefaultTargetName = chosenTarget;
    SettingsEditor<RunnerAndConfigurationSettings> editor = getEditor();
    if (editor instanceof TargetAwareRunConfigurationEditor) {
      ((TargetAwareRunConfigurationEditor)editor).targetChanged(chosenTarget);
    }
  }
}
