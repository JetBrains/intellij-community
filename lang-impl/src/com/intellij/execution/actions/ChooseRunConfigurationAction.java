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
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author spleaner
 */
public class ChooseRunConfigurationAction extends AnAction {
  private static final Icon EDIT_ICON = IconLoader.getIcon("/actions/editSource.png");
  private static final Icon SAVE_ICON = IconLoader.getIcon("/runConfigurations/saveTempConfig.png");

  private Executor myCurrentExecutor;
  private boolean myEditConfiguration;
  private boolean myDeleteConfiguration;

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    assert project != null;

    final Executor executor = getDefaultExecutor();
    assert executor != null;

    final ListPopupImpl popup =
      new ListPopupImpl(new ConfigurationListPopupStep(this, project, String.format("%s", executor.getActionName()))) {
        {
          registerActions(this, getInputMap(), getActionMap());
        }};

    final String adText = getAdText(getAlternateExecutor());
    if (adText != null) {
      popup.setAdText(adText);
    }

    popup.showCenteredInCurrentWindow(project);
  }

  protected String getAdKey() {
    return "run.configuration.alternate.action.ad";
  }

  @Nullable
  protected String getAdText(final Executor alternateExecutor) {
    if (alternateExecutor != null && !PropertiesComponent.getInstance().isTrueValue(getAdKey())) {
      return String
        .format("Hold %s to %s", KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke("SHIFT")), alternateExecutor.getActionName());
    }

    if (!PropertiesComponent.getInstance().isTrueValue("run.configuration.edit.ad")) {
      return String.format("Press %s to Edit", KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke("F4")));
    }

    return null;
  }

  private void registerActions(final ListPopupImpl listPopup, @NotNull final InputMap inputMap, @NotNull final ActionMap actionMap) {
    inputMap.put(KeyStroke.getKeyStroke("shift pressed SHIFT"), "alternateExecutor");
    inputMap.put(KeyStroke.getKeyStroke("released SHIFT"), "restoreDefaultExecutor");
    inputMap.put(KeyStroke.getKeyStroke("shift ENTER"), "invokeAction");
    inputMap.put(KeyStroke.getKeyStroke("F4"), "editConfiguration");
    inputMap.put(KeyStroke.getKeyStroke("DELETE"), "deleteConfiguration");

    actionMap.put("deleteConfiguration", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        myDeleteConfiguration = true;
        try {
          listPopup.handleSelect(true);
        }
        finally {
          myDeleteConfiguration = false;
        }
      }
    });

    actionMap.put("editConfiguration", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        myEditConfiguration = true;
        try {
          listPopup.handleSelect(true);
        }
        finally {
          myEditConfiguration = false;
        }

      }
    });

    actionMap.put("invokeAction", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        listPopup.handleSelect(true);
      }
    });

    actionMap.put("alternateExecutor", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        myCurrentExecutor = getAlternateExecutor();
        updatePresentation(listPopup);
      }
    });

    actionMap.put("restoreDefaultExecutor", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        myCurrentExecutor = getDefaultExecutor();
        updatePresentation(listPopup);
      }
    });
  }

  private void updatePresentation(ListPopupImpl listPopup) {
    final Executor executor = getCurrentExecutor();
    if (executor != null) {
      listPopup.setCaption(executor.getActionName());
    }
  }

  void editConfiguration(@NotNull final Project project, @NotNull final RunnerAndConfigurationSettingsImpl configuration) {
    final Executor executor = getCurrentExecutor();
    assert executor != null;

    PropertiesComponent.getInstance().setValue("run.configuration.edit.ad", Boolean.toString(true));
    if (RunDialog.editConfiguration(project, configuration, "Edit configuration settings", executor.getActionName(), executor.getIcon())) {
      RunManagerEx.getInstanceEx(project).setSelectedConfiguration(configuration);
      ExecutionUtil.executeConfiguration(project, configuration, executor, DataManager.getInstance().getDataContext());
    }
  }

  private void deleteConfiguration(final Project project, final RunnerAndConfigurationSettingsImpl configurationSettings) {
    // TODO:
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

  private abstract static class ItemWrapper<T> {
    private T myValue;
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

    public abstract Icon getIcon();

    public abstract String getText();

    public abstract void perform(@NotNull final Project project, @NotNull final Executor executor, @NotNull final DataContext context);

    @Nullable
    public ConfigurationType getType() {
      return null;
    }

    public boolean available(Executor executor) {
      return true;
    }

    public boolean hasActions() {
      return false;
    }

    public PopupStep getNextStep(Project project, ChooseRunConfigurationAction action) {
      return PopupStep.FINAL_CHOICE;
    }

    public static ItemWrapper wrap(@NotNull final Project project,
                                   @NotNull final RunnerAndConfigurationSettingsImpl settings,
                                   final boolean dynamic) {
      final ItemWrapper result = wrap(project, settings);
      result.setDynamic(dynamic);
      return result;
    }

    public static ItemWrapper wrap(@NotNull final Project project, @NotNull final RunnerAndConfigurationSettingsImpl settings) {
      return new ItemWrapper<RunnerAndConfigurationSettingsImpl>(settings) {
        @Override
        public void perform(@NotNull Project project, @NotNull Executor executor, @NotNull DataContext context) {
          RunManagerEx.getInstanceEx(project).setSelectedConfiguration(getValue());
          ExecutionUtil.executeConfiguration(project, getValue(), executor, DataManager.getInstance().getDataContext());
        }

        @Override
        public ConfigurationType getType() {
          return getValue().getType();
        }

        @Override
        public Icon getIcon() {
          return ExecutionUtil.getConfigurationIcon(project, getValue());
        }

        @Override
        public String getText() {
          if (getMnemonic() != -1) {
            return String.format("&%s. %s", Integer.valueOf(getMnemonic()), getValue().getName());
          }

          return getValue().getName();
        }

        @Override
        public boolean hasActions() {
          return true;
        }

        @Override
        public boolean available(Executor executor) {
          return null != ExecutionUtil.getRunner(executor.getId(), getValue());
        }

        @Override
        public PopupStep getNextStep(@NotNull final Project project, @NotNull final ChooseRunConfigurationAction action) {
          return new ConfigurationActionsStep(project, action, getValue());
        }
      };
    }

  }

  private static final class ConfigurationListPopupStep extends BaseListPopupStep<ItemWrapper> {
    private Project myProject;
    private ChooseRunConfigurationAction myAction;

    private ConfigurationListPopupStep(@NotNull final ChooseRunConfigurationAction action,
                                       @NotNull final Project project,
                                       @NotNull final String title) {
      super(title, createSettingsList(project));
      myProject = project;
      myAction = action;
    }

    @Override
    public boolean isAutoSelectionEnabled() {
      return false;
    }

    private static ItemWrapper[] createSettingsList(@NotNull final Project project) {
      final RunManagerEx manager = RunManagerEx.getInstanceEx(project);

      final List<ItemWrapper> result = new ArrayList<ItemWrapper>();

      //noinspection unchecked
      result.add(new ItemWrapper(null) {
        @Override
        public Icon getIcon() {
          return EDIT_ICON;
        }

        @Override
        public String getText() {
          return "Edit configurations...";
        }

        @Override
        public void perform(@NotNull Project project, @NotNull Executor executor, @NotNull DataContext context) {
          new EditConfigurationsDialog(project).show();
        }
      });

      final RunnerAndConfigurationSettingsImpl context = populateWithDynamicRunners(result, project, manager);

      final ConfigurationType[] factories = manager.getConfigurationFactories();
      for (final ConfigurationType factory : factories) {
        final RunnerAndConfigurationSettingsImpl[] configurations = manager.getConfigurationSettings(factory);
        for (final RunnerAndConfigurationSettingsImpl configuration : configurations) {
          if (configuration != context) { // exclude context configuration
            result.add(ItemWrapper.wrap(project, configuration));
          }
        }
      }

      return result.toArray(new ItemWrapper[result.size()]);
    }

    @Nullable
    private static RunnerAndConfigurationSettingsImpl populateWithDynamicRunners(final List<ItemWrapper> result,
                                                                                 final Project project,
                                                                                 final RunManagerEx manager) {
      final DataContext dataContext = DataManager.getInstance().getDataContext();
      final ConfigurationContext context = new ConfigurationContext(dataContext);
      final RunnerAndConfigurationSettingsImpl existing = context.findExisting();
      if (existing == null) {
        final List<RuntimeConfigurationProducer> producers = PreferedProducerFind.findPreferedProducers(context.getLocation(), context);
        if (producers == null) return null;

        Collections.sort(producers, new Comparator<RuntimeConfigurationProducer>() {
          public int compare(final RuntimeConfigurationProducer p1, final RuntimeConfigurationProducer p2) {
            return p1.getConfigurationType().getDisplayName().compareTo(p2.getConfigurationType().getDisplayName());
          }
        });

        int i = 1;
        for (final RuntimeConfigurationProducer producer : producers) {
          final RunnerAndConfigurationSettingsImpl configuration = producer.getConfiguration();
          if (configuration != null) {

            //noinspection unchecked
            final ItemWrapper wrapper = new ItemWrapper(null) {
              @Override
              public Icon getIcon() {
                return IconLoader.getTransparentIcon(ExecutionUtil.getConfigurationIcon(project, configuration), 0.3f);
              }

              @Override
              public String getText() {
                return producers.size() == 1
                       ? String.format("&%s. %s", Integer.toString(getMnemonic()), configuration.getName())
                       : String.format("&%s. %s (%s)", Integer.toString(getMnemonic()), configuration.getName(),
                                       producer.getConfigurationType().getDisplayName());
              }

              @Override
              public void perform(@NotNull Project project, @NotNull Executor executor, @NotNull DataContext context) {
                manager.setTemporaryConfiguration(configuration);
                RunManagerEx.getInstanceEx(project).setSelectedConfiguration(configuration);
                ExecutionUtil.executeConfiguration(project, configuration, executor, DataManager.getInstance().getDataContext());
              }

              @Override
              public PopupStep getNextStep(@NotNull final Project project, @NotNull final ChooseRunConfigurationAction action) {
                return new ConfigurationActionsStep(project, action, configuration);
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
      else {
        final ItemWrapper wrapper = ItemWrapper.wrap(project, existing, true);
        wrapper.setMnemonic(1);
        result.add(wrapper);
      }

      return existing;
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

      final List<ItemWrapper> list = getValues();
      int i = 0;
      for (final ItemWrapper wrapper : list) {
        if (wrapper.getValue() == currentConfiguration) {
          return i;
        }
        i++;
      }

      return super.getDefaultOptionIndex();
    }

    @Override
    public boolean isMnemonicsNavigationEnabled() {
      return true;
    }

    @Override
    public PopupStep onChosen(final ItemWrapper wrapper, boolean finalChoice) {
      if (myAction.myEditConfiguration) {
        final Object o = wrapper.getValue();
        if (o instanceof RunnerAndConfigurationSettingsImpl) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              myAction.editConfiguration(myProject, (RunnerAndConfigurationSettingsImpl)o);
            }
          });

          return FINAL_CHOICE;
        }
      }

      if (myAction.myDeleteConfiguration) {
        final Object o = wrapper.getValue();
        if (o instanceof RunnerAndConfigurationSettingsImpl) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              myAction.deleteConfiguration(myProject, (RunnerAndConfigurationSettingsImpl)o);
            }
          });

          return FINAL_CHOICE;
        }
      }

      final Executor executor = myAction.getCurrentExecutor();
      assert executor != null;

      if (finalChoice && wrapper.available(executor)) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (myAction.getCurrentExecutor() == myAction.getAlternateExecutor()) {
              PropertiesComponent.getInstance().setValue(myAction.getAdKey(), Boolean.toString(true));
            }

            wrapper.perform(myProject, executor, DataManager.getInstance().getDataContext());
          }
        });

        return FINAL_CHOICE;
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
                                     @NotNull final RunnerAndConfigurationSettingsImpl settings) {
      super("Actions", buildActions(project, action, settings));
    }

    private static ActionWrapper[] buildActions(@NotNull final Project project,
                                                final ChooseRunConfigurationAction action,
                                                @NotNull final RunnerAndConfigurationSettingsImpl settings) {
      final List<ActionWrapper> result = new ArrayList<ActionWrapper>();
      final Executor[] executors = ExecutorRegistry.getInstance().getRegisteredExecutors();
      for (final Executor executor : executors) {
        final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), settings.getConfiguration());
        if (runner != null) {
          result.add(new ActionWrapper(executor.getActionName(), executor.getIcon()) {
            @Override
            public void perform() {
              RunManagerEx.getInstanceEx(project).setSelectedConfiguration(settings);
              ExecutionUtil.executeConfiguration(project, settings, executor, DataManager.getInstance().getDataContext());
            }
          });
        }
      }

      result.add(new ActionWrapper("Edit...", EDIT_ICON) {
        @Override
        public void perform() {
          action.editConfiguration(project, settings);
        }
      });

      if (RunManager.getInstance(project).isTemporary(settings.getConfiguration())) {
        result.add(new ActionWrapper("Save temp configuration", SAVE_ICON) {
          @Override
          public void perform() {
            RunManager.getInstance(project).makeStable(settings.getConfiguration());
          }
        });
      }

      return result.toArray(new ActionWrapper[result.size()]);
    }

    @Override
    public PopupStep onChosen(final ActionWrapper selectedValue, boolean finalChoice) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          selectedValue.perform();
        }
      });

      return FINAL_CHOICE;
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
    private String myName;
    private Icon myIcon;

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
}
