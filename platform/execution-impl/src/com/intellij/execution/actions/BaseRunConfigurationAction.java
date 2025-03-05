 // Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerProvider;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public abstract class BaseRunConfigurationAction extends ActionGroup implements ActionRemoteBehaviorSpecification.BackendOnly, DumbAware {
  protected static final Logger LOG = Logger.getInstance(BaseRunConfigurationAction.class);

  protected BaseRunConfigurationAction(@NotNull Supplier<String> text, @NotNull Supplier<String> description, final Icon icon) {
    super(text, description, icon);
    setPopup(true);
    setEnabledInModalContext(true);
  }

  protected BaseRunConfigurationAction(@NotNull Supplier<String> text,
                                       @NotNull Supplier<String> description,
                                       @Nullable Supplier<? extends @Nullable Icon> icon) {
    super(text, description, icon);

    setPopup(true);
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return e != null ? getChildren(e.getDataContext(), e.getPlace()) : EMPTY_ARRAY;
  }

  private AnAction @NotNull [] getChildren(@NotNull DataContext dataContext, @Nullable String place) {
    if (dataContext.getData(ExecutorAction.getOrderKey()) != null) return EMPTY_ARRAY;
    final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext, place); //!!! to rule???
    if (!Registry.is("suggest.all.run.configurations.from.context") && findExisting(context) != null) {
      return EMPTY_ARRAY;
    }
    return createChildActions(context, getConfigurationsFromContext(context)).toArray(EMPTY_ARRAY);
  }

  protected @Nullable RunnerAndConfigurationSettings findExisting(@NotNull ConfigurationContext context) {
    return context.findExisting();
  }

  protected @NotNull List<AnAction> createChildActions(@NotNull ConfigurationContext context,
                                                       @NotNull List<? extends ConfigurationFromContext> configurations) {
    if (configurations.size() <= 1) {
      return Collections.emptyList();
    }
    final List<AnAction> childActions = new ArrayList<>();
    for (final ConfigurationFromContext fromContext : configurations) {
      final ConfigurationType configurationType = fromContext.getConfigurationType();
      final String actionName = childActionName(fromContext);
      //noinspection DialogTitleCapitalization
      final AnAction anAction = new AnAction(actionName, configurationType.getDisplayName(), fromContext.getConfiguration().getIcon()) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          perform(fromContext, ConfigurationContext.getFromContext(e.getDataContext(), e.getPlace()));
        }
      };
      anAction.getTemplatePresentation().setText(actionName, false);
      childActions.add(anAction);
    }
    return childActions;
  }

  private @NotNull List<ConfigurationFromContext> getConfigurationsFromContext(@NotNull ConfigurationContext context) {
    final List<ConfigurationFromContext> fromContext = context.getConfigurationsFromContext();
    if (fromContext == null) {
      return Collections.emptyList();
    }

    final List<ConfigurationFromContext> enabledConfigurations = new ArrayList<>();
    for (ConfigurationFromContext configurationFromContext : fromContext) {
      if (isEnabledFor(configurationFromContext.getConfiguration(), context)) {
        enabledConfigurations.add(configurationFromContext);
      }
    }
    return enabledConfigurations;
  }

  protected boolean isEnabledFor(@NotNull RunConfiguration configuration) {
    return true;
  }
  
  protected boolean isEnabledFor(@NotNull RunConfiguration configuration, @NotNull ConfigurationContext context) {
    return isEnabledFor(configuration);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    ConfigurationContext context = ConfigurationContext.getFromContext(dataContext, e.getPlace());
    RunnerAndConfigurationSettings existing = findExisting(context);
    if (existing == null || dataContext.getData(ExecutorAction.getOrderKey()) != null) {
      List<ConfigurationFromContext> producers = getConfigurationsFromContext(context);
      if (producers.isEmpty()) return;
      ConfigurationFromContext configuration = getOrderedConfiguration(dataContext, producers);
      if (ActionUtil.isDumbMode(context.getProject()) && !configuration.getConfiguration().getType().isDumbAware()) {
        ActionUtil.showDumbModeWarning(context.getProject(), this, e);
      }
      else {
        perform(configuration, context);
      }
    }
    else {
      if (LOG.isDebugEnabled()) {
        String configurationClass = existing.getConfiguration().getClass().getName();
        LOG.debug(String.format("Use existing run configuration: %s", configurationClass));
      }
      Project project = Objects.requireNonNull(context.getProject());
      if (ActionUtil.isDumbMode(context.getProject()) && !existing.getType().isDumbAware()) {
        ActionUtil.showDumbModeWarning(project, this, e);
      }
      else {
        perform(existing, context);
      }
    }
  }

  private static @NotNull ConfigurationFromContext getOrderedConfiguration(@NotNull DataContext dataContext,
                                                                           @NotNull List<? extends ConfigurationFromContext> producers) {
    Integer order = dataContext.getData(ExecutorAction.getOrderKey());
    if (order != null && order < producers.size()) {
      return producers.get(order);
    }
    return producers.get(0);
  }

  private void perform(@NotNull ConfigurationFromContext configurationFromContext, @NotNull ConfigurationContext context) {
    int eventCount = IdeEventQueue.getInstance().getEventCount();
    RunnerAndConfigurationSettings configurationSettings = configurationFromContext.getConfigurationSettings();
    context.setConfiguration(configurationSettings);
    configurationFromContext.onFirstRun(context, () -> {
      if (LOG.isDebugEnabled()) {
        RunnerAndConfigurationSettings settings = context.getConfiguration();
        RunConfiguration configuration = settings == null ? null : settings.getConfiguration();
        String configurationClass = configuration == null ? null : configuration.getClass().getName();
        LOG.debug(String.format("Create run configuration: %s", configurationClass));
      }
      // Reset event counter if some UI was shown as
      // stated in `ConfigurationFromContext.onFirstRun` javadoc.
      // Can be removed if pre-cached context is used in ConfigurationFromContext.
      if (!Utils.isAsyncDataContext(context.getDataContext()) &&
          eventCount != IdeEventQueue.getInstance().getEventCount()) {
        IdeEventQueue.getInstance().setEventCount(eventCount);
      }
      perform(configurationSettings, context);
    });
  }

  protected void perform(@NotNull RunnerAndConfigurationSettings configurationSettings,
                         @NotNull ConfigurationContext context) {
    perform(context);
  }

  /** @deprecated Use {@link #perform(RunnerAndConfigurationSettings, ConfigurationContext)} instead */
  @Deprecated(forRemoval = true)
  protected void perform(ConfigurationContext context) {
  }

  /** @deprecated Use regular {@link #update(AnActionEvent)} instead */
  @Deprecated(forRemoval = true)
  protected void fullUpdate(@NotNull AnActionEvent event) {
    update(event);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    ConfigurationContext context = ConfigurationContext.getFromEvent(event);
    Presentation presentation = event.getPresentation();
    RunnerAndConfigurationSettings existing = findExisting(context);
    RunnerAndConfigurationSettings configuration = existing;
    if (configuration == null) {
      configuration = context.getConfiguration();
    }
    if (configuration == null ||
        ActionUtil.isDumbMode(context.getProject()) && !configuration.getType().isDumbAware()) {
      presentation.setEnabledAndVisible(false);
      presentation.setPerformGroup(false);
    }
    else {
      presentation.setEnabledAndVisible(true);
      VirtualFile vFile = dataContext.getData(CommonDataKeys.VIRTUAL_FILE);
      if (vFile != null) {
        RunLineMarkerProvider.markRunnable(vFile, true);
      }
      final List<ConfigurationFromContext> fromContext = getConfigurationsFromContext(context);
      if (existing == null && fromContext.isEmpty()) {
        presentation.setEnabledAndVisible(false);
        presentation.setPerformGroup(false);
        return;
      }
      if ((existing == null || dataContext.getData(ExecutorAction.getOrderKey()) != null) && !fromContext.isEmpty()) {
        ConfigurationFromContext configurationFromContext = getOrderedConfiguration(dataContext, fromContext);
        configuration = configurationFromContext.getConfigurationSettings();
        context.setConfiguration(configurationFromContext.getConfigurationSettings());
      }
      final String name = suggestRunActionName(configuration.getConfiguration());

      boolean performGroup = existing != null || fromContext.size() <= 1 || dataContext.getData(ExecutorAction.getOrderKey()) != null;
      updatePresentation(presentation, performGroup ? name : "", context);
      presentation.setPerformGroup(performGroup);
    }
  }

  public static @NotNull @Nls String suggestRunActionName(@NotNull RunConfiguration configuration) {
    if (configuration instanceof LocatableConfigurationBase && ((LocatableConfigurationBase<?>)configuration).isGeneratedName()) {
      String actionName = ((LocatableConfigurationBase<?>)configuration).getActionName();
      if (actionName != null) {
        return actionName;
      }
    }
    return ProgramRunnerUtil.shortenName(configuration.getName(), 0); 
  }
  
  public static @NotNull String suggestRunActionName(@NotNull LocatableConfiguration configuration) {
    return suggestRunActionName((RunConfiguration)configuration);
  }

  private static @NotNull @Nls String childActionName(@NotNull ConfigurationFromContext configurationFromContext) {
    RunConfiguration configuration = configurationFromContext.getConfiguration();
    if (!(configuration instanceof LocatableConfiguration)) {
      return configurationFromContext.getConfigurationType().getDisplayName();
    }
    if (configurationFromContext.isFromAlternativeLocation()) {
      String locationDisplayName = configurationFromContext.getAlternativeLocationDisplayName();
      if (locationDisplayName != null) {
        return ((LocatableConfigurationBase<?>)configuration).getActionName() + " " + locationDisplayName;
      }
    }

    return StringUtil.unquoteString(suggestRunActionName(configurationFromContext.getConfiguration()));
  }

  protected abstract void updatePresentation(@NotNull Presentation presentation,
                                             @NotNull String actionText, ConfigurationContext context);

}
