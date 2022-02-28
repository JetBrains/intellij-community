// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import com.intellij.ide.macro.MacroManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public abstract class BaseRunConfigurationAction extends ActionGroup implements UpdateInBackground {
  protected static final Logger LOG = Logger.getInstance(BaseRunConfigurationAction.class);

  protected BaseRunConfigurationAction(@NotNull Supplier<String> text, @NotNull Supplier<String> description, final Icon icon) {
    super(text, description, icon);
    setPopup(true);
    setEnabledInModalContext(true);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return e != null ? getChildren(e.getDataContext(), e.getPlace()) : EMPTY_ARRAY;
  }

  private AnAction[] getChildren(DataContext dataContext, @Nullable String place) {
    if (dataContext.getData(ExecutorAction.getOrderKey()) != null) return EMPTY_ARRAY;
    final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext, place); //!!! to rule???
    if (!Registry.is("suggest.all.run.configurations.from.context") && findExisting(context) != null) {
      return EMPTY_ARRAY;
    }
    return createChildActions(context, getConfigurationsFromContext(context)).toArray(EMPTY_ARRAY);
  }

  @Nullable
  protected RunnerAndConfigurationSettings findExisting(ConfigurationContext context) {
    return context.findExisting();
  }

  @NotNull
  protected List<AnAction> createChildActions(@NotNull ConfigurationContext context,
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

  @NotNull
  private List<ConfigurationFromContext> getConfigurationsFromContext(ConfigurationContext context) {
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

  protected boolean isEnabledFor(RunConfiguration configuration) {
    return true;
  }
  
  protected boolean isEnabledFor(RunConfiguration configuration, ConfigurationContext context) {
    return isEnabledFor(configuration);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    MacroManager.getInstance().cacheMacrosPreview(e.getDataContext());
    final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext, e.getPlace());
    final RunnerAndConfigurationSettings existing = findExisting(context);
    if (existing == null || dataContext.getData(ExecutorAction.getOrderKey()) != null) {
      final List<ConfigurationFromContext> producers = getConfigurationsFromContext(context);
      if (producers.isEmpty()) return;
      perform(getOrderedConfiguration(dataContext, producers), context);
      return;
    }

    if (LOG.isDebugEnabled()) {
      String configurationClass = existing.getConfiguration().getClass().getName();
      LOG.debug(String.format("Use existing run configuration: %s", configurationClass));
    }
    perform(context);
  }

  private static ConfigurationFromContext getOrderedConfiguration(DataContext dataContext, List<ConfigurationFromContext> producers) {
    Integer order = dataContext.getData(ExecutorAction.getOrderKey());
    if (order != null && order < producers.size()) {
      return producers.get(order);
    }
    return producers.get(0);
  }

  private void perform(final ConfigurationFromContext configurationFromContext, final ConfigurationContext context) {
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

  protected void perform(RunnerAndConfigurationSettings configurationSettings, ConfigurationContext context) {
    perform(context);
  }

  protected abstract void perform(ConfigurationContext context);

  @Override
  public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
    fullUpdate(e);
  }

  @Nullable private static Integer ourLastTimeoutStamp = null;

  @Override
  public void update(@NotNull final AnActionEvent event) {
    boolean doFullUpdate = !ApplicationManager.getApplication().isDispatchThread() ||
                           ApplicationManager.getApplication().isUnitTestMode();
    VirtualFile vFile = event.getDataContext().getData(CommonDataKeys.VIRTUAL_FILE);
    ThreeState hadAnythingRunnable = vFile == null ? ThreeState.UNSURE : RunLineMarkerProvider.hadAnythingRunnable(vFile);
    if (doFullUpdate || hadAnythingRunnable == ThreeState.UNSURE) {
      fullUpdate(event);
      return;
    }

    boolean success =
      !alreadyExceededTimeoutOnSimilarAction() &&
      ProgressIndicatorUtils.withTimeout(Registry.intValue("run.configuration.update.timeout"), () -> {
        fullUpdate(event);
        return true;
      }) != null;
    if (!success) {
      recordUpdateTimeout();
      approximatePresentationByPreviousAvailability(event, hadAnythingRunnable);
      event.getPresentation().setPerformGroup(false);
    }
  }

  private static boolean alreadyExceededTimeoutOnSimilarAction() {
    return Objects.equals(IdeEventQueue.getInstance().getEventCount(), ourLastTimeoutStamp);
  }

  private static void recordUpdateTimeout() {
    ourLastTimeoutStamp = IdeEventQueue.getInstance().getEventCount();
  }

  // we assume that presence of anything runnable in a file changes rarely, so using last recorded state is mostly OK
  protected void approximatePresentationByPreviousAvailability(AnActionEvent event, ThreeState hadAnythingRunnable) {
    event.getPresentation().copyFrom(getTemplatePresentation());
    event.getPresentation().setEnabledAndVisible(hadAnythingRunnable == ThreeState.YES);
  }

  protected void fullUpdate(@NotNull AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext, event.getPlace());
    final Presentation presentation = event.getPresentation();
    final RunnerAndConfigurationSettings existing = findExisting(context);
    RunnerAndConfigurationSettings configuration = existing;
    if (configuration == null) {
      configuration = context.getConfiguration();
    }
    if (configuration == null){
      presentation.setEnabledAndVisible(false);
      presentation.setPerformGroup(false);
    }
    else{
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

  @Override
  public boolean isDumbAware() {
    return false;
  }

  @NotNull
  @Nls
  public static String suggestRunActionName(@NotNull RunConfiguration configuration) {
    if (configuration instanceof LocatableConfigurationBase && ((LocatableConfigurationBase<?>)configuration).isGeneratedName()) {
      String actionName = ((LocatableConfigurationBase<?>)configuration).getActionName();
      if (actionName != null) {
        return actionName;
      }
    }
    return ProgramRunnerUtil.shortenName(configuration.getName(), 0); 
  }
  
  @NotNull
  public static String suggestRunActionName(@NotNull LocatableConfiguration configuration) {
    return suggestRunActionName((RunConfiguration)configuration);
  }

  @NotNull
  @Nls
  private static String childActionName(ConfigurationFromContext configurationFromContext) {
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

  protected abstract void updatePresentation(Presentation presentation, @NotNull String actionText, ConfigurationContext context);

}
