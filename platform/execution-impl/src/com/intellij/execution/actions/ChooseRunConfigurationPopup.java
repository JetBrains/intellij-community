// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.CommonBundle;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.ExecutorGroup;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.macro.MacroManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.popup.NumericMnemonicItem;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public final class ChooseRunConfigurationPopup implements ExecutorProvider {
  private final Project myProject;
  @NotNull private final String myAddKey;
  @NotNull private final Executor myDefaultExecutor;
  @Nullable private final Executor myAlternativeExecutor;

  private Executor myCurrentExecutor;
  private boolean myEditConfiguration;
  private final RunListPopup myPopup;

  public ChooseRunConfigurationPopup(@NotNull Project project,
                                     @NotNull String addKey,
                                     @NotNull Executor defaultExecutor,
                                     @Nullable Executor alternativeExecutor) {
    myProject = project;
    myAddKey = addKey;
    myDefaultExecutor = defaultExecutor;
    myAlternativeExecutor = alternativeExecutor;

    myPopup = new RunListPopup(project, null, new ConfigurationListPopupStep(this, myProject, this, myDefaultExecutor.getActionName()), null);
  }

  public void show() {

    final String adText = getAdText(myAlternativeExecutor);
    if (adText != null) {
      myPopup.setAdText(adText);
    }

    myPopup.showCenteredInCurrentWindow(myProject);
  }

  private @Nullable @Nls String getAdText(final Executor alternateExecutor) {
    final PropertiesComponent properties = PropertiesComponent.getInstance();
    if (alternateExecutor != null && !properties.isTrueValue(myAddKey)) {
      return ExecutionBundle.message("choose.run.configuration.popup.ad.text.hold",
                                     KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke("SHIFT")),
                                     alternateExecutor.getActionName());
    }

    if (!properties.isTrueValue("run.configuration.edit.ad")) {
      return ExecutionBundle.message("choose.run.configuration.popup.ad.text.edit",
                                     KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke("F4")));
    }

    if (!properties.isTrueValue("run.configuration.delete.ad")) {
      return ExecutionBundle.message("choose.run.configuration.popup.ad.text.delete",
                                     KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke("DELETE")));
    }

    return null;
  }

  private void registerActions(final RunListPopup popup) {
    popup.registerAction("alternateExecutor", KeyStroke.getKeyStroke("shift pressed SHIFT"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCurrentExecutor = myAlternativeExecutor;
        updatePresentation();
      }
    });

    popup.registerAction("restoreDefaultExecutor", KeyStroke.getKeyStroke("released SHIFT"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCurrentExecutor = myDefaultExecutor;
        updatePresentation();
      }
    });


    popup.registerAction("invokeAction", KeyStroke.getKeyStroke("shift ENTER"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        popup.handleSelect(true);
      }
    });

    popup.registerAction("editConfiguration", KeyStroke.getKeyStroke("F4"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myEditConfiguration = true;
        popup.handleSelect(true);
      }
    });

    popup.registerAction("deleteConfiguration", KeyStroke.getKeyStroke("DELETE"),
                         new AbstractAction() {
                           @Override
                           public void actionPerformed(ActionEvent e) {
                             popup.removeSelected();
                           }
                         });

    popup.registerAction("speedsearch_bksp", KeyStroke.getKeyStroke("BACK_SPACE"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        SpeedSearch speedSearch = popup.getSpeedSearch();
        if (speedSearch.isHoldingFilter()) {
          speedSearch.backspace();
          speedSearch.update();
        } else {
          popup.removeSelected();
        }
      }
    });

    for (int i = 0; i < 10; i++) {
      addNumberAction(popup, i);
    }
  }

  private void addNumberAction(RunListPopup popup, int number) {
    Action action = createNumberAction(number, popup, myDefaultExecutor);
    Action action_ = createNumberAction(number, popup, myAlternativeExecutor);
    popup.registerAction(number + "Action", KeyStroke.getKeyStroke(String.valueOf(number)), action);
    popup.registerAction(number + "Action_", KeyStroke.getKeyStroke("shift pressed " + number), action_);
    popup.registerAction(number + "Action1", KeyStroke.getKeyStroke("NUMPAD" + number), action);
    popup.registerAction(number + "Action_1", KeyStroke.getKeyStroke("shift pressed NUMPAD" + number), action_);
  }

  private void updatePresentation() {
    myPopup.setCaption(getExecutor().getActionName());
  }

  private static void execute(ItemWrapper<?> itemWrapper, @Nullable Executor executor) {
    if (executor == null) {
      return;
    }

    final DataContext dataContext = DataManager.getInstance().getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      itemWrapper.perform(project, executor, dataContext);
    }
  }

  private void editConfiguration(@NotNull Project project, @NotNull RunnerAndConfigurationSettings configuration) {
    final Executor executor = getExecutor();
    PropertiesComponent.getInstance().setValue("run.configuration.edit.ad", Boolean.toString(true));
    if (RunDialog.editConfiguration(project, configuration, ExecutionBundle.message("dialog.title.edit.configuration.settings"), executor)) {
      RunManager.getInstance(project).setSelectedConfiguration(configuration);
      ExecutorRegistryImpl.RunnerHelper.run(project, configuration.getConfiguration(), configuration, DataContext.EMPTY_CONTEXT, executor);
    }
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

                      @NotNull
                      @Override
                      public String getDoNotShowMessage() {
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

  @Override
  @NotNull
  public Executor getExecutor() {
    return myCurrentExecutor == null ? myDefaultExecutor : myCurrentExecutor;
  }

  private static Action createNumberAction(final int number, final ListPopupImpl listPopup, final Executor executor) {
    return new MyAbstractAction(listPopup, number, executor);
  }

  private abstract static class Wrapper implements NumericMnemonicItem {
    private int myMnemonic = -1;
    private boolean myMnemonicsEnabled;
    private final boolean myAddSeparatorAbove;
    private boolean myChecked;

    protected Wrapper(boolean addSeparatorAbove) {
      myAddSeparatorAbove = addSeparatorAbove;
    }

    public int getMnemonic() {
      return myMnemonic;
    }

    @Override
    public @Nullable Character getMnemonicChar() {
      return myMnemonic > -1 ? Character.forDigit(myMnemonic, 10) : null;
    }

    @Override
    public boolean digitMnemonicsEnabled() {
      return myMnemonicsEnabled;
    }

    public boolean isChecked() {
      return myChecked;
    }

    public void setChecked(boolean checked) {
      myChecked = checked;
    }

    public void setMnemonic(int mnemonic) {
      myMnemonic = mnemonic;
    }

    protected void setMnemonicsEnabled(boolean mnemonicsEnabled) {
      myMnemonicsEnabled = mnemonicsEnabled;
    }

    public boolean addSeparatorAbove() {
      return myAddSeparatorAbove;
    }

    @Nullable
    public abstract Icon getIcon();

    public abstract @NlsActions.ActionText String getText();

    public boolean canBeDeleted() {
      return false;
    }

    @Override
    public String toString() {
      return "Wrapper[" + getText() + "]";
    }
  }

  public abstract static class ItemWrapper<T> extends Wrapper {
    private final T myValue;
    private boolean myDynamic;


    protected ItemWrapper(@Nullable final T value) {
      this(value, false);
    }

    protected ItemWrapper(@Nullable final T value, boolean addSeparatorAbove) {
      super(addSeparatorAbove);
      myValue = value;
    }

    public @NlsActions.ActionText T getValue() {
      return myValue;
    }

    public boolean isDynamic() {
      return myDynamic;
    }

    public void setDynamic(final boolean b) {
      myDynamic = b;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ItemWrapper)) return false;

      if (!Objects.equals(myValue, ((ItemWrapper<?>)o).myValue)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return myValue != null ? myValue.hashCode() : 0;
    }

    public abstract void perform(@NotNull final Project project, @NotNull final Executor executor, @NotNull final DataContext context);

    @Nullable
    public ConfigurationType getType() {
      return null;
    }

    public boolean available(Executor executor) {
      return false;
    }

    public boolean hasActions() {
      return false;
    }

    public PopupStep getNextStep(Project project, ChooseRunConfigurationPopup action) {
      return PopupStep.FINAL_CHOICE;
    }

    public static ItemWrapper wrap(@NotNull final Project project,
                                   @NotNull final RunnerAndConfigurationSettings settings,
                                   final boolean dynamic) {
      final ItemWrapper result = wrap(project, settings);
      result.setDynamic(dynamic);
      return result;
    }

    public static ItemWrapper wrap(@NotNull final Project project, @NotNull final RunnerAndConfigurationSettings settings) {
      return new ItemWrapper<>(settings) {
        @Override
        public void perform(@NotNull Project project, @NotNull Executor executor, @NotNull DataContext context) {
          RunnerAndConfigurationSettings config = getValue();

          final RunManager manager = RunManager.getInstance(project);

          if (!manager.isRiderRunWidgetActive()) RunManager.getInstance(project).setSelectedConfiguration(config);
          MacroManager.getInstance().cacheMacrosPreview(context);
          ExecutorRegistryImpl.RunnerHelper.run(project, settings.getConfiguration(), settings, context, executor);
        }

        @Override
        public ConfigurationType getType() {
          return getValue().getType();
        }

        @Override
        public Icon getIcon() {
          return RunManagerEx.getInstanceEx(project).getConfigurationIcon(getValue(), true);
        }

        @Override
        public String getText() {
          return Executor.shortenNameIfNeeded(getValue().getName()) + getValue().getConfiguration().getPresentableType();
        }

        @Override
        public boolean hasActions() {
          return true;
        }

        @Override
        public boolean available(@NotNull Executor executor) {
          RunnerAndConfigurationSettings value = getValue();
          return value != null && ExecutorRegistryImpl.RunnerHelper.canRun(project, executor, settings.getConfiguration());
        }

        @Override
        public PopupStep getNextStep(@NotNull final Project project, @NotNull final ChooseRunConfigurationPopup action) {
          return new ConfigurationActionsStep(project, action, getValue(), isDynamic());
        }
      };
    }

    @Override
    public boolean canBeDeleted() {
      return !isDynamic() && getValue() instanceof RunnerAndConfigurationSettings;
    }
  }

  private static final class ConfigurationListPopupStep extends BaseListPopupStep<ItemWrapper> {
    private final Project myProject;
    private final ChooseRunConfigurationPopup myAction;
    private int myDefaultConfiguration = -1;

    private ConfigurationListPopupStep(@NotNull final ChooseRunConfigurationPopup action,
                                       @NotNull final Project project,
                                       @NotNull final ExecutorProvider executorProvider,
                                       @NotNull @Nls final String title) {
      super(title, createSettingsList(project, executorProvider, true));
      myProject = project;
      myAction = action;

      if (-1 == getDefaultOptionIndex()) {
        myDefaultConfiguration = getDynamicIndex();
      }

      boolean hasMnemonics = getValues().stream().anyMatch(wrapper -> wrapper.getMnemonic() != -1);
      if (hasMnemonics) getValues().forEach(wrapper -> wrapper.setMnemonicsEnabled(true));
    }

    private int getDynamicIndex() {
      int i = 0;
      for (final ItemWrapper wrapper : getValues()) {
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
    public ListSeparator getSeparatorAbove(ItemWrapper value) {
      if (value.addSeparatorAbove()) return new ListSeparator();

      final List<ItemWrapper> configurations = getValues();
      final int index = configurations.indexOf(value);
      if (index > 0 && index <= configurations.size() - 1) {
        final ItemWrapper aboveConfiguration = configurations.get(index - 1);

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
        .indexOf(ItemWrapper.wrap(myProject, currentConfiguration)) : -1;
    }

    @Override
    public PopupStep onChosen(final ItemWrapper wrapper, boolean finalChoice) {
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
    public boolean isFinal(ItemWrapper wrapper) {
      return myAction.myEditConfiguration
             || wrapper.available(myAction.getExecutor())
             || wrapper.getNextStep(myProject, myAction) == FINAL_CHOICE;
    }

    @Override
    public boolean hasSubstep(ItemWrapper selectedValue) {
      return selectedValue.hasActions();
    }

    @NotNull
    @Override
    public String getTextFor(ItemWrapper value) {
      //noinspection DialogTitleCapitalization
      return value.getText();
    }

    @Override
    public Icon getIconFor(ItemWrapper value) {
      return value.getIcon();
    }
  }

  private static final class ConfigurationActionsStep extends BaseListPopupStep<ActionWrapper> {

    @NotNull private final RunnerAndConfigurationSettings mySettings;
    @NotNull private final Project myProject;

    private ConfigurationActionsStep(@NotNull final Project project,
                                     ChooseRunConfigurationPopup action,
                                     @NotNull final RunnerAndConfigurationSettings settings, final boolean dynamic) {
      super(null, buildActions(project, action, settings, dynamic));
      myProject = project;
      mySettings = settings;
    }

    @NotNull
    public RunnerAndConfigurationSettings getSettings() {
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

    private static ActionWrapper[] buildActions(@NotNull final Project project,
                                                final ChooseRunConfigurationPopup action,
                                                @NotNull final RunnerAndConfigurationSettings settings,
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
      for (Executor executor: Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
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
    public PopupStep onChosen(final ActionWrapper selectedValue, boolean finalChoice) {
      return doFinalStep(() -> selectedValue.perform());
    }

    @Override
    public Icon getIconFor(ActionWrapper aValue) {
      return aValue.getIcon();
    }

    @NotNull
    @Override
    public String getTextFor(ActionWrapper value) {
      return value.getText();
    }
  }

  private abstract static class ActionWrapper extends Wrapper {
    private final @Nls String myName;
    private final Icon myIcon;

    private ActionWrapper(@Nls String name, Icon icon) {
      this(name, icon, false);
    }

    private ActionWrapper(@Nls String name, Icon icon, boolean addSeparatorAbove) {
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

  private static class MyAbstractAction extends AbstractAction implements DumbAware {
    private final ListPopupImpl myListPopup;
    private final int myNumber;
    private final Executor myExecutor;

    MyAbstractAction(ListPopupImpl listPopup, int number, Executor executor) {
      myListPopup = listPopup;
      myNumber = number;
      myExecutor = executor;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (myListPopup.getSpeedSearch().isHoldingFilter())
        return;
      for (final Object item : myListPopup.getListStep().getValues()) {
        if (item instanceof ItemWrapper && ((ItemWrapper<?>)item).getMnemonic() == myNumber) {
          myListPopup.setFinalRunnable(() -> execute((ItemWrapper)item, myExecutor));
          myListPopup.closeOk(null);
        }
      }
    }
  }

  private class RunListPopup extends ListPopupImpl {

    RunListPopup(Project project, WizardPopup aParent, ListPopupStep aStep, Object parentValue) {
      super(project, aParent, aStep, parentValue);
      registerActions(this);
    }

    @Override
    protected WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
      return new RunListPopup(getProject(), parent, (ListPopupStep)step, parentValue);
    }

    @Override
    public boolean shouldBeShowing(Object value) {
      if (super.shouldBeShowing(value)) {
        return true;
      }
      if (value instanceof FolderWrapper folderWrapper && mySpeedSearch.isHoldingFilter()) {
        for (RunnerAndConfigurationSettings configuration : folderWrapper.myConfigurations) {
          if (mySpeedSearch.shouldBeShowing(configuration.getName() + configuration.getConfiguration().getPresentableType()) ) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    protected ListCellRenderer<?> getListElementRenderer() {
      return new PopupListElementRenderer<>(this){
        @Override
        protected JComponent createIconBar() {
          JPanel res = new JPanel(new BorderLayout());
          res.setBorder(JBUI.Borders.emptyRight(JBUI.CurrentTheme.ActionsList.elementIconGap()));
          res.add(myMnemonicLabel, BorderLayout.WEST);
          res.add(myIconLabel, BorderLayout.CENTER);

          return res;
        }
      };
    }

    @Override
    public void handleSelect(boolean handleFinalChoices, InputEvent e) {
      if (e instanceof MouseEvent && e.isShiftDown()) {
        handleShiftClick(handleFinalChoices, e, this);
        return;
      }

      _handleSelect(handleFinalChoices, e);
    }

    private void _handleSelect(boolean handleFinalChoices, InputEvent e) {
      super.handleSelect(handleFinalChoices, e);
    }

    protected void handleShiftClick(boolean handleFinalChoices, final InputEvent inputEvent, final RunListPopup popup) {
      myCurrentExecutor = myAlternativeExecutor;
      popup._handleSelect(handleFinalChoices, inputEvent);
    }

    public void removeSelected() {
      final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
      if (!propertiesComponent.isTrueValue("run.configuration.delete.ad")) {
        propertiesComponent.setValue("run.configuration.delete.ad", Boolean.toString(true));
      }

      final int index = getSelectedIndex();
      if (index == -1) {
        return;
      }

      final Object o = getListModel().get(index);
      if (o instanceof ItemWrapper && ((ItemWrapper<?>)o).canBeDeleted()) {
        RunnerAndConfigurationSettings runConfig = (RunnerAndConfigurationSettings)((ItemWrapper<?>)o).getValue();
        deleteConfiguration(myProject, runConfig, ChooseRunConfigurationPopup.this.myPopup);
        getListModel().deleteItem(o);
        final List<Object> values = getListStep().getValues();
        values.remove(o);

        if (index < values.size()) {
          onChildSelectedFor(values.get(index));
        }
        else if (index - 1 >= 0) {
          onChildSelectedFor(values.get(index - 1));
        }
      }
    }

    @Override
    protected boolean isResizable() {
      return true;
    }
  }

  private static final class FolderWrapper extends ItemWrapper<String> {
    private final Project myProject;
    private final ExecutorProvider myExecutorProvider;
    private final List<? extends RunnerAndConfigurationSettings> myConfigurations;

    private FolderWrapper(Project project, ExecutorProvider executorProvider, @Nullable String value, List<? extends RunnerAndConfigurationSettings> configurations) {
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
        ExecutionUtil.runConfiguration(selectedConfiguration, myExecutorProvider.getExecutor());
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

    @Nullable
    @Override
    public ConfigurationType getType() {
      return Registry.is("run.popup.move.folders.to.top") || myConfigurations.isEmpty()
             ? null
             : myConfigurations.get(0).getType();
    }

    @Override
    public boolean hasActions() {
      return true;
    }

    @Override
    public PopupStep getNextStep(Project project, ChooseRunConfigurationPopup action) {
      List<ConfigurationActionsStep> steps = new ArrayList<>();
      for (RunnerAndConfigurationSettings settings : myConfigurations) {
        steps.add(new ConfigurationActionsStep(project, action, settings, false));
      }
      return new FolderStep(myProject, myExecutorProvider, null, steps, action);
    }
  }

  private static final class FolderStep extends BaseListPopupStep<ConfigurationActionsStep> {
    private final Project myProject;
    private final ChooseRunConfigurationPopup myPopup;
    private final ExecutorProvider myExecutorProvider;

    private FolderStep(Project project, ExecutorProvider executorProvider, @NlsSafe String folderName, List<ConfigurationActionsStep> children,
                       ChooseRunConfigurationPopup popup) {
      super(folderName, children, new ArrayList<>());
      myProject = project;
      myExecutorProvider = executorProvider;
      myPopup = popup;
    }

    @Override
    public PopupStep onChosen(final ConfigurationActionsStep selectedValue, boolean finalChoice) {
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
      } else {
        return selectedValue;
      }
    }

    @Override
    public Icon getIconFor(ConfigurationActionsStep aValue) {
      return aValue.getIcon();
    }

    @NotNull
    @Override
    public String getTextFor(ConfigurationActionsStep value) {
      return value.getName();
    }

    @Override
    public boolean hasSubstep(ConfigurationActionsStep selectedValue) {
      return !selectedValue.getValues().isEmpty();
    }
  }

  @NotNull
  public static List<ItemWrapper> createSettingsList(@NotNull Project project,
                                                     @NotNull ExecutorProvider executorProvider,
                                                     boolean isCreateEditAction) {
    //noinspection TestOnlyProblems
    return createSettingsList(RunManagerImpl.getInstanceImpl(project), executorProvider, isCreateEditAction, Registry.is("run.popup.move.folders.to.top", false));
  }

  public static List<ItemWrapper> createFlatSettingsList(@NotNull Project project) {
    return RunManagerImpl.getInstanceImpl(project).getConfigurationsGroupedByTypeAndFolder(false)
      .values()
      .stream()
      .flatMap(map -> map.values().stream().flatMap(settings -> settings.stream()))
      .map(settings -> ItemWrapper.wrap(project, settings))
      .collect(Collectors.toList());
  }

  @TestOnly
  @NotNull
  public static List<ItemWrapper> createSettingsList(@NotNull RunManagerImpl runManager,
                                                     @NotNull ExecutorProvider executorProvider,
                                                     boolean isCreateEditAction,
                                                     boolean isMoveFoldersToTop) {
    List<ItemWrapper> result = new ArrayList<>();

    if (isCreateEditAction) {
      result.add(createEditAction());
    }

    Project project = runManager.getProject();
    final RunnerAndConfigurationSettings selectedConfiguration = runManager.getSelectedConfiguration();
    if (selectedConfiguration != null) {
      addActionsForSelected(selectedConfiguration, project, result);
    }

    Map<RunnerAndConfigurationSettings, ItemWrapper> wrappedExisting = new LinkedHashMap<>();
    List<FolderWrapper> folderWrappers = new SmartList<>();
    for (Map<String, List<RunnerAndConfigurationSettings>> folderToConfigurations : runManager.getConfigurationsGroupedByTypeAndFolder(false).values()) {
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
      populateWithDynamicRunners(result, wrappedExisting, project, RunManagerEx.getInstanceEx(project), selectedConfiguration);
    }
    if (isMoveFoldersToTop) {
      result.addAll(wrappedExisting.values());
    }
    return result;
  }

  @NotNull
  private static ItemWrapper wrapAndAdd(@NotNull Project project,
                                        @NotNull RunnerAndConfigurationSettings configuration,
                                        @Nullable RunnerAndConfigurationSettings selectedConfiguration,
                                        @NotNull Map<RunnerAndConfigurationSettings, ItemWrapper> wrappedExisting) {
    ItemWrapper wrapped = ItemWrapper.wrap(project, configuration);
    if (configuration == selectedConfiguration) {
      wrapped.setMnemonic(1);
    }
    wrappedExisting.put(configuration, wrapped);
    return wrapped;
  }

  @NotNull
  private static FolderWrapper createFolderItem(@NotNull Project project,
                                                @NotNull ExecutorProvider executorProvider,
                                                @Nullable RunnerAndConfigurationSettings selectedConfiguration,
                                                @NotNull String folderName,
                                                @NotNull List<? extends RunnerAndConfigurationSettings> configurations) {
    boolean isSelected = selectedConfiguration != null && configurations.contains(selectedConfiguration);
    String value = folderName;
    if (isSelected) {
      value += "  (mnemonic is to \"" + selectedConfiguration.getName() + "\")";
    }
    FolderWrapper result = new FolderWrapper(project, executorProvider, value, configurations);
    if (isSelected) {
      result.setMnemonic(1);
    }
    return result;
  }

  private static void addActionsForSelected(@NotNull RunnerAndConfigurationSettings selectedConfiguration, @NotNull Project project, @NotNull List<? super ItemWrapper> result) {
    boolean isFirst = true;
    final ExecutionTarget activeTarget = ExecutionTargetManager.getActiveTarget(project);
    for (ExecutionTarget eachTarget : ExecutionTargetManager.getTargetsToChooseFor(project, selectedConfiguration.getConfiguration())) {
      ItemWrapper<ExecutionTarget> itemWrapper = new ItemWrapper<>(eachTarget, isFirst) {
        @Override
        public Icon getIcon() {
          return getValue().getIcon();
        }

        @Override
        public String getText() {
          return getValue().getDisplayName();
        }

        @Override
        public void perform(@NotNull final Project project, @NotNull final Executor executor, @NotNull DataContext context) {
          ExecutionTargetManager.setActiveTarget(project, getValue());
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

  @NotNull
  private static ItemWrapper<Void> createEditAction() {
    ItemWrapper<Void> result = new ItemWrapper<>(null) {
      @Override
      public Icon getIcon() {
        return AllIcons.Actions.EditSource;
      }

      @Override
      public String getText() {
        return UIUtil.removeMnemonic(ActionsBundle.message("action.editRunConfigurations.text"));
      }

      @Override
      public void perform(@NotNull final Project project, @NotNull final Executor executor, @NotNull DataContext context) {
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

  private static void populateWithDynamicRunners(final List<? super ItemWrapper> result,
                                                 Map<RunnerAndConfigurationSettings, ItemWrapper> existing,
                                                 final Project project, final RunManager manager,
                                                 final RunnerAndConfigurationSettings selectedConfiguration) {
    if (!EventQueue.isDispatchThread()) {
      return;
    }

    final DataContext dataContext = DataManager.getInstance().getDataContext();
    final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);

    final List<ConfigurationFromContext> producers = PreferredProducerFind.getConfigurationsFromContext(context.getLocation(),
                                                                                                        context, false, true);
    if (producers == null) return;

    producers.sort(ConfigurationFromContext.NAME_COMPARATOR);

    final RunnerAndConfigurationSettings[] preferred = {null};

    int i = 2; // selectedConfiguration == null ? 1 : 2;
    for (final ConfigurationFromContext fromContext : producers) {
      final RunnerAndConfigurationSettings configuration = fromContext.getConfigurationSettings();
      if (existing.containsKey(configuration)) {
        final ItemWrapper wrapper = existing.get(configuration);
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

        //noinspection unchecked
        final ItemWrapper wrapper = new ItemWrapper(configuration) {
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
          public PopupStep getNextStep(@NotNull final Project project, @NotNull final ChooseRunConfigurationPopup action) {
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
}
