// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.execution.*;
import com.intellij.execution.actions.RunConfigurationsComboBoxAction;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.intellij.ui.components.JBOptionButton.getDefaultTooltip;

public class EditConfigurationsDialog extends SingleConfigurableEditor {
  private static final Logger LOG = Logger.getInstance(EditConfigurationsDialog.class);
  protected Executor myExecutor;
  private final @NotNull Project myProject;
  private @Nullable Action myRunAction;
  private final List<AnAction> myExecutorActions = new ArrayList<>();
  private final @Nullable DataContext myDataContext;

  public EditConfigurationsDialog(@NotNull Project project) {
    this(project, (DataContext)null);
  }

  public EditConfigurationsDialog(@NotNull Project project, @Nullable DataContext dataContext) {
    this(project, RunConfigurableKt.createRunConfigurationConfigurable(project), null, dataContext);
  }

  public EditConfigurationsDialog(@NotNull Project project, @NotNull RunConfigurable configurable, @Nullable DataContext dataContext) {
    this(project, configurable, null, dataContext);
  }

  public EditConfigurationsDialog(@NotNull Project project, @Nullable ConfigurationFactory factory) {
    this(project, RunConfigurableKt.createRunConfigurationConfigurable(project), factory, null);
  }

  private EditConfigurationsDialog(@NotNull Project project, @NotNull RunConfigurable runConfigurable, @Nullable ConfigurationFactory factory, @Nullable DataContext dataContext) {
    super(project, runConfigurable, "#com.intellij.execution.impl.EditConfigurationsDialog", IdeModalityType.IDE);

    myProject = project;
    myDataContext = dataContext;

    getConfigurable().getTree().registerKeyboardAction((event) -> {
      clickDefaultButton();
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
    getConfigurable().setDialogUpdateCallback(() -> updateDialog());

    getConfigurable().initTreeSelectionListener(getDisposable());
    setTitle(ExecutionBundle.message("run.debug.dialog.title"));
    setHorizontalStretch(1.3F);
    RunnerAndConfigurationSettings initial = null;
    if (factory != null) {
      addRunConfiguration(factory);
    } else {
      getConfigurable().selectConfigurableOnShow();
      initial = getConfigurable().getInitialSelectedConfiguration();
    }
    updateSelectedExecutor(initial);
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
  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected Action @NotNull [] createActions() {
    Action[] actions = super.createActions();
    if (myExecutor != null) {
      return actions;
    }
    myRunAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        SingleConfigurationConfigurable<RunConfiguration> selected = getConfigurable().getSelectedConfiguration();
        LOG.assertTrue(selected != null, "No configuration selected");
        LOG.assertTrue(myExecutor != null, "No executor selected");
        ExecutorRegistryImpl.RunnerHelper.run(myProject, selected.getConfiguration(), selected.getSettings(),
                                              Objects.requireNonNull(myDataContext), myExecutor);
        doOKAction();
      }
    };
    myRunAction.putValue(DialogWrapper.MAC_ACTION_ORDER, -100);
    return ArrayUtil.prepend(myRunAction, actions);
  }

  @Override
  protected JButton createJButtonForAction(Action action) {
    if (action == myRunAction) {
      JBOptionButton button = new JBOptionButton(action, null);
      button.setAddSeparator(false);
      button.setOptionTooltipText(getDefaultTooltip());
      button.setIconTextGap(JBUI.CurrentTheme.ActionsList.elementIconGap());
      return button;
    }
    return super.createJButtonForAction(action);
  }

  private void updateSelectedExecutor(@Nullable RunnerAndConfigurationSettings selected) {
    if (myRunAction == null) return;
    Executor executor = null;
    if (selected != null) {
      executor = ExecutionManagerImpl.getInstance(myProject).getRecentExecutor(selected);
    }
    if (executor == null) {
      executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
      if (executor != null && selected != null && !canRun(selected, executor)) {
        executor = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);
        if (executor != null && !canRun(selected, executor)) {
          executor = null;
        }
      }
    }
    updateRunButton(executor, selected);
  }

  private boolean canRun(@Nullable RunnerAndConfigurationSettings settings, Executor executor) {
    return settings != null && ExecutorRegistryImpl.RunnerHelper.canRun(myProject, executor, settings.getConfiguration());
  }

  private void updateRunButton(@Nullable Executor executor, @Nullable RunnerAndConfigurationSettings selected) {
    myExecutor = executor;
    JBOptionButton button = (JBOptionButton)Objects.requireNonNull(getButton(Objects.requireNonNull(myRunAction)));
    button.setVisible(executor != null && myDataContext != null);
    button.setEnabled(executor != null && myDataContext != null);
    if (executor != null) {
      myRunAction.putValue(Action.NAME, executor.getActionName());
      button.setText(executor.getActionName());
      button.setIcon(executor.getIcon());
      myExecutorActions.forEach(action -> action.unregisterCustomShortcutSet(getContentPanel()));
      myExecutorActions.clear();
      if (selected != null) {
        ExecutorRegistryImpl.ExecutorAction action = createAction(selected, executor);
        DefaultActionGroup group = new DefaultActionGroup();
        RunConfigurationsComboBoxAction.forAllExecutors(o -> {
          if (o != executor) {
            group.addAction(createAction(selected, o));
          }
        });
        button.setOptions(Arrays.asList(group.getChildren(null)));
        button.setToolTipText(UIUtil.removeMnemonic(executor.getStartActionText(selected.getName())) + " (" + KeymapUtil.getFirstKeyboardShortcutText(action) + ")");
      }
    }
  }

  @NotNull
  private ExecutorRegistryImpl.ExecutorAction createAction(@NotNull RunnerAndConfigurationSettings selected, @NotNull Executor executor) {
    return new ExecutorRegistryImpl.ExecutorAction(executor) {
      {
        AnAction action = ActionManager.getInstance().getAction(executor.getId());
        if (action != null) {
          setShortcutSet(action.getShortcutSet());
          registerCustomShortcutSet(getContentPanel(), null);
        }
        myExecutorActions.add(this);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        run(myProject, selected, Objects.requireNonNull(myDataContext));
        doOKAction();
      }

      @Override
      protected RunnerAndConfigurationSettings getSelectedConfiguration(@NotNull AnActionEvent e) {
        return selected;
      }

      @Override
      protected boolean hideDisabledExecutorButtons() {
        return true;
      }
    };
  }

  private void updateDialog() {
    if (myExecutor != null && myRunAction == null) {
      updateDialogForSingleExecutor();
    }
    else {
      SingleConfigurationConfigurable<RunConfiguration> configurable = getConfigurable().getSelectedConfiguration();
      updateSelectedExecutor(configurable == null ? null : configurable.getSettings());
    }
  }

  private void updateDialogForSingleExecutor() {
    @Nls StringBuilder buffer = new StringBuilder();
    buffer.append(myExecutor.getId());
    SingleConfigurationConfigurable<RunConfiguration> configuration = getConfigurable().getSelectedConfiguration();
    if (configuration != null) {
      buffer.append(" - ");
      buffer.append(configuration.getNameText());

      ReadAction.nonBlocking(() -> canRun(configuration.getSettings(), myExecutor))
        .finishOnUiThread(ModalityState.current(), b -> setOKActionEnabled(b))
        .expireWith(getDisposable())
        .submit(AppExecutorUtil.getAppExecutorService());
    }
    setTitle(buffer.toString());
  }
}