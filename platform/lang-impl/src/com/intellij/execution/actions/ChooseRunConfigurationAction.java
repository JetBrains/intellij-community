/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author spleaner
 */
public class ChooseRunConfigurationAction extends AnAction {
  private static final Icon EDIT_ICON = IconLoader.getIcon("/actions/editSource.png");
  private static final Icon SAVE_ICON = IconLoader.getIcon("/runConfigurations/saveTempConfig.png");

  private Executor myCurrentExecutor;
  private boolean myEditConfiguration;

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    assert project != null;

    final Executor executor = getDefaultExecutor();
    assert executor != null;

    final RunListPopup popup = new RunListPopup(project, new ConfigurationListPopupStep(this, project, String.format("%s", executor.getActionName()))) {
      @Override
      protected void handleShiftClick(final boolean handleFinalChoices, final InputEvent inputEvent, final RunListPopup popup) {
        try {
          myCurrentExecutor = getAlternateExecutor();
          popup._handleSelect(handleFinalChoices, inputEvent);
        } finally {
          myCurrentExecutor = null;
        }
      }
    };
    registerActions(popup);

    final String adText = getAdText(getAlternateExecutor());
    if (adText != null) {
      popup.setAdText(adText);
    }

    popup.showCenteredInCurrentWindow(project);
  }

  protected String getAdKey() {
    return "run.configuration.alternate.action.ad";
  }

  protected static boolean canRun(@NotNull final Executor executor, final RunnerAndConfigurationSettings settings) {
    return ProgramRunnerUtil.getRunner(executor.getId(), settings) != null;
  }

  @Nullable
  protected String getAdText(final Executor alternateExecutor) {
    final PropertiesComponent properties = PropertiesComponent.getInstance();
    if (alternateExecutor != null && !properties.isTrueValue(getAdKey())) {
      return String
        .format("Hold %s to %s", KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke("SHIFT")), alternateExecutor.getActionName());
    }

    if (!properties.isTrueValue("run.configuration.edit.ad")) {
      return String.format("Press %s to Edit", KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke("F4")));
    }

    if (!properties.isTrueValue("run.configuration.delete.ad")) {
      return String.format("Press %s to Delete configuration", KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke("DELETE")));
    }

    return null;
  }

  private void registerActions(final RunListPopup popup) {
    popup.registerAction("alternateExecutor", KeyStroke.getKeyStroke("shift pressed SHIFT"), new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        myCurrentExecutor = getAlternateExecutor();
        updatePresentation(popup);
      }
    });

    popup.registerAction("restoreDefaultExecutor", KeyStroke.getKeyStroke("released SHIFT"), new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        myCurrentExecutor = getDefaultExecutor();
        updatePresentation(popup);
      }
    });


    popup.registerAction("invokeAction", KeyStroke.getKeyStroke("shift ENTER"), new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        try {
          popup.handleSelect(true);
        }
        finally {
          myCurrentExecutor = null;
        }
      }
    });

    popup.registerAction("editConfiguration", KeyStroke.getKeyStroke("F4"), new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        myEditConfiguration = true;
        try {
          popup.handleSelect(true);
        }
        finally {
          myEditConfiguration = false;
        }
      }
    });


    popup.registerAction("deleteConfiguration", KeyStroke.getKeyStroke("DELETE"), new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        popup.removeSelected();
      }
    });

    final Action action0 = createNumberAction(0, popup, getDefaultExecutor());
    final Action action0_ = createNumberAction(0, popup, getAlternateExecutor());
    popup.registerAction("0Action", KeyStroke.getKeyStroke("0"), action0);
    popup.registerAction("0Action_", KeyStroke.getKeyStroke("shift pressed 0"), action0_);
    popup.registerAction("0Action1", KeyStroke.getKeyStroke("NUMPAD0"), action0);
    popup.registerAction("0Action_1", KeyStroke.getKeyStroke("shift pressed NUMPAD0"), action0_);

    final Action action1 = createNumberAction(1, popup, getDefaultExecutor());
    final Action action1_ = createNumberAction(1, popup, getAlternateExecutor());
    popup.registerAction("1Action", KeyStroke.getKeyStroke("1"), action1);
    popup.registerAction("1Action_", KeyStroke.getKeyStroke("shift pressed 1"), action1_);
    popup.registerAction("1Action1", KeyStroke.getKeyStroke("NUMPAD1"), action1);
    popup.registerAction("1Action_1", KeyStroke.getKeyStroke("shift pressed NUMPAD1"), action1_);

    final Action action2 = createNumberAction(2, popup, getDefaultExecutor());
    final Action action2_ = createNumberAction(2, popup, getAlternateExecutor());
    popup.registerAction("2Action", KeyStroke.getKeyStroke("2"), action2);
    popup.registerAction("2Action_", KeyStroke.getKeyStroke("shift pressed 2"), action2_);
    popup.registerAction("2Action1", KeyStroke.getKeyStroke("NUMPAD2"), action2);
    popup.registerAction("2Action_1", KeyStroke.getKeyStroke("shift pressed NUMPAD2"), action2_);

    final Action action3 = createNumberAction(3, popup, getDefaultExecutor());
    final Action action3_ = createNumberAction(3, popup, getAlternateExecutor());
    popup.registerAction("3Action", KeyStroke.getKeyStroke("3"), action3);
    popup.registerAction("3Action_", KeyStroke.getKeyStroke("shift pressed 3"), action3_);
    popup.registerAction("3Action1", KeyStroke.getKeyStroke("NUMPAD3"), action3);
    popup.registerAction("3Action_1", KeyStroke.getKeyStroke("shift pressed NUMPAD3"), action3_);
  }

  private void updatePresentation(ListPopupImpl listPopup) {
    final Executor executor = getCurrentExecutor();
    if (executor != null) {
      listPopup.setCaption(executor.getActionName());
    }
  }

  static void execute(final ItemWrapper itemWrapper, final Executor executor) {
    if (executor == null) {
      return;
    }

    final DataContext dataContext = DataManager.getInstance().getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          itemWrapper.perform(project, executor, dataContext);
        }
      });
    }
  }

  void editConfiguration(@NotNull final Project project, @NotNull final RunnerAndConfigurationSettings configuration) {
    final Executor executor = getCurrentExecutor();
    assert executor != null;

    PropertiesComponent.getInstance().setValue("run.configuration.edit.ad", Boolean.toString(true));
    if (RunDialog.editConfiguration(project, configuration, "Edit configuration settings", executor.getActionName(), executor.getIcon())) {
      RunManagerEx.getInstanceEx(project).setSelectedConfiguration(configuration);
      ProgramRunnerUtil.executeConfiguration(project, configuration, executor);
    }
  }

  private static void deleteConfiguration(final Project project, @NotNull final RunnerAndConfigurationSettings configurationSettings) {
    final RunManagerEx manager = RunManagerEx.getInstanceEx(project);
    manager.removeConfiguration(configurationSettings);
  }

  @Nullable
  protected Executor getDefaultExecutor() {
    return DefaultRunExecutor.getRunExecutorInstance();
  }

  @Nullable
  protected Executor getAlternateExecutor() {
    return ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);
  }

  @Nullable
  protected Executor getCurrentExecutor() {
    return myCurrentExecutor == null ? getDefaultExecutor() : myCurrentExecutor;
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Project project = e.getData(PlatformDataKeys.PROJECT);

    presentation.setEnabled(true);
    if (project == null || project.isDisposed()) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    if (null == getDefaultExecutor()) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    presentation.setEnabled(true);
    presentation.setVisible(true);
  }

  private static Action createNumberAction(final int number, final ListPopupImpl listPopup, final Executor executor) {
    return new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        for (final Object item : listPopup.getListStep().getValues()) {
          if (item instanceof ItemWrapper && ((ItemWrapper)item).getMnemonic() == number) {
            listPopup.setFinalRunnable(new Runnable() {
              public void run() {
                execute((ItemWrapper)item, executor);
              }
            });
            listPopup.cancel();
          }
        }
      }
    };
  }

  private abstract static class ItemWrapper<T> {
    private final T myValue;
    private boolean myDynamic = false;
    private int myMnemonic = -1;

    protected ItemWrapper(@Nullable final T value) {
      myValue = value;
    }

    public int getMnemonic() {
      return myMnemonic;
    }

    public void setMnemonic(int mnemonic) {
      myMnemonic = mnemonic;
    }

    public T getValue() {
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

      ItemWrapper that = (ItemWrapper)o;

      if (myValue != null ? !myValue.equals(that.myValue) : that.myValue != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myValue != null ? myValue.hashCode() : 0;
    }

    public abstract Icon getIcon();

    public abstract String getText();

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

    public PopupStep getNextStep(Project project, ChooseRunConfigurationAction action) {
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
      return new ItemWrapper<RunnerAndConfigurationSettings>(settings) {
        @Override
        public void perform(@NotNull Project project, @NotNull Executor executor, @NotNull DataContext context) {
          RunManagerEx.getInstanceEx(project).setSelectedConfiguration(getValue());
          ProgramRunnerUtil.executeConfiguration(project, getValue(), executor);
        }

        @Override
        public ConfigurationType getType() {
          return getValue().getType();
        }

        @Override
        public Icon getIcon() {
          return ProgramRunnerUtil.getConfigurationIcon(project, getValue());
        }

        @Override
        public String getText() {
          if (getMnemonic() != -1) {
            return String.format("%s", getValue().getName());
          }

          return getValue().getName();
        }

        @Override
        public boolean hasActions() {
          return true;
        }

        @Override
        public boolean available(Executor executor) {
          return null != ProgramRunnerUtil.getRunner(executor.getId(), getValue());
        }

        @Override
        public PopupStep getNextStep(@NotNull final Project project, @NotNull final ChooseRunConfigurationAction action) {
          return new ConfigurationActionsStep(project, action, getValue(), isDynamic());
        }
      };
    }

    public boolean canBeDeleted() {
      return !isDynamic() && getValue() != null;
    }
  }

  private static final class ConfigurationListPopupStep extends BaseListPopupStep<ItemWrapper> {
    private final Project myProject;
    private final ChooseRunConfigurationAction myAction;
    private int myDefaultConfiguration = -1;

    private ConfigurationListPopupStep(@NotNull final ChooseRunConfigurationAction action,
                                       @NotNull final Project project,
                                       @NotNull final String title) {
      super(title, createSettingsList(project));
      myProject = project;
      myAction = action;

      if (-1 == getDefaultOptionIndex()) {
        myDefaultConfiguration = getDynamicIndex();
      }
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

    private static ItemWrapper[] createSettingsList(@NotNull final Project project) {
      final RunManagerEx manager = RunManagerEx.getInstanceEx(project);

      final List<ItemWrapper> result = new ArrayList<ItemWrapper>();

      final RunnerAndConfigurationSettings selectedConfiguration = manager.getSelectedConfiguration();

      final Map<RunnerAndConfigurationSettings, ItemWrapper> wrappedExisting = new LinkedHashMap<RunnerAndConfigurationSettings, ItemWrapper>();
      final ConfigurationType[] factories = manager.getConfigurationFactories();
      for (final ConfigurationType factory : factories) {
        final RunnerAndConfigurationSettings[] configurations = manager.getConfigurationSettings(factory);
        for (final RunnerAndConfigurationSettings configuration : configurations) {
          final ItemWrapper wrapped = ItemWrapper.wrap(project, configuration);
          if (configuration == selectedConfiguration) {
            wrapped.setMnemonic(1);
          }

          wrappedExisting.put(configuration, wrapped);
        }
      }

      populateWithDynamicRunners(result, wrappedExisting, project, manager, selectedConfiguration);
      result.addAll(wrappedExisting.values());

      //noinspection unchecked
      final ItemWrapper edit = new ItemWrapper(null) {
        @Override
        public Icon getIcon() {
          return EDIT_ICON;
        }

        @Override
        public String getText() {
          return "Edit configurations...";
        }

        @Override
        public void perform(@NotNull final Project project, @NotNull final Executor executor, @NotNull DataContext context) {
          final EditConfigurationsDialog dialog = new EditConfigurationsDialog(project) {
            @Override
            protected void init() {
              setOKButtonText(executor.getStartActionText());
              setOKButtonIcon(executor.getIcon());

              super.init();
            }
          };

          dialog.show();
          if (dialog.isOK()) {
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                final RunnerAndConfigurationSettings configuration = RunManager.getInstance(project).getSelectedConfiguration();
                if (configuration instanceof RunnerAndConfigurationSettingsImpl) {
                  if (canRun(executor, configuration)) {
                    ProgramRunnerUtil.executeConfiguration(project, configuration, executor);
                  }
                }
              }
            });
          }
        }

        @Override
        public boolean available(Executor executor) {
          return true;
        }
      };

      edit.setMnemonic(0);
      result.add(0, edit);

      return result.toArray(new ItemWrapper[result.size()]);
    }

    @NotNull
    private static List<RunnerAndConfigurationSettings> populateWithDynamicRunners(final List<ItemWrapper> result,
                                                                                       Map<RunnerAndConfigurationSettings, ItemWrapper> existing,
                                                                                       final Project project, final RunManagerEx manager,
                                                                                       final RunnerAndConfigurationSettings selectedConfiguration) {

      final ArrayList<RunnerAndConfigurationSettings> contextConfigurations = new ArrayList<RunnerAndConfigurationSettings>();
      final DataContext dataContext = DataManager.getInstance().getDataContext();
      final ConfigurationContext context = new ConfigurationContext(dataContext);

      final List<RuntimeConfigurationProducer> producers = PreferedProducerFind.findPreferredProducers(context.getLocation(), context, false);
      if (producers == null) return Collections.emptyList();

      Collections.sort(producers, new Comparator<RuntimeConfigurationProducer>() {
        public int compare(final RuntimeConfigurationProducer p1, final RuntimeConfigurationProducer p2) {
          return p1.getConfigurationType().getDisplayName().compareTo(p2.getConfigurationType().getDisplayName());
        }
      });

      final RunnerAndConfigurationSettings[] preferred = {null};

      int i = 2; // selectedConfiguration == null ? 1 : 2;
      for (final RuntimeConfigurationProducer producer : producers) {
        final RunnerAndConfigurationSettings configuration = producer.getConfiguration();
        if (configuration != null) {
          if (existing.keySet().contains(configuration)) {
            final ItemWrapper wrapper = existing.get(configuration);
            if (wrapper.getMnemonic() != 1) {
              wrapper.setMnemonic(i);
              i++;
            }
          } else {
            if (selectedConfiguration != null && configuration.equals(selectedConfiguration)) continue;
            contextConfigurations.add(configuration);

            if (preferred[0] == null) {
              preferred[0] = configuration;
            }

            //noinspection unchecked
            final ItemWrapper wrapper = new ItemWrapper(configuration) {
              @Override
              public Icon getIcon() {
                return IconLoader.getTransparentIcon(ProgramRunnerUtil.getConfigurationIcon(project, configuration), 0.3f);
              }

              @Override
              public String getText() {
                return String.format("%s", configuration.getName());
              }

              @Override
              public boolean available(Executor executor) {
                return canRun(executor, configuration);
              }

              @Override
              public void perform(@NotNull Project project, @NotNull Executor executor, @NotNull DataContext context) {
                manager.setTemporaryConfiguration(configuration);
                RunManagerEx.getInstanceEx(project).setSelectedConfiguration(configuration);
                ProgramRunnerUtil.executeConfiguration(project, configuration, executor);
              }

              @Override
              public PopupStep getNextStep(@NotNull final Project project, @NotNull final ChooseRunConfigurationAction action) {
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

      return contextConfigurations;
    }

    @Override
    public ListSeparator getSeparatorAbove(ItemWrapper value) {
      final List<ItemWrapper> configurations = getValues();
      final int index = configurations.indexOf(value);
      if (index > 0 && index <= configurations.size() - 1) {
        final ItemWrapper aboveConfiguration = index == 0 ? null : configurations.get(index - 1);

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
          return doFinalStep(new Runnable() {
            public void run() {
              myAction.editConfiguration(myProject, (RunnerAndConfigurationSettings)o);
            }
          });
        }
      }

      final Executor executor = myAction.getCurrentExecutor();
      assert executor != null;

      if (finalChoice && wrapper.available(executor)) {
        return doFinalStep(new Runnable() {
          public void run() {
            if (executor == myAction.getAlternateExecutor()) {
              PropertiesComponent.getInstance().setValue(myAction.getAdKey(), Boolean.toString(true));
            }

            wrapper.perform(myProject, executor, DataManager.getInstance().getDataContext());
          }
        });
      }
      else {
        return wrapper.getNextStep(myProject, myAction);
      }
    }

    @Override
    public boolean hasSubstep(ItemWrapper selectedValue) {
      return selectedValue.hasActions();
    }

    @NotNull
    @Override
    public String getTextFor(ItemWrapper value) {
      return value.getText();
    }

    @Override
    public Icon getIconFor(ItemWrapper value) {
      return value.getIcon();
    }
  }

  private static final class ConfigurationActionsStep extends BaseListPopupStep<ActionWrapper> {
    private ConfigurationActionsStep(@NotNull final Project project,
                                     ChooseRunConfigurationAction action,
                                     @NotNull final RunnerAndConfigurationSettings settings, final boolean dynamic) {
      super("Actions", buildActions(project, action, settings, dynamic));
    }

    private static ActionWrapper[] buildActions(@NotNull final Project project,
                                                final ChooseRunConfigurationAction action,
                                                @NotNull final RunnerAndConfigurationSettings settings,
                                                final boolean dynamic) {
      final List<ActionWrapper> result = new ArrayList<ActionWrapper>();
      final Executor[] executors = ExecutorRegistry.getInstance().getRegisteredExecutors();
      for (final Executor executor : executors) {
        final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), settings.getConfiguration());
        if (runner != null) {
          result.add(new ActionWrapper(executor.getActionName(), executor.getIcon()) {
            @Override
            public void perform() {
              final RunManagerEx manager = RunManagerEx.getInstanceEx(project);
              if (dynamic) manager.setTemporaryConfiguration(settings);
              manager.setSelectedConfiguration(settings);
              ProgramRunnerUtil.executeConfiguration(project, settings, executor);
            }
          });
        }
      }

      result.add(new ActionWrapper("Edit...", EDIT_ICON) {
        @Override
        public void perform() {
          final RunManagerEx manager = RunManagerEx.getInstanceEx(project);
          if (dynamic) manager.setTemporaryConfiguration(settings);
          action.editConfiguration(project, settings);
        }
      });

      if (RunManager.getInstance(project).isTemporary(settings.getConfiguration()) || dynamic) {
        result.add(new ActionWrapper("Save temp configuration", SAVE_ICON) {
          @Override
          public void perform() {
            final RunManagerEx manager = RunManagerEx.getInstanceEx(project);
            if (dynamic) manager.setTemporaryConfiguration(settings);
            manager.makeStable(settings.getConfiguration());
          }
        });
      }

      return result.toArray(new ActionWrapper[result.size()]);
    }

    @Override
    public PopupStep onChosen(final ActionWrapper selectedValue, boolean finalChoice) {
      return doFinalStep(new Runnable() {
        public void run() {
          selectedValue.perform();
        }
      });
    }

    @Override
    public Icon getIconFor(ActionWrapper aValue) {
      return aValue.getIcon();
    }

    @NotNull
    @Override
    public String getTextFor(ActionWrapper value) {
      return value.getName();
    }
  }

  private abstract static class ActionWrapper {
    private final String myName;
    private final Icon myIcon;

    private ActionWrapper(String name, Icon icon) {
      myName = name;
      myIcon = icon;
    }

    public abstract void perform();

    public String getName() {
      return myName;
    }

    public Icon getIcon() {
      return myIcon;
    }
  }

  private static class RunListElementRenderer extends PopupListElementRenderer {
    private JLabel myLabel;
    private final ListPopupImpl myPopup1;

    private RunListElementRenderer(ListPopupImpl popup) {
      super(popup);

      myPopup1 = popup;
    }

    @Override
    protected JComponent createItemComponent() {
      if (myLabel == null) {
        myLabel = new JLabel();
        myLabel.setPreferredSize(new JLabel("8.").getPreferredSize());
      }
      
      final JComponent result = super.createItemComponent();
      result.add(myLabel, BorderLayout.WEST);
      return result;
    }

    @Override
    protected void customizeComponent(JList list, Object value, boolean isSelected) {
      super.customizeComponent(list, value, isSelected);

      ListPopupStep<Object> step = myPopup1.getListStep();
      boolean isSelectable = step.isSelectable(value);
      myLabel.setEnabled(isSelectable);

      if (isSelected) {
        setSelected(myLabel);
      } else {
        setDeselected(myLabel);
      }

      if (value instanceof ItemWrapper) {
        final int mnemonic = ((ItemWrapper)value).getMnemonic();
        if (mnemonic != -1) {
          myLabel.setText(mnemonic + ".");
          myLabel.setDisplayedMnemonicIndex(0);
        } else {
          myLabel.setText("");
        }
      }
    }
  }

  private static abstract class RunListPopup extends ListPopupImpl {
    private final Project myProject_;

    public RunListPopup(final Project project, ListPopupStep step) {
      super(step);
      myProject_ = project;
    }

    @Override
    public void handleSelect(boolean handleFinalChoices, InputEvent e) {
      if (e instanceof MouseEvent && e.isShiftDown()) {
        handleShiftClick(handleFinalChoices, e, this);
        return;
      }

      _handleSelect(handleFinalChoices, e);
    }

    protected void _handleSelect(boolean handleFinalChoices, InputEvent e) {
      super.handleSelect(handleFinalChoices, e);
    }

    protected abstract void handleShiftClick(boolean handleFinalChoices, final InputEvent inputEvent, final RunListPopup popup);

    @Override
    protected boolean isActionClick(MouseEvent e) {
      if (e.getButton() == MouseEvent.BUTTON2 || e.isPopupTrigger() || e.getID() != MouseEvent.MOUSE_PRESSED) return false;
      return e.getButton() == MouseEvent.BUTTON1;
    }

    @Override
    protected ListCellRenderer getListElementRenderer() {
      return new RunListElementRenderer(this);
    }

    public void removeSelected() {
      final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
      if (!propertiesComponent.isTrueValue("run.configuration.delete.ad")) propertiesComponent.setValue("run.configuration.delete.ad", Boolean.toString(true));

      final int index = getSelectedIndex();
      if (index == -1) {
        return;
      }

      final Object o = getListModel().get(index);
      if (o != null && o instanceof ItemWrapper && ((ItemWrapper)o).canBeDeleted()) {
        deleteConfiguration(myProject_, (RunnerAndConfigurationSettings) ((ItemWrapper)o).getValue());
        getListModel().deleteItem(o);
        final List<Object> values = getListStep().getValues();
        values.remove(o);

        if (index < values.size()){
          onChildSelectedFor(values.get(index));
        } else if (index - 1 >= 0) {
          onChildSelectedFor(values.get(index - 1));
        }
      }
    }
  }
}
