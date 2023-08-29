// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistryImpl;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

public class EditConfigurationsDialog extends SingleConfigurableEditor implements RunDialogBase {
  protected Executor myExecutor;
  private final @NotNull Project myProject;
  private @Nullable Action myRunAction;
  private final @Nullable DataContext myDataContext;

  public EditConfigurationsDialog(@NotNull Project project) {
    this(project, (DataContext)null);
  }

  public EditConfigurationsDialog(@NotNull Project project, @Nullable DataContext dataContext) {
    this(project, RunConfigurableKt.createRunConfigurationConfigurable(project), null, dataContext);
  }

  public EditConfigurationsDialog(@NotNull Project project, @Nullable RunConfigurable configurable, @Nullable DataContext dataContext) {
    this(project, configurable, null, dataContext);
  }

  public EditConfigurationsDialog(@NotNull Project project, @Nullable ConfigurationFactory factory) {
    this(project, RunConfigurableKt.createRunConfigurationConfigurable(project), factory, null);
  }

  private EditConfigurationsDialog(@NotNull Project project, RunConfigurable runConfigurable, @Nullable ConfigurationFactory factory, @Nullable DataContext dataContext) {
    super(project, runConfigurable, "#com.intellij.execution.impl.EditConfigurationsDialog", IdeModalityType.IDE);

    myProject = project;
    myDataContext = dataContext;

    getConfigurable().setRunDialog(this);
    getConfigurable().initTreeSelectionListener(getDisposable());
    setTitle(ExecutionBundle.message("run.debug.dialog.title"));
    setHorizontalStretch(1.3F);
    if (factory != null) {
      addRunConfiguration(factory);
    } else {
      getConfigurable().selectConfigurableOnShow();
    }
  }

  @Override
  public RunConfigurable getConfigurable() {
    return (RunConfigurable)super.getConfigurable();
  }

  private void addRunConfiguration(final @NotNull ConfigurationFactory factory) {
    final RunConfigurable configurable = getConfigurable();
    final SingleConfigurationConfigurable<RunConfiguration> configuration = configurable.createNewConfiguration(factory);

    if (!isVisible()) {
       getContentPanel().addComponentListener(new ComponentAdapter() {
         @Override
         public void componentShown(ComponentEvent e) {
           configurable.updateRightPanel(configuration);
           getContentPanel().removeComponentListener(this);
         }
       });
    }
  }

  @Override
  protected void doOKAction() {
    RunConfigurable configurable = getConfigurable();
    super.doOKAction();
    if (isOK()) {
      // if configurable was not modified, apply was not called and Run Configurable has not called 'updateActiveConfigurationFromSelected'
      configurable.updateActiveConfigurationFromSelected();
    }
  }

  @Override
  public @Nullable Executor getExecutor() {
    return myExecutor;
  }

  @Override
  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected Action @NotNull [] createActions() {
    List<Action> actions = new ArrayList<>(List.of(super.createActions()));
    myRunAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        performRunFromConfig((runnerAndConfigurationSettings, executor) -> {
          ApplicationManager.getApplication().invokeLater(() -> {
            RunConfiguration configuration = runnerAndConfigurationSettings.getConfiguration();
            DataContext context = Objects.requireNonNull(myDataContext);
            ExecutorRegistryImpl.RunnerHelper.run(myProject, configuration, runnerAndConfigurationSettings, context, executor);
            doOKAction();
          }, ModalityState.any());
        }, () -> {
          Logger.getInstance(EditConfigurationsDialog.class).error("Should not be possible to click it when nothing can be run");
        });
      }
    };
    actions.add(0, myRunAction);
    return actions.toArray(new Action[0]);
  }

  @Override
  protected JButton createJButtonForAction(Action action) {
    JButton button = super.createJButtonForAction(action);
    button.setVisible(myRunAction != action || getConfigurable().getInitialSelectedConfiguration() != null);
    return button;
  }

  private void performRunFromConfig(@NotNull BiConsumer<@NotNull RunnerAndConfigurationSettings, @NotNull Executor> onRunnableExecutor,
                                    @NotNull Runnable onFail) {
    SingleConfigurationConfigurable<RunConfiguration> configurable = getConfigurable().getSelectedConfiguration();
    if (configurable != null) {
      processInBackground(onRunnableExecutor, onFail, configurable.getSettings());
    } else {
      onFail.run();
    }
  }

  private void processInBackground(@NotNull BiConsumer<@NotNull RunnerAndConfigurationSettings, @NotNull Executor> onRunnableExecutor,
                                   @NotNull Runnable onFail, RunnerAndConfigurationSettings runnerAndConfigurationSettings) {
    AppExecutorUtil.getAppExecutorService().execute(() -> {
      for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
        if (ExecutorRegistryImpl.RunnerHelper.canRun(myProject, executor, runnerAndConfigurationSettings.getConfiguration())) {
          onRunnableExecutor.accept(runnerAndConfigurationSettings, executor);
          return;
        }
      }
      onFail.run();
    });
  }

  public void updateRunAction() {
    if (myRunAction == null) {
      return;
    }
    JButton button = Objects.requireNonNull(getButton(myRunAction));
    if (myDataContext == null) {
      button.setEnabled(false);
      button.setVisible(false);
    }

    performRunFromConfig((runnerAndConfigurationSettings, executor) -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        button.setVisible(true);
        button.setEnabled(true);
        myRunAction.putValue(Action.NAME, executor.getActionName());
        button.setText(executor.getActionName());
      }, ModalityState.any());
    }, () -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        button.setEnabled(false);
        button.setVisible(false);
      }, ModalityState.any());
    });
 }
}