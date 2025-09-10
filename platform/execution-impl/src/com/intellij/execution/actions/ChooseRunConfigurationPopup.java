// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.actions.ChooseRunConfigurationManager.ConfigurationListPopupStep;
import com.intellij.execution.actions.ChooseRunConfigurationManager.FolderWrapper;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.NlsActions;
import com.intellij.ui.popup.NumericMnemonicItem;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Use {@link ChooseRunConfigurationManager} instead.
 */
@ApiStatus.Internal
public final class ChooseRunConfigurationPopup implements ExecutorProvider {
  private final Project myProject;
  final @NotNull String myAddKey;
  private final @NotNull Executor myDefaultExecutor;
  final @Nullable Executor myAlternativeExecutor;

  private Executor myCurrentExecutor;
  boolean myEditConfiguration;
  RunListPopup myPopup;

  public ChooseRunConfigurationPopup(@NotNull Project project,
                                     @NotNull String addKey,
                                     @NotNull Executor defaultExecutor,
                                     @Nullable Executor alternativeExecutor) {
    myProject = project;
    myAddKey = addKey;
    myDefaultExecutor = defaultExecutor;
    myAlternativeExecutor = alternativeExecutor;
  }

  @RequiresBackgroundThread
  @NotNull ListPopupStep<?> buildStep(@NotNull DataContext dataContext) {
    List<ChooseRunConfigurationPopup.ItemWrapper<?>> settingsList = ChooseRunConfigurationManager.createSettingsList(myProject, this, dataContext, true);
    return new ConfigurationListPopupStep(this, myProject, myDefaultExecutor.getActionName(), settingsList);
  }

  void show(@NotNull ListPopupStep<?> step) {
    myPopup = new RunListPopup(myProject, null, step, null);
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

    //noinspection SpellCheckingInspection
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

  void editConfiguration(@NotNull Project project, @NotNull RunnerAndConfigurationSettings configuration) {
    final Executor executor = getExecutor();
    PropertiesComponent.getInstance().setValue("run.configuration.edit.ad", Boolean.toString(true));
    if (RunDialog.editConfiguration(project, configuration, ExecutionBundle.message("dialog.title.edit.configuration.settings"), executor)) {
      RunManager.getInstance(project).setSelectedConfiguration(configuration);
      ExecutorRegistryImpl.RunnerHelper.run(project, configuration.getConfiguration(), configuration, DataContext.EMPTY_CONTEXT, executor);
    }
  }

  /**
   * @deprecated Use {@link ChooseRunConfigurationManager#deleteConfiguration(Project, RunnerAndConfigurationSettings, JBPopup)}
   */
  @Deprecated
  public static void deleteConfiguration(@NotNull Project project,
                                         @NotNull RunnerAndConfigurationSettings configurationSettings,
                                         @Nullable JBPopup popupToCancel) {
    ChooseRunConfigurationManager.deleteConfiguration(project, configurationSettings, popupToCancel);
  }

  @Override
  public @NotNull Executor getExecutor() {
    return myCurrentExecutor == null ? myDefaultExecutor : myCurrentExecutor;
  }

  private static Action createNumberAction(final int number, final ListPopupImpl listPopup, final Executor executor) {
    return new MyAbstractAction(listPopup, number, executor);
  }

  /**
   * @deprecated Use {@link ChooseRunConfigurationManager#createSettingsList(RunManagerImpl, ExecutorProvider, boolean, boolean, DataContext)}
   */
  @Deprecated
  public static @NotNull List<ChooseRunConfigurationPopup.ItemWrapper<?>> createSettingsList(@NotNull Project project,
                                                                                             @NotNull ExecutorProvider executorProvider,
                                                                                             @NotNull DataContext dataContext,
                                                                                             boolean isCreateEditAction) {
    return ChooseRunConfigurationManager.createSettingsList(project, executorProvider, dataContext, isCreateEditAction);
  }

  @ApiStatus.Internal
  public abstract static class Wrapper implements NumericMnemonicItem {
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

    @SuppressWarnings("SameParameterValue")
    protected void setMnemonicsEnabled(boolean mnemonicsEnabled) {
      myMnemonicsEnabled = mnemonicsEnabled;
    }

    public boolean addSeparatorAbove() {
      return myAddSeparatorAbove;
    }

    public abstract @Nullable Icon getIcon();

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

    protected ItemWrapper(@Nullable T value) {
      this(value, false);
    }

    protected ItemWrapper(@Nullable T value, boolean addSeparatorAbove) {
      super(addSeparatorAbove);
      myValue = value;
    }

    public @NlsActions.ActionText @Nullable T getValue() {
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

    public abstract void perform(final @NotNull Project project, final @NotNull Executor executor, final @NotNull DataContext context);

    public @Nullable ConfigurationType getType() {
      return null;
    }

    public boolean available(Executor executor) {
      return false;
    }

    public boolean hasActions() {
      return false;
    }

    public PopupStep<?> getNextStep(Project project, ChooseRunConfigurationPopup action) {
      return PopupStep.FINAL_CHOICE;
    }

    public static ItemWrapper<?> wrap(@NotNull Project project,
                                      @NotNull RunnerAndConfigurationSettings settings,
                                      boolean dynamic) {
      final ItemWrapper<?> result = wrap(project, settings);
      result.setDynamic(dynamic);
      return result;
    }

    public static ItemWrapper<?> wrap(@NotNull Project project, @NotNull RunnerAndConfigurationSettings settings) {
      return new ItemWrapper<>(settings) {
        @Override
        public void perform(@NotNull Project project, @NotNull Executor executor, @NotNull DataContext context) {
          RunnerAndConfigurationSettings config = getValue();

          final RunManager manager = RunManager.getInstance(project);

          if (!manager.isRiderRunWidgetActive()) RunManager.getInstance(project).setSelectedConfiguration(config);
          ExecutorRegistryImpl.RunnerHelper.run(project, settings.getConfiguration(), settings, context, executor);
        }

        @Override
        public ConfigurationType getType() {
          return requireNonNull(getValue()).getType();
        }

        @Override
        public Icon getIcon() {
          return RunManagerEx.getInstanceEx(project).getConfigurationIcon(requireNonNull(getValue()), true);
        }

        @Override
        public String getText() {
          return Executor.shortenNameIfNeeded(requireNonNull(getValue()).getName()) + getValue().getConfiguration().getPresentableType();
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
        public PopupStep<?> getNextStep(final @NotNull Project project, final @NotNull ChooseRunConfigurationPopup action) {
          return new ChooseRunConfigurationManager.ConfigurationActionsStep(project, action, requireNonNull(getValue()), isDynamic());
        }
      };
    }

    @Override
    public boolean canBeDeleted() {
      return !isDynamic() && getValue() instanceof RunnerAndConfigurationSettings;
    }
  }

  private static final class MyAbstractAction extends AbstractAction implements DumbAware {
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
          myListPopup.setFinalRunnable(() -> execute((ItemWrapper<?>)item, myExecutor));
          myListPopup.closeOk(null);
        }
      }
    }
  }

  private final class RunListPopup extends ListPopupImpl {

    RunListPopup(Project project, WizardPopup aParent, ListPopupStep aStep, Object parentValue) {
      super(project, aParent, aStep, parentValue);
      registerActions(this);
    }

    @Override
    protected WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
      return new RunListPopup(getProject(), parent, (ListPopupStep<?>)step, parentValue);
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

    private void handleShiftClick(boolean handleFinalChoices, final InputEvent inputEvent, final RunListPopup popup) {
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
        ChooseRunConfigurationManager.deleteConfiguration(myProject, requireNonNull(runConfig), ChooseRunConfigurationPopup.this.myPopup);
        getListModel().deleteItem(o);

        List<Object> values = getListStep().getValues();
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
}
