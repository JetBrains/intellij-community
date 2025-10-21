// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.CommonBundle;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.ExecutorGroup;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunManagerImplKt;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;

import static java.util.Objects.requireNonNull;

public final class ChooseRunConfigurationManager {

  public static @NotNull List<ChooseRunConfigurationPopup.ItemWrapper<?>> createSettingsList(@NotNull Project project,
                                                                                             @NotNull ExecutorProvider executorProvider,
                                                                                             @NotNull DataContext dataContext,
                                                                                             boolean isCreateEditAction) {
    RunManager runManager = RunManager.getInstanceIfCreated(project);
    if (runManager == null) {
      return Collections.emptyList();
    }
    //noinspection TestOnlyProblems
    return createSettingsList((RunManagerImpl)runManager, executorProvider, isCreateEditAction,
                              Registry.is("run.popup.move.folders.to.top", false), dataContext);
  }

  public static List<ChooseRunConfigurationPopup.ItemWrapper<?>> createFlatSettingsList(@NotNull Project project) {
    return RunManagerImplKt.createFlatSettingsList(project);
  }

  @TestOnly
  public static @NotNull List<ChooseRunConfigurationPopup.ItemWrapper<?>> createSettingsList(@NotNull RunManagerImpl runManager,
                                                                                             @NotNull ExecutorProvider executorProvider,
                                                                                             boolean isCreateEditAction,
                                                                                             boolean isMoveFoldersToTop,
                                                                                             @NotNull DataContext dataContext) {
    List<ChooseRunConfigurationPopup.ItemWrapper<?>> result = new ArrayList<>();

    if (isCreateEditAction) {
      result.add(createEditAction());
    }

    Project project = runManager.getProject();
    final RunnerAndConfigurationSettings selectedConfiguration = runManager.getSelectedConfiguration();
    if (selectedConfiguration != null) {
      addActionsForSelected(selectedConfiguration, project, result);
    }

    Map<RunnerAndConfigurationSettings, ChooseRunConfigurationPopup.ItemWrapper<?>> wrappedExisting = new LinkedHashMap<>();
    List<FolderWrapper> folderWrappers = new SmartList<>();
    for (Map<String, List<RunnerAndConfigurationSettings>> folderToConfigurations : runManager.getConfigurationsGroupedByTypeAndFolder(
      false).values()) {
      if (isMoveFoldersToTop) {
        for (Map.Entry<String, List<RunnerAndConfigurationSettings>> entry : folderToConfigurations.entrySet()) {
          final String folderName = entry.getKey();
          List<RunnerAndConfigurationSettings> configurations = entry.getValue();
          if (folderName != null) {
            folderWrappers.add(createFolderItem(project, executorProvider, selectedConfiguration, folderName, configurations));
          }
          else {
            for (RunnerAndConfigurationSettings configuration : configurations) {
              wrapAndAdd(project, configuration, selectedConfiguration, wrappedExisting);
            }
          }
        }
      }
      else {
        // add only folders
        for (Map.Entry<String, List<RunnerAndConfigurationSettings>> entry : folderToConfigurations.entrySet()) {
          final String folderName = entry.getKey();
          if (folderName != null) {
            result.add(createFolderItem(project, executorProvider, selectedConfiguration, folderName, entry.getValue()));
          }
        }

        // add configurations
        List<RunnerAndConfigurationSettings> configurations = folderToConfigurations.get(null);
        if (!ContainerUtil.isEmpty(configurations)) {
          for (RunnerAndConfigurationSettings configuration : configurations) {
            result.add(wrapAndAdd(project, configuration, selectedConfiguration, wrappedExisting));
          }
        }
      }
    }

    if (isMoveFoldersToTop) {
      result.addAll(folderWrappers);
    }
    if (!DumbService.isDumb(project)) {
      populateWithDynamicRunners(result, wrappedExisting, project, RunManagerEx.getInstanceEx(project), selectedConfiguration, dataContext);
    }
    if (isMoveFoldersToTop) {
      result.addAll(wrappedExisting.values());
    }
    return result;
  }

  public static void deleteConfiguration(@NotNull Project project,
                                         @NotNull RunnerAndConfigurationSettings configurationSettings,
                                         @Nullable JBPopup popupToCancel) {
    RunManagerConfig runManagerConfig = RunManagerImpl.getInstanceImpl(project).getConfig();
    boolean confirmed;
    if (runManagerConfig.isDeletionFromPopupRequiresConfirmation()) {
      if (popupToCancel != null) {
        popupToCancel.cancel();
      }

      confirmed = MessageDialogBuilder.yesNo(CommonBundle.message("title.confirmation"),
                                             ExecutionBundle.message("are.you.sure.you.want.to.delete.0", configurationSettings.getName()))
        .doNotAsk(new DoNotAskOption.Adapter() {
          @Override
          public void rememberChoice(boolean isSelected, int exitCode) {
            runManagerConfig.setDeletionFromPopupRequiresConfirmation(!isSelected);
          }

          @Override
          public @NotNull String getDoNotShowMessage() {
            return ExecutionBundle.message("don.t.ask.again");
          }

          @Override
          public boolean shouldSaveOptionsOnCancel() {
            return true;
          }
        })
        .ask(project);
    }
    else {
      confirmed = true;
    }
    if (confirmed) {
      RunManager.getInstance(project).removeConfiguration(configurationSettings);
    }
  }

  private static @NotNull ChooseRunConfigurationPopup.ItemWrapper<?> wrapAndAdd(@NotNull Project project,
                                                                                @NotNull RunnerAndConfigurationSettings configuration,
                                                                                @Nullable RunnerAndConfigurationSettings selectedConfiguration,
                                                                                @NotNull Map<RunnerAndConfigurationSettings, ChooseRunConfigurationPopup.ItemWrapper<?>> wrappedExisting) {
    ChooseRunConfigurationPopup.ItemWrapper<?> wrapped = ChooseRunConfigurationPopup.ItemWrapper.wrap(project, configuration);
    if (configuration == selectedConfiguration) {
      wrapped.setMnemonic(1);
    }
    wrappedExisting.put(configuration, wrapped);
    return wrapped;
  }

  private static @NotNull FolderWrapper createFolderItem(@NotNull Project project,
                                                         @NotNull ExecutorProvider executorProvider,
                                                         @Nullable RunnerAndConfigurationSettings selectedConfiguration,
                                                         @NotNull String folderName,
                                                         @NotNull List<? extends RunnerAndConfigurationSettings> configurations) {
    boolean isSelected = selectedConfiguration != null && configurations.contains(selectedConfiguration);
    String value = folderName;
    if (isSelected) {
      value += "  (mnemonic is to \"" + selectedConfiguration.getName() + "\")";
    }
    FolderWrapper
      result = new FolderWrapper(project, executorProvider, value, configurations);
    if (isSelected) {
      result.setMnemonic(1);
    }
    return result;
  }

  private static void addActionsForSelected(@NotNull RunnerAndConfigurationSettings selectedConfiguration,
                                            @NotNull Project project,
                                            @NotNull List<ChooseRunConfigurationPopup.ItemWrapper<?>> result) {
    boolean isFirst = true;
    final ExecutionTarget activeTarget = ExecutionTargetManager.getActiveTarget(project);
    for (ExecutionTarget eachTarget : ExecutionTargetManager.getTargetsToChooseFor(project, selectedConfiguration.getConfiguration())) {
      ChooseRunConfigurationPopup.ItemWrapper<ExecutionTarget>
        itemWrapper = new ChooseRunConfigurationPopup.ItemWrapper<>(eachTarget, isFirst) {
        @Override
        public Icon getIcon() {
          return requireNonNull(getValue()).getIcon();
        }

        @Override
        public String getText() {
          return requireNonNull(getValue()).getDisplayName();
        }

        @Override
        public void perform(final @NotNull Project project, final @NotNull Executor executor, @NotNull DataContext context) {
          ExecutionTargetManager.setActiveTarget(project, requireNonNull(getValue()));
          ExecutionUtil.doRunConfiguration(selectedConfiguration, executor, null, null, context);
        }

        @Override
        public boolean available(Executor executor) {
          return true;
        }
      };
      itemWrapper.setChecked(eachTarget.equals(activeTarget));
      result.add(itemWrapper);
      isFirst = false;
    }
  }

  private static @NotNull ChooseRunConfigurationPopup.ItemWrapper<Void> createEditAction() {
    ChooseRunConfigurationPopup.ItemWrapper<Void> result = new ChooseRunConfigurationPopup.ItemWrapper<>(null) {
      @Override
      public Icon getIcon() {
        return AllIcons.Actions.EditSource;
      }

      @Override
      public String getText() {
        return UIUtil.removeMnemonic(ActionsBundle.message("action.editRunConfigurations.text"));
      }

      @Override
      public void perform(final @NotNull Project project, final @NotNull Executor executor, @NotNull DataContext context) {
        if (new EditConfigurationsDialog(project) {
          @Override
          protected void init() {
            setOKButtonText(executor.getActionName());
            myExecutor = executor;
            super.init();
          }
        }.showAndGet()) {
          ApplicationManager.getApplication().invokeLater(() -> {
            RunnerAndConfigurationSettings configuration = RunManager.getInstance(project).getSelectedConfiguration();
            if (configuration != null) {
              ExecutionUtil.doRunConfiguration(configuration, executor, null, null, context);
            }
          }, project.getDisposed());
        }
      }

      @Override
      public boolean available(Executor executor) {
        return true;
      }
    };
    result.setMnemonic(0);
    return result;
  }

  private static void populateWithDynamicRunners(List<ChooseRunConfigurationPopup.ItemWrapper<?>> result,
                                                 Map<RunnerAndConfigurationSettings, ChooseRunConfigurationPopup.ItemWrapper<?>> existing,
                                                 Project project,
                                                 RunManager manager,
                                                 RunnerAndConfigurationSettings selectedConfiguration,
                                                 @NotNull DataContext dataContext) {
    final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);

    List<ConfigurationFromContext> producers = PreferredProducerFind.getConfigurationsFromContext(context.getLocation(),
                                                                                                  context, false, true);
    if (producers == null) return;

    producers = ContainerUtil.sorted(producers, ConfigurationFromContext.NAME_COMPARATOR);

    final RunnerAndConfigurationSettings[] preferred = {null};

    int i = 2; // selectedConfiguration == null ? 1 : 2;
    for (final ConfigurationFromContext fromContext : producers) {
      final RunnerAndConfigurationSettings configuration = fromContext.getConfigurationSettings();
      if (existing.containsKey(configuration)) {
        ChooseRunConfigurationPopup.ItemWrapper<?> wrapper = existing.get(configuration);
        if (wrapper.getMnemonic() != 1) {
          wrapper.setMnemonic(i);
          i++;
        }
      }
      else {
        if (selectedConfiguration != null && configuration.equals(selectedConfiguration)) continue;

        if (preferred[0] == null) {
          preferred[0] = configuration;
        }

        ChooseRunConfigurationPopup.ItemWrapper<?> wrapper = new ChooseRunConfigurationPopup.ItemWrapper<Object>(configuration) {
          @Override
          public Icon getIcon() {
            return RunManagerEx.getInstanceEx(project).getConfigurationIcon(configuration);
          }

          @Override
          public String getText() {
            return Executor.shortenNameIfNeeded(configuration.getName()) + configuration.getConfiguration().getPresentableType();
          }

          @Override
          public boolean available(Executor executor) {
            return ProgramRunner.getRunner(executor.getId(), configuration.getConfiguration()) != null;
          }

          @Override
          public void perform(@NotNull Project project, @NotNull Executor executor, @NotNull DataContext context) {
            manager.setTemporaryConfiguration(configuration);
            RunManager.getInstance(project).setSelectedConfiguration(configuration);
            ExecutionUtil.doRunConfiguration(configuration, executor, null, null, context);
          }

          @Override
          public PopupStep<?> getNextStep(@NotNull Project project, final @NotNull ChooseRunConfigurationPopup action) {
            return new ConfigurationActionsStep(project, action, configuration, isDynamic());
          }

          @Override
          public boolean hasActions() {
            return true;
          }
        };

        wrapper.setDynamic(true);
        wrapper.setMnemonic(i);
        result.add(wrapper);
        i++;
      }
    }
  }

  static final class FolderWrapper extends ChooseRunConfigurationPopup.ItemWrapper<String> {
    private final Project myProject;
    private final ExecutorProvider myExecutorProvider;
    final List<? extends RunnerAndConfigurationSettings> myConfigurations;

    private FolderWrapper(Project project,
                          ExecutorProvider executorProvider,
                          @Nullable String value,
                          List<? extends RunnerAndConfigurationSettings> configurations) {
      super(value);
      myProject = project;
      myExecutorProvider = executorProvider;
      myConfigurations = configurations;
    }

    @Override
    public void perform(@NotNull Project project, @NotNull Executor executor, @NotNull DataContext context) {
      RunManager runManager = RunManager.getInstance(project);
      RunnerAndConfigurationSettings selectedConfiguration = runManager.getSelectedConfiguration();
      if (myConfigurations.contains(selectedConfiguration)) {
        runManager.setSelectedConfiguration(selectedConfiguration);
        ExecutionUtil.runConfiguration(requireNonNull(selectedConfiguration), myExecutorProvider.getExecutor());
      }
    }

    @Override
    public @NotNull Icon getIcon() {
      return AllIcons.Nodes.Folder;
    }

    @Override
    public String getText() {
      return getValue();
    }

    @Override
    public @Nullable ConfigurationType getType() {
      return Registry.is("run.popup.move.folders.to.top") || myConfigurations.isEmpty()
             ? null
             : myConfigurations.get(0).getType();
    }

    @Override
    public boolean hasActions() {
      return true;
    }

    @Override
    public PopupStep<?> getNextStep(Project project, ChooseRunConfigurationPopup action) {
      List<ConfigurationActionsStep> steps = new ArrayList<>();
      for (RunnerAndConfigurationSettings settings : myConfigurations) {
        steps.add(new ConfigurationActionsStep(project, action, settings, false));
      }
      return new FolderStep(myProject, myExecutorProvider, null, steps, action);
    }
  }

  static final class FolderStep extends BaseListPopupStep<ConfigurationActionsStep> {
    private final Project myProject;
    private final ChooseRunConfigurationPopup myPopup;
    private final ExecutorProvider myExecutorProvider;

    private FolderStep(Project project,
                       ExecutorProvider executorProvider,
                       @NlsSafe String folderName,
                       List<ConfigurationActionsStep> children,
                       ChooseRunConfigurationPopup popup) {
      super(folderName, children, new ArrayList<>());
      myProject = project;
      myExecutorProvider = executorProvider;
      myPopup = popup;
    }

    @Override
    public PopupStep<?> onChosen(ConfigurationActionsStep selectedValue, boolean finalChoice) {
      if (finalChoice) {
        if (myPopup.myEditConfiguration) {
          final RunnerAndConfigurationSettings settings = selectedValue.getSettings();
          return doFinalStep(() -> myPopup.editConfiguration(myProject, settings));
        }

        return doFinalStep(() -> {
          RunnerAndConfigurationSettings settings = selectedValue.getSettings();
          RunManager.getInstance(myProject).setSelectedConfiguration(settings);
          ExecutionUtil.runConfiguration(settings, myExecutorProvider.getExecutor());
        });
      }
      else {
        return selectedValue;
      }
    }

    @Override
    public Icon getIconFor(ConfigurationActionsStep aValue) {
      return aValue.getIcon();
    }

    @Override
    public @NotNull String getTextFor(ConfigurationActionsStep value) {
      return value.getName();
    }

    @Override
    public boolean hasSubstep(ConfigurationActionsStep selectedValue) {
      return !selectedValue.getValues().isEmpty();
    }
  }

  static final class ConfigurationListPopupStep extends BaseListPopupStep<ChooseRunConfigurationPopup.ItemWrapper<?>> {
    private final Project myProject;
    private final ChooseRunConfigurationPopup myAction;
    private int myDefaultConfiguration = -1;

    ConfigurationListPopupStep(final @NotNull ChooseRunConfigurationPopup action,
                               final @NotNull Project project,
                               final @NotNull @Nls String title,
                               @NotNull List<ChooseRunConfigurationPopup.ItemWrapper<?>> list) {
      super(title, list);
      myProject = project;
      myAction = action;

      if (-1 == getDefaultOptionIndex()) {
        myDefaultConfiguration = getDynamicIndex();
      }

      boolean hasMnemonics = ContainerUtil.exists(getValues(), wrapper -> wrapper.getMnemonic() != -1);
      if (hasMnemonics) getValues().forEach(wrapper -> wrapper.setMnemonicsEnabled(true));
    }

    private int getDynamicIndex() {
      int i = 0;
      for (ChooseRunConfigurationPopup.ItemWrapper<?> wrapper : getValues()) {
        if (wrapper.isDynamic()) {
          return i;
        }
        i++;
      }

      return -1;
    }

    @Override
    public boolean isAutoSelectionEnabled() {
      return false;
    }

    @Override
    public ListSeparator getSeparatorAbove(ChooseRunConfigurationPopup.ItemWrapper value) {
      if (value.addSeparatorAbove()) return new ListSeparator();

      List<ChooseRunConfigurationPopup.ItemWrapper<?>> configurations = getValues();
      final int index = configurations.indexOf(value);
      if (index > 0 && index <= configurations.size() - 1) {
        ChooseRunConfigurationPopup.ItemWrapper<?> aboveConfiguration = configurations.get(index - 1);

        if (aboveConfiguration != null && aboveConfiguration.isDynamic() != value.isDynamic()) {
          return new ListSeparator();
        }

        final ConfigurationType currentType = value.getType();
        final ConfigurationType aboveType = aboveConfiguration == null ? null : aboveConfiguration.getType();
        if (aboveType != currentType && currentType != null) {
          return new ListSeparator(); // new ListSeparator(currentType.getDisplayName());
        }
      }

      return null;
    }

    @Override
    public boolean isSpeedSearchEnabled() {
      return true;
    }

    @Override
    public int getDefaultOptionIndex() {
      final RunnerAndConfigurationSettings currentConfiguration = RunManager.getInstance(myProject).getSelectedConfiguration();
      if (currentConfiguration == null && myDefaultConfiguration != -1) {
        return myDefaultConfiguration;
      }

      return currentConfiguration instanceof RunnerAndConfigurationSettingsImpl ? getValues()
        .indexOf(ChooseRunConfigurationPopup.ItemWrapper.wrap(myProject, currentConfiguration)) : -1;
    }

    @Override
    public PopupStep<?> onChosen(ChooseRunConfigurationPopup.ItemWrapper wrapper, boolean finalChoice) {
      if (myAction.myEditConfiguration) {
        final Object o = wrapper.getValue();
        if (o instanceof RunnerAndConfigurationSettingsImpl) {
          return doFinalStep(() -> myAction.editConfiguration(myProject, (RunnerAndConfigurationSettings)o));
        }
      }

      if (finalChoice && wrapper.available(myAction.getExecutor())) {
        return doFinalStep(() -> {
          if (myAction.getExecutor() == myAction.myAlternativeExecutor) {
            PropertiesComponent.getInstance().setValue(myAction.myAddKey, Boolean.toString(true));
          }

          wrapper.perform(myProject, myAction.getExecutor(), DataManager.getInstance().getDataContext());
        });
      }
      else {
        return wrapper.getNextStep(myProject, myAction);
      }
    }

    @Override
    public boolean isFinal(ChooseRunConfigurationPopup.ItemWrapper wrapper) {
      return myAction.myEditConfiguration
             || wrapper.available(myAction.getExecutor())
             || wrapper.getNextStep(myProject, myAction) == FINAL_CHOICE;
    }

    @Override
    public boolean hasSubstep(ChooseRunConfigurationPopup.ItemWrapper selectedValue) {
      return selectedValue.hasActions();
    }

    @Override
    public @NotNull String getTextFor(ChooseRunConfigurationPopup.ItemWrapper value) {
      //noinspection DialogTitleCapitalization
      return value.getText();
    }

    @Override
    public Icon getIconFor(ChooseRunConfigurationPopup.ItemWrapper value) {
      return value.getIcon();
    }
  }

  static final class ConfigurationActionsStep extends BaseListPopupStep<ActionWrapper> {

    private final @NotNull RunnerAndConfigurationSettings mySettings;
    private final @NotNull Project myProject;

    ConfigurationActionsStep(final @NotNull Project project,
                             ChooseRunConfigurationPopup action,
                             final @NotNull RunnerAndConfigurationSettings settings, final boolean dynamic) {
      super(null, buildActions(project, action, settings, dynamic));
      myProject = project;
      mySettings = settings;
    }

    public @NotNull RunnerAndConfigurationSettings getSettings() {
      return mySettings;
    }

    public @NlsContexts.ListItem String getName() {
      return Executor.shortenNameIfNeeded(mySettings.getName());
    }

    public Icon getIcon() {
      return RunManagerEx.getInstanceEx(myProject).getConfigurationIcon(mySettings);
    }

    @Override
    public ListSeparator getSeparatorAbove(ActionWrapper value) {
      return value.addSeparatorAbove() ? new ListSeparator() : null;
    }

    private static ActionWrapper[] buildActions(final @NotNull Project project,
                                                final ChooseRunConfigurationPopup action,
                                                final @NotNull RunnerAndConfigurationSettings settings,
                                                final boolean dynamic) {
      final List<ActionWrapper> result = new ArrayList<>();

      final ExecutionTarget active = ExecutionTargetManager.getActiveTarget(project);
      for (final ExecutionTarget eachTarget : ExecutionTargetManager.getTargetsToChooseFor(project, settings.getConfiguration())) {
        result.add(new ActionWrapper(eachTarget.getDisplayName(), eachTarget.getIcon()) {
          {
            setChecked(eachTarget.equals(active));
          }

          @Override
          public void perform() {
            final RunManager manager = RunManager.getInstance(project);
            if (dynamic) {
              manager.setTemporaryConfiguration(settings);
            }
            manager.setSelectedConfiguration(settings);

            ExecutionTargetManager.setActiveTarget(project, eachTarget);
            ExecutionUtil.runConfiguration(settings, action.getExecutor());
          }
        });
      }

      boolean isFirst = true;
      final List<Executor> allExecutors = new ArrayList<>();
      for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
        if (executor instanceof ExecutorGroup) {
          allExecutors.addAll(((ExecutorGroup<?>)executor).childExecutors());
        }
        else {
          allExecutors.add(executor);
        }
      }
      for (final Executor executor : allExecutors) {
        if (ExecutorRegistryImpl.RunnerHelper.canRun(project, executor, settings.getConfiguration())) {
          result.add(new ActionWrapper(executor.getActionName(), executor.getIcon(), isFirst) {
            @Override
            public void perform() {
              final RunManager manager = RunManager.getInstance(project);
              if (dynamic) {
                manager.setTemporaryConfiguration(settings);
              }
              if (!manager.isRiderRunWidgetActive()) manager.setSelectedConfiguration(settings);
              ExecutorRegistryImpl.RunnerHelper.run(project, settings.getConfiguration(), settings, DataContext.EMPTY_CONTEXT, executor);
            }
          });
          isFirst = false;
        }
      }

      result.add(new ActionWrapper(ExecutionBundle.message("choose.run.popup.edit"), AllIcons.Actions.EditSource, true) {
        @Override
        public void perform() {
          if (dynamic) {
            RunManager.getInstance(project).setTemporaryConfiguration(settings);
          }
          action.editConfiguration(project, settings);
        }
      });

      if (settings.isTemporary() || dynamic) {
        result.add(new ActionWrapper(ExecutionBundle.message("choose.run.popup.save"), AllIcons.Actions.MenuSaveall) {
          @Override
          public void perform() {
            final RunManager manager = RunManager.getInstance(project);
            if (dynamic) {
              manager.setTemporaryConfiguration(settings);
            }
            manager.makeStable(settings);
          }
        });
      }
      result.add(new ActionWrapper(ExecutionBundle.message("choose.run.popup.delete"), AllIcons.Actions.Cancel) {
        @Override
        public void perform() {
          deleteConfiguration(project, settings, action.myPopup);
        }
      });

      return result.toArray(new ActionWrapper[0]);
    }

    @Override
    public PopupStep<?> onChosen(ActionWrapper selectedValue, boolean finalChoice) {
      return doFinalStep(() -> selectedValue.perform());
    }

    @Override
    public Icon getIconFor(ActionWrapper aValue) {
      return aValue.getIcon();
    }

    @Override
    public @NotNull String getTextFor(ActionWrapper value) {
      return value.getText();
    }
  }

  abstract static class ActionWrapper extends ChooseRunConfigurationPopup.Wrapper {
    private final @Nls String myName;
    private final Icon myIcon;

    ActionWrapper(@Nls String name, Icon icon) {
      this(name, icon, false);
    }

    ActionWrapper(@Nls String name, Icon icon, boolean addSeparatorAbove) {
      super(addSeparatorAbove);
      myName = name;
      myIcon = icon;
    }

    public abstract void perform();

    @Override
    public String getText() {
      return myName;
    }

    @Override
    public Icon getIcon() {
      return myIcon;
    }
  }
}
