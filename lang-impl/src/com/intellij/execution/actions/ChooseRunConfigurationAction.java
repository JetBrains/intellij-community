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
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.popup.AbstractPopup;
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

  private static final Icon _00_ICON = IconLoader.getIcon("/ide/numbers/00.png");
  private static final Icon _01_ICON = IconLoader.getIcon("/ide/numbers/01.png");
  private static final Icon _02_ICON = IconLoader.getIcon("/ide/numbers/02.png");
  private static final Icon _03_ICON = IconLoader.getIcon("/ide/numbers/03.png");

  private Executor myCurrentExecutor;
  private boolean myEditConfiguration;
  private boolean myDeleteConfiguration;

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    assert project != null;

    final Executor executor = getDefaultExecutor();
    assert executor != null;

    final ListPopup popup = JBPopupFactory.getInstance()
      .createListPopup(new ConfigurationListPopupStep(this, project, String.format("%s", executor.getActionName())));

    if (popup instanceof ListPopupImpl) {
      registerActions((ListPopupImpl)popup);
    }

    final String adText = getAdText(getAlternateExecutor());
    if (adText != null) {
      ((AbstractPopup)popup).setAdText(adText);
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

  private static Icon getNumberIcon(final Icon base, final int number) {
    switch (number) {
      case 0:
        return LayeredIcon.create(base, _00_ICON);
      case 1:
        return LayeredIcon.create(base, _01_ICON);
      case 2:
        return LayeredIcon.create(base, _02_ICON);
      case 3:
        return LayeredIcon.create(base, _03_ICON);
      default:
        return base;
    }
  }

  private void registerActions(final ListPopupImpl popup) {
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
        popup.handleSelect(true);
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
        myDeleteConfiguration = true;
        try {
          popup.handleSelect(true);
        }
        finally {
          myDeleteConfiguration = false;
        }
      }
    });

    popup.registerAction("0Action", KeyStroke.getKeyStroke("0"), createNumberAction(0, popup, getDefaultExecutor()));
    popup.registerAction("0Action_", KeyStroke.getKeyStroke("shift pressed 0"), createNumberAction(0, popup, getAlternateExecutor()));
    popup.registerAction("1Action", KeyStroke.getKeyStroke("1"), createNumberAction(1, popup, getDefaultExecutor()));
    popup.registerAction("1Action_", KeyStroke.getKeyStroke("shift pressed 1"), createNumberAction(1, popup, getAlternateExecutor()));
    popup.registerAction("2Action", KeyStroke.getKeyStroke("2"), createNumberAction(2, popup, getDefaultExecutor()));
    popup.registerAction("2Action_", KeyStroke.getKeyStroke("shift pressed 2"), createNumberAction(2, popup, getAlternateExecutor()));
    popup.registerAction("3Action", KeyStroke.getKeyStroke("3"), createNumberAction(3, popup, getDefaultExecutor()));
    popup.registerAction("3Action_", KeyStroke.getKeyStroke("shift pressed 3"), createNumberAction(3, popup, getAlternateExecutor()));
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

  private static Action createNumberAction(final int number, final ListPopupImpl listPopup, final Executor executor) {
    return new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        for (Object item : listPopup.getListStep().getValues()) {
          if (item instanceof ItemWrapper && ((ItemWrapper)item).getMnemonic() == number) {
            listPopup.cancel();
            execute((ItemWrapper)item, executor);
          }
        }
      }
    };
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

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

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
          final Icon result = ExecutionUtil.getConfigurationIcon(project, getValue());
          if (getMnemonic() != -1) {
            return getNumberIcon(result, getMnemonic());
          }

          return result;
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

      final RunnerAndConfigurationSettingsImpl selectedConfiguration = manager.getSelectedConfiguration();
      final RunnerAndConfigurationSettingsImpl context = populateWithDynamicRunners(result, project, manager, selectedConfiguration);

      final ConfigurationType[] factories = manager.getConfigurationFactories();
      for (final ConfigurationType factory : factories) {
        final RunnerAndConfigurationSettingsImpl[] configurations = manager.getConfigurationSettings(factory);
        for (final RunnerAndConfigurationSettingsImpl configuration : configurations) {
          if (configuration != context) { // exclude context configuration
            final ItemWrapper wrapped = ItemWrapper.wrap(project, configuration);
            if (configuration == selectedConfiguration) {
              wrapped.setMnemonic(1);
            }

            result.add(wrapped);
          }
        }
      }

      //noinspection unchecked
      final ItemWrapper edit = new ItemWrapper(null) {
        @Override
        public Icon getIcon() {
          return getNumberIcon(EDIT_ICON, 0);
        }

        @Override
        public String getText() {
          return "Edit configurations...";
        }

        @Override
        public void perform(@NotNull Project project, @NotNull Executor executor, @NotNull DataContext context) {
          new EditConfigurationsDialog(project).show();
        }
      };

      edit.setMnemonic(0);
      result.add(0, edit);

      return result.toArray(new ItemWrapper[result.size()]);
    }

    @Nullable
    private static RunnerAndConfigurationSettingsImpl populateWithDynamicRunners(final List<ItemWrapper> result,
                                                                                 final Project project,
                                                                                 final RunManagerEx manager,
                                                                                 final RunnerAndConfigurationSettingsImpl selectedConfiguration) {
      final DataContext dataContext = DataManager.getInstance().getDataContext();
      final ConfigurationContext context = new ConfigurationContext(dataContext);
      final RunnerAndConfigurationSettingsImpl existing = context.findExisting();
      if (existing != null && existing == selectedConfiguration) {
        return null;
      }

      if (existing == null) {
        final List<RuntimeConfigurationProducer> producers = PreferedProducerFind.findPreferedProducers(context.getLocation(), context);
        if (producers == null) return null;

        Collections.sort(producers, new Comparator<RuntimeConfigurationProducer>() {
          public int compare(final RuntimeConfigurationProducer p1, final RuntimeConfigurationProducer p2) {
            return p1.getConfigurationType().getDisplayName().compareTo(p2.getConfigurationType().getDisplayName());
          }
        });

        final RunnerAndConfigurationSettingsImpl[] preferred = {null};

        int i = 2; // selectedConfiguration == null ? 1 : 2;
        for (final RuntimeConfigurationProducer producer : producers) {
          final RunnerAndConfigurationSettingsImpl configuration = producer.getConfiguration();
          if (configuration != null) {

            if (preferred[0] == null) {
              preferred[0] = configuration;
            }

            //noinspection unchecked
            final ItemWrapper wrapper = new ItemWrapper(null) {
              @Override
              public Icon getIcon() {
                Icon result = IconLoader.getTransparentIcon(ExecutionUtil.getConfigurationIcon(project, configuration), 0.3f);
                if (getMnemonic() != -1) {
                  result = getNumberIcon(result, getMnemonic());
                }

                return result;
              }

              @Override
              public String getText() {
                return producers.size() == 1
                       ? String.format("%s", configuration.getName())
                       : String.format("%s (%s)", configuration.getName(), producer.getConfigurationType().getDisplayName());
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

        return preferred[0];
      }
      else {
        final ItemWrapper wrapper = ItemWrapper.wrap(project, existing, true);
        wrapper.setMnemonic(2); // selectedConfiguration == null ? 1 : 2);
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
      if (currentConfiguration == null && myDefaultConfiguration != -1) {
        return myDefaultConfiguration;
      }

      return currentConfiguration instanceof RunnerAndConfigurationSettingsImpl ? getValues()
        .indexOf(ItemWrapper.wrap(myProject, (RunnerAndConfigurationSettingsImpl)currentConfiguration)) : -1;
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
