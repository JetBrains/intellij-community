// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.*;
import com.intellij.execution.impl.statistics.FusCollectSettingChangesRunConfiguration;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentConfigurations;
import com.intellij.execution.ui.RunnerAndConfigurationSettingsEditor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

public final class SingleConfigurationConfigurable<Config extends RunConfiguration> extends BaseRCSettingsConfigurable {

  public static final DataKey<String> RUN_ON_TARGET_NAME_KEY = DataKey.create("RunOnTargetName");

  private static final Logger LOG = Logger.getInstance(SingleConfigurationConfigurable.class);

  private final PlainDocument myNameDocument = new PlainDocument();

  private final @NotNull Project myProject;
  private final @Nullable Executor myExecutor;
  private MyValidatableComponent myComponent;
  private final @NlsContexts.ConfigurableName String myDisplayName;
  private final String myHelpTopic;
  private final boolean myBrokenConfiguration;
  private boolean myIsAllowRunningInParallel = false;
  private String myFolderName;
  private boolean myChangingNameFromCode;
  private final Alarm myValidationAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, getEditor());
  private ValidationResult myLastValidationResult = null;
  private volatile boolean myValidationRequested = true;
  private final List<ValidationListener> myValidationListeners = new SmartList<>();
  private final RunOnTargetPanel myRunOnTargetPanel;

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
            ((LocatableConfigurationBase<?>)runConfiguration).setNameChangedByUser(true);
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

    myRunOnTargetPanel = new RunOnTargetPanel(settings, getEditor());
  }

  public static @NotNull <Config extends RunConfiguration> SingleConfigurationConfigurable<Config> editSettings(@NotNull RunnerAndConfigurationSettings settings,
                                                                                                                @Nullable Executor executor) {
    SingleConfigurationConfigurable<Config> configurable = new SingleConfigurationConfigurable<>(settings, executor);
    configurable.reset();
    return configurable;
  }

  @Override
  protected @NotNull RunnerAndConfigurationSettings getSnapshot() throws ConfigurationException {
    RunnerAndConfigurationSettings snapshot = super.getSnapshot();
    snapshot.setName(getNameText());
    snapshot.setFolderName(getFolderName());
    if (hasParallelCheckBox()) {
      snapshot.getConfiguration().setAllowRunningInParallel(myIsAllowRunningInParallel);
    }
    RunnerAndConfigurationSettings original = getSettings();
    snapshot.setTemporary(original.isTemporary());

    if (myComponent != null && myComponent.myRCStorageUi != null) {
      myComponent.myRCStorageUi.apply(snapshot, false);
    }

    return snapshot;
  }

  @Override
  boolean isSpecificallyModified() {
    return myComponent != null && myComponent.myRCStorageUi != null && myComponent.myRCStorageUi.isModified() ||
           myRunOnTargetPanel.isModified() ||
           getEditor().isSpecificallyModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    RunnerAndConfigurationSettings settings = getSettings();
    RunConfiguration runConfiguration = settings.getConfiguration();

    if (runConfiguration instanceof FusCollectSettingChangesRunConfiguration) {
      RunConfiguration oldRunConfiguration = runConfiguration.clone();

      performApply(settings, runConfiguration);

      ((FusCollectSettingChangesRunConfiguration)runConfiguration)
        .collectSettingChangesOnApply((FusCollectSettingChangesRunConfiguration)oldRunConfiguration);
    }
    else {
      performApply(settings, runConfiguration);
    }
  }

  private void performApply(@NotNull RunnerAndConfigurationSettings settings,
                            @NotNull RunConfiguration runConfiguration) throws ConfigurationException {
    settings.setName(getNameText());
    runConfiguration.setAllowRunningInParallel(myIsAllowRunningInParallel);
    myRunOnTargetPanel.apply();
    settings.setFolderName(myFolderName);

    if (myComponent.myRCStorageUi != null) {
      myComponent.myRCStorageUi.apply(settings);
      myComponent.myRCStorageUi.reset(settings); // to reset its internal state
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
    myRunOnTargetPanel.reset();
  }

  void requestToUpdateWarning() {
    myValidationRequested = false;
    if (myComponent == null || isInplaceValidationSupported()) return;

    addValidationRequest();
  }

  private void addValidationRequest() {
    if (myComponent == null) return;

    ModalityState modalityState = ModalityState.stateForComponent(myComponent.myWholePanel);
    if (modalityState == ModalityState.nonModal()) return;

    myValidationRequested = true;
    myValidationAlarm.cancelAllRequests();
    myValidationAlarm.addRequest(() -> {
      if (myComponent != null) {
        if (!getEditor().isReadyForApply()) {
          addValidationRequest();
          return;
        }
        try {
          RunnerAndConfigurationSettings snapshot = createSnapshot(false);
          snapshot.setName(getNameText());
          validateResultOnBackgroundThread(snapshot);
        }
        catch (ConfigurationException e) {
          setValidationResult(createValidationResult(null, e));
        }
      }
    }, 100, modalityState);
  }

  void addValidationListener(ValidationListener listener) {
    myValidationListeners.add(listener);
  }

  private boolean isInplaceValidationSupported() {
    return getEditor() instanceof RunnerAndConfigurationSettingsEditor &&
           ((RunnerAndConfigurationSettingsEditor)getEditor()).isInplaceValidationSupported();
  }

  @Override
  public JComponent createComponent() {
    myComponent.myNameText.setEnabled(!myBrokenConfiguration);
    JComponent result = myComponent.getWholePanel();
    Dimension size = result.getPreferredSize();
    result.setPreferredSize(new Dimension(Math.min(size.width, 800), Math.min(size.height, 600)));
    return UiDataProvider.wrapComponent(result, sink -> uiDataSnapshot(sink));
  }

  private void uiDataSnapshot(@NotNull DataSink sink) {
    if (myComponent == null) return;

    sink.set(ConfigurationSettingsEditorWrapper.CONFIGURATION_EDITOR_KEY,
             getEditor() instanceof ConfigurationSettingsEditorWrapper o ? o : null);
    sink.set(RUN_ON_TARGET_NAME_KEY,
             TargetEnvironmentConfigurations.getEffectiveTargetName(myRunOnTargetPanel.getDefaultTargetName(), myProject));
    sink.set(RunConfigurationSelector.KEY, new RunConfigurationSelector() {
      @Override
      public void select(@NotNull RunConfiguration configuration) {
        RunnerAndConfigurationSettingsImpl settings = RunManagerImpl.getInstanceImpl(myProject).getSettings(configuration);
        RunDialog.editConfiguration(myProject,
                                    Objects.requireNonNull(settings),
                                    ExecutionBundle.message("edit.run.configuration.for.item.dialog.title", configuration.getName()));
      }
    });
  }

  JComponent getValidationComponent() {
    return myComponent.myValidationPanel;
  }

  public boolean isStoredInFile() {
    return myComponent != null && myComponent.myRCStorageUi != null && myComponent.myRCStorageUi.isStoredInFile();
  }

  private void validateResultOnBackgroundThread(RunnerAndConfigurationSettings snapshot) {
    getValidateAction(snapshot)
      .expireWith(getEditor())
      .coalesceBy(getEditor())
      .finishOnUiThread(ModalityState.current(), this::setValidationResult)
      .submit(NonUrgentExecutor.getInstance());
  }

  private void setValidationResult(ValidationResult result) {
    myLastValidationResult = result;
    if (myComponent != null && !isInplaceValidationSupported()) {
      myComponent.updateValidationResultVisibility(result);
    }
    for (ValidationListener listener : myValidationListeners) {
      listener.validationCompleted(result);
    }
  }

  public boolean isValid() {
    if (!myValidationRequested) {
      addValidationRequest();
    }
    return myLastValidationResult == null;
  }

  private NonBlockingReadAction<ValidationResult> getValidateAction(RunnerAndConfigurationSettings snapshot) {
    return ReadAction.nonBlocking(() -> {
      try {
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
    });
  }

  private ValidationResult createValidationResult(RunnerAndConfigurationSettings snapshot, ConfigurationException e) {
    if (!e.shouldShowInDumbMode() && DumbService.isDumb(myProject)) return null;

    return new ValidationResult(
      e.getLocalizedMessage(),
      e instanceof RuntimeConfigurationException ? e.getTitle() : ExecutionBundle.message("invalid.data.dialog.title"),
      getQuickFix(snapshot, e),
      e instanceof RuntimeConfigurationWarning
    );
  }

  private @Nullable Runnable getQuickFix(RunnerAndConfigurationSettings snapshot, ConfigurationException exception) {
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
  public void disposeUIResources() {
    super.disposeUIResources();
    myComponent = null;
  }

  public String getNameText() {
    try {
      return myNameDocument.getText(0, myNameDocument.getLength());
    }
    catch (BadLocationException e) {
      LOG.error(e);
      return "";
    }
  }

  public void addNameListener(DocumentListener listener) {
    myNameDocument.addDocumentListener(listener);
  }

  public void addSharedListener(ActionListener listener) {
    if (myComponent.myRCStorageUi != null) {
      myComponent.myRCStorageUi.addStoreAsFileCheckBoxListener(listener);
    }
  }

  public void setNameText(final String name) {
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

  public JTextField getNameTextField() {
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

  public @NotNull Config getConfiguration() {
    //noinspection unchecked
    return (Config)getSettings().getConfiguration();
  }

  public @NotNull RunnerAndConfigurationSettings createSnapshot(boolean cloneBeforeRunTasks) throws ConfigurationException {
    RunnerAndConfigurationSettings snapshot = getEditor().getSnapshot();
    RunConfiguration runConfiguration = snapshot.getConfiguration();
    runConfiguration.setAllowRunningInParallel(myIsAllowRunningInParallel);
    if (runConfiguration instanceof TargetEnvironmentAwareRunProfile) {
      ((TargetEnvironmentAwareRunProfile)runConfiguration).setDefaultTargetName(myRunOnTargetPanel.getDefaultTargetName());
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

  public @Nullable String getFolderName() {
    return myFolderName;
  }

  private final class MyValidatableComponent {
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

    private JPanel myRunOnPanel;

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
                                                  JBInsets.emptyInsets(), 0, 0));
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
        myRCStoragePanel.add(myRCStorageUi.createComponent());
      }

      myRunOnPanel.setBorder(JBUI.Borders.emptyLeft(5));
      myRunOnTargetPanel.buildUi(myRunOnPanel, myNameLabel);
      //hide validation result
      updateValidationResultVisibility(null);
    }

    private void doReset() {
      RunConfiguration configuration = getSettings().getConfiguration();
      boolean isManagedRunConfiguration = configuration.getType().isManaged();

      if (myRCStorageUi != null) {
        myRCStorageUi.reset(getSettings());
      }

      myRunOnTargetPanel.reset();
      myIsAllowRunningInParallel = configuration.isAllowRunningInParallel();
      myIsAllowRunningInParallelCheckBox.setEnabled(isManagedRunConfiguration);
      myIsAllowRunningInParallelCheckBox.setSelected(myIsAllowRunningInParallel);
      myIsAllowRunningInParallelCheckBox.setVisible(hasParallelCheckBox());
    }

    public JComponent getWholePanel() {
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
        myWarningLabel.setIcon(configurationException.isWarning() ? AllIcons.General.BalloonWarning : AllIcons.General.BalloonError);
        final Runnable quickFix = configurationException.getQuickFix();
        if (quickFix == null) {
          myFixButton.setVisible(false);
        }
        else {
          myFixButton.setVisible(true);
          myQuickFix = quickFix;
        }
        myValidationPanel.setVisible(true);
        Window window = ComponentUtil.getWindow(myWholePanel);
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

    private static @NlsContexts.Label String generateWarningLabelText(final ValidationResult configurationException) {
      return new HtmlBuilder().append(configurationException.getTitle()).append(": ")
        .wrapWith("b").wrapWith("body").addRaw(configurationException.getMessage()).wrapWith("html").toString();
    }

    private void createUIComponents() {
      myComponentPlace = new NonOpaquePanel();
      myJBScrollPane = wrapWithScrollPane(null);
    }
  }

  private boolean hasParallelCheckBox() {
    return getEditor() instanceof ConfigurationSettingsEditorWrapper &&
           getSettings().getFactory().getSingletonPolicy().isPolicyConfigurable();
  }

  interface ValidationListener {
    void validationCompleted(ValidationResult result);
  }
}
